package com.lab2;

import quickfix.Application;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.RejectLogon;
import quickfix.Session;
import quickfix.UnsupportedMessageType;
import quickfix.field.*;
import org.java_websocket.WebSocket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderApplication implements Application {
    private OrderBroadcaster broadcaster;
    private BlockingQueue<Object> dbQueue;
    private LtpKafkaProducer ltpProducer;
    // Load securities into a HashMap on startup for fast lookup
    private Map<String, Security> validSecurities = new HashMap<>();
    // Order Books: One per symbol for isolated concurrency
    private Map<String, OrderBook> orderBooks = new HashMap<>();
    // Track current session for sending FIX cancel responses
    private SessionID currentSessionID;
    // All active orders (recovered + current session) — for streaming to newly connected UI clients
    private ConcurrentHashMap<Long, Order> activeOrders = new ConcurrentHashMap<>();
    
    public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Object> dbQueue, LtpKafkaProducer ltpProducer) {
        this.broadcaster = broadcaster;
        this.dbQueue = dbQueue;
        this.ltpProducer = ltpProducer;
        // Load reference data on startup
        loadReferenceData();
        // Recover unfilled orders from previous sessions
        recoverUnfilledOrders();
        // Wire up the broadcaster to stream active orders to new WS clients
        broadcaster.setActiveOrdersSupplier(() -> activeOrders.values());
        // Wire up cancel handler: UI cancel requests are delegated here
        broadcaster.setCancelHandler(this::processCancelRequest);
        // Wire up audit trail loader: on-demand loading from DB
        broadcaster.setAuditTrailLoader(DatabaseManager::loadAuditTrail);
    }
    
    /**
     * Load reference data (securities, customers) on startup
     */
    private void loadReferenceData() {
        System.out.println("Loading reference data from database...");
        List<Security> securities = DatabaseManager.loadAllSecurities();
        for (Security security : securities) {
            validSecurities.put(security.getSymbol(), security);
        }
        System.out.println("Reference data loaded successfully. Valid symbols: " + validSecurities.keySet());
    }

    /**
     * Recover unfilled orders from the database and re-insert them into Order Books.
     * Also adds them to activeOrders so they get streamed to the frontend on connect.
     */
    private void recoverUnfilledOrders() {
        System.out.println("Recovering unfilled orders from database...");
        List<Order> unfilledOrders = DatabaseManager.loadUnfilledOrders();
        for (Order order : unfilledOrders) {
            OrderBook book = getOrCreateOrderBook(order.getSymbol());
            book.addOrder(order);
            // Track in activeOrders for streaming to new UI clients
            activeOrders.put(order.getOrderId(), order);
        }
        if (!unfilledOrders.isEmpty()) {
            System.out.println("Recovered " + unfilledOrders.size() + " unfilled orders into order books.");
        }
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
    System.out.println("Session Created: " + sessionId);
    }
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("LOGON Success: " + sessionId);
        this.currentSessionID = sessionId;
    }
    @Override
    public void onLogout(SessionID sessionId) {
    System.out.println("LOGOUT: " + sessionId);
    }
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
    // Used for administrative messages (Heartbeats, Logons)
    }
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, RejectLogon {
    // Received administrative messages
    }
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
    // Outgoing business messages
    }
    @Override
   public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
    IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        // 1. Identify Message Type
        long ingressTime = System.nanoTime(); // Start Clock
        String msgType = message.getHeader().getString(MsgType.FIELD);
        if (msgType.equals(MsgType.ORDER_SINGLE)) {
        processNewOrder(message, sessionId, ingressTime);
        } else if (msgType.equals(MsgType.ORDER_CANCEL_REQUEST)) {
        processFixCancelRequest(message, sessionId, ingressTime);
        } else {
        System.out.println("Received unknown message type: " + msgType);
        }
    }

    private void processNewOrder(Message message, SessionID sessionId, long ingressTime) {
        try {
            // 2. Extract Fields using QuickFIX types
            // cl_ord_id is a BIGINT from the MiniFIX client
            long clOrdId = Long.parseLong(message.getString(ClOrdID.FIELD));
            String symbol = message.getString(Symbol.FIELD);
            char side = message.getChar(Side.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);
            double price = message.getDouble(Price.FIELD);
            
            // Validate symbol against security master
            if (!validSecurities.containsKey(symbol)) {
                System.out.println("REJECTED: Unknown Security Symbol: " + symbol);
                sendReject(message, sessionId, "Unknown Security Symbol", ingressTime);
                return;
            }
            
            // Create Order POJO with timestamp-based ID
            long orderId = IdGenerator.next();
            Order order = new Order(orderId, clOrdId, symbol, side, price, qty);
            
            // Track in activeOrders (for streaming to new UI clients)
            activeOrders.put(orderId, order);
            
            // Broadcast order to WebSocket clients (includes status=NEW, originalQuantity)
            broadcaster.broadcastOrder(order);
            
            // Audit: ORDER_NEW
            AuditEvent newEvent = new AuditEvent(IdGenerator.next(), orderId,
                "ORDER_NEW", null, "NEW",
                String.format("New %s order: %s %.0f@%.2f", side == '1' ? "BUY" : "SELL", symbol, qty, price));
            dbQueue.offer(newEvent);
            broadcaster.broadcastAuditEvent(newEvent);
            
            // 3. Validation (Simple Rule: Price and Qty must be positive)
            if (qty <= 0 || price <= 0) {
                sendReject(message, sessionId, "Invalid Price or Qty", ingressTime);
                // Audit: ORDER_REJECTED
                AuditEvent rejEvent = new AuditEvent(IdGenerator.next(), orderId,
                    "ORDER_REJECTED", "NEW", "REJECTED", "Invalid Price or Qty");
                dbQueue.offer(rejEvent);
                broadcaster.broadcastAuditEvent(rejEvent);
            } else {
                // Send ACK first (Low Latency)
                acceptOrder(message, sessionId, ingressTime);
                // Then queue for storage (Async via OrderPersister worker)
                dbQueue.offer(order);
                
                // Audit: ORDER_ACK
                AuditEvent ackEvent = new AuditEvent(IdGenerator.next(), orderId,
                    "ORDER_ACK", "NEW", "NEW", "Order acknowledged");
                dbQueue.offer(ackEvent);
                broadcaster.broadcastAuditEvent(ackEvent);
                
                // 4. Match the order using the Order Book for this symbol
                OrderBook book = getOrCreateOrderBook(symbol);
                MatchResult result = book.match(order);
                
                // 5. Process executions (if any trades happened)
                if (result.hasTrades()) {
                    for (Execution trade : result.getExecutions()) {
                        // 1. Persist to DB (Async via OrderPersister worker)
                        dbQueue.offer(trade);
                        // 2. Notify User Interface
                        broadcaster.sendTradeUpdate(trade);
                        // 3. Send FIX Message to Client
                        sendFillReport(trade, sessionId, ingressTime);
                        
                        // 4. Publish LTP to Kafka for downstream services (option pricing, etc.)
                        if (ltpProducer != null) {
                            ltpProducer.sendLtp(trade.getSymbol(), trade.getExecPrice(), trade.getMatchTimeMicros());
                        }
                    }
                    
                    // 6. Broadcast + persist status updates for RESTING orders that were affected
                    for (Order restingOrder : result.getAffectedRestingOrders()) {
                        // Persist resting order's status change (async)
                        dbQueue.offer(new OrderStatusUpdate(
                            restingOrder.getOrderId(), restingOrder.getStatus(),
                            restingOrder.getQuantity(), restingOrder.getOriginalQuantity()
                        ));
                        // Broadcast resting order's fill status change to UI
                        broadcaster.sendOrderStatusUpdate(restingOrder);
                        
                        // Audit: fill status change for resting order
                        String fillEventType = "FILLED".equals(restingOrder.getStatus()) 
                            ? "ORDER_FILLED" : "ORDER_PARTIAL_FILL";
                        AuditEvent restFillEvent = new AuditEvent(IdGenerator.next(), restingOrder.getOrderId(),
                            fillEventType, "NEW", restingOrder.getStatus(),
                            String.format("Resting order filled: qty remaining %.0f", restingOrder.getQuantity()));
                        dbQueue.offer(restFillEvent);
                        broadcaster.broadcastAuditEvent(restFillEvent);
                    }
                }
                
                // 7. Persist incoming order status update (fill state) asynchronously
                dbQueue.offer(new OrderStatusUpdate(
                    order.getOrderId(), order.getStatus(),
                    order.getQuantity(), order.getOriginalQuantity()
                ));
                // Broadcast incoming order's fill status to UI
                broadcaster.sendOrderStatusUpdate(order);
                
                // Audit: fill status for incoming order (if it matched)
                if (!"NEW".equals(order.getStatus())) {
                    String incomingFillType = "FILLED".equals(order.getStatus()) 
                        ? "ORDER_FILLED" : "ORDER_PARTIAL_FILL";
                    AuditEvent incomingFillEvent = new AuditEvent(IdGenerator.next(), order.getOrderId(),
                        incomingFillType, "NEW", order.getStatus(),
                        String.format("Incoming order fill: qty remaining %.0f", order.getQuantity()));
                    dbQueue.offer(incomingFillEvent);
                    broadcaster.broadcastAuditEvent(incomingFillEvent);
                }
            }
        } 
        catch (FieldNotFound e) {
            e.printStackTrace();
        }
    }

    /**
     * Process cancel request from UI WebSocket.
     * Validates the order state, removes from OrderBook, updates status, persists, and broadcasts.
     * Also sends FIX cancel response if FIX session is available.
     */
    public void processCancelRequest(WebSocket conn, long orderId) {
        Order order = activeOrders.get(orderId);
        
        if (order == null) {
            broadcaster.sendCancelResponse(conn, orderId, "CANCEL_REJECTED", "Order not found");
            System.out.println("Cancel REJECTED for " + orderId + ": Order not found");
            return;
        }
        
        if (!order.isCancellable()) {
            String reason = "Order already " + order.getStatus().toLowerCase().replace('_', ' ');
            broadcaster.sendCancelResponse(conn, orderId, "CANCEL_REJECTED", reason);
            System.out.println("Cancel REJECTED for " + orderId + ": " + reason);
            
            // Audit: CANCEL_REJECTED (persisted for audit trail, status doesn't change in DB)
            AuditEvent rejEvent = new AuditEvent(IdGenerator.next(), orderId,
                "CANCEL_REJECTED", order.getStatus(), order.getStatus(), reason);
            dbQueue.offer(rejEvent);
            broadcaster.broadcastAuditEvent(rejEvent);
            return;
        }
        
        // Step 1: Broadcast CANCEL_PENDING to all UIs (transient state, not persisted)
        broadcaster.broadcastCancelUpdate(orderId, "CANCEL_PENDING",
            order.getQuantity(), order.getOriginalQuantity());
        
        // Audit: CANCEL_REQUESTED
        String previousStatus = order.getStatus();
        AuditEvent reqEvent = new AuditEvent(IdGenerator.next(), orderId,
            "CANCEL_REQUESTED", previousStatus, "CANCEL_PENDING", "Cancel requested by user");
        dbQueue.offer(reqEvent);
        broadcaster.broadcastAuditEvent(reqEvent);
        
        // Step 2: Remove from OrderBook
        OrderBook book = orderBooks.get(order.getSymbol());
        if (book != null) {
            book.cancelOrder(orderId);
        }
        
        // Step 3: Set final cancelled status on the order object
        String fromStatus = order.cancel(); // Returns previous status, sets CANCELLED or PARTIALLY_CANCELLED
        String finalStatus = order.getStatus();
        
        // Step 4: Persist the status change to DB
        dbQueue.offer(new OrderStatusUpdate(
            orderId, finalStatus, order.getQuantity(), order.getOriginalQuantity()
        ));
        
        // Step 5: Broadcast final cancel state to all UIs
        broadcaster.broadcastCancelUpdate(orderId, finalStatus,
            order.getQuantity(), order.getOriginalQuantity());
        
        // Step 6: Send targeted cancel response to the requesting UI client
        broadcaster.sendCancelResponse(conn, orderId, finalStatus, null);
        
        // Step 7: Send FIX ExecutionReport with OrdStatus.CANCELED if FIX session is active
        if (currentSessionID != null) {
            sendFixCancelConfirm(order);
        }
        
        // Audit: CANCEL_ACCEPTED
        AuditEvent acceptEvent = new AuditEvent(IdGenerator.next(), orderId,
            "CANCEL_ACCEPTED", fromStatus, finalStatus,
            String.format("Order cancelled. Remaining qty: %.0f", order.getQuantity()));
        dbQueue.offer(acceptEvent);
        broadcaster.broadcastAuditEvent(acceptEvent);
        
        System.out.println("Cancel ACCEPTED for " + orderId + ": " + fromStatus + " → " + finalStatus);
    }

    /**
     * Process cancel request arriving via FIX protocol (MsgType F).
     */
    private void processFixCancelRequest(Message message, SessionID sessionId, long ingressTime) {
        try {
            long origOrderId = Long.parseLong(message.getString(OrigClOrdID.FIELD));
            
            // Look up in active orders
            Order order = activeOrders.get(origOrderId);
            if (order == null) {
                sendFixCancelReject(message, sessionId, "Unknown order", ingressTime);
                return;
            }
            
            if (!order.isCancellable()) {
                sendFixCancelReject(message, sessionId, 
                    "Order already " + order.getStatus().toLowerCase().replace('_', ' '), ingressTime);
                return;
            }
            
            // Remove from book
            OrderBook book = orderBooks.get(order.getSymbol());
            if (book != null) {
                book.cancelOrder(origOrderId);
            }
            
            String fromStatus = order.cancel();
            String finalStatus = order.getStatus();
            
            // Persist
            dbQueue.offer(new OrderStatusUpdate(
                origOrderId, finalStatus, order.getQuantity(), order.getOriginalQuantity()
            ));
            
            // Broadcast to all UIs
            broadcaster.broadcastCancelUpdate(origOrderId, finalStatus,
                order.getQuantity(), order.getOriginalQuantity());
            
            // FIX confirm
            sendFixCancelConfirm(order);
            
            // Audit
            AuditEvent event = new AuditEvent(IdGenerator.next(), origOrderId,
                "CANCEL_ACCEPTED", fromStatus, finalStatus, "Cancel via FIX protocol");
            dbQueue.offer(event);
            broadcaster.broadcastAuditEvent(event);
            
            long egressTime = System.nanoTime();
            PerformanceMonitor.recordLatency(egressTime - ingressTime);
            
        } catch (FieldNotFound e) {
            e.printStackTrace();
        }
    }

    /**
     * Send FIX ExecutionReport with OrdStatus.CANCELED confirming the cancel.
     */
    private void sendFixCancelConfirm(Order order) {
        try {
            quickfix.fix44.ExecutionReport cancelReport = new quickfix.fix44.ExecutionReport();
            cancelReport.set(new OrderID(String.valueOf(order.getOrderId())));
            cancelReport.set(new ExecID(String.valueOf(IdGenerator.next())));
            cancelReport.set(new ClOrdID(String.valueOf(order.getClOrdID())));
            cancelReport.set(new Symbol(order.getSymbol()));
            cancelReport.set(new Side(order.getSide()));
            cancelReport.set(new ExecType(ExecType.CANCELED));
            cancelReport.set(new OrdStatus(OrdStatus.CANCELED));
            cancelReport.set(new LeavesQty(order.getQuantity()));
            cancelReport.set(new CumQty(order.getOriginalQuantity() - order.getQuantity()));
            cancelReport.set(new AvgPx(0));
            Session.sendToTarget(cancelReport, currentSessionID);
        } catch (Exception e) {
            System.err.println("Error sending FIX cancel confirm: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send FIX OrderCancelReject (MsgType = 9) when cancel cannot be performed.
     */
    private void sendFixCancelReject(Message request, SessionID sessionId, String reason, long ingressTime) {
        try {
            quickfix.fix44.OrderCancelReject reject = new quickfix.fix44.OrderCancelReject();
            reject.set(new OrderID(request.getString(OrigClOrdID.FIELD)));
            reject.set(new ClOrdID(request.getString(ClOrdID.FIELD)));
            reject.set(new OrigClOrdID(request.getString(OrigClOrdID.FIELD)));
            reject.set(new OrdStatus(OrdStatus.REJECTED));
            reject.set(new CxlRejResponseTo(CxlRejResponseTo.ORDER_CANCEL_REQUEST));
            reject.set(new Text(reason));
            Session.sendToTarget(reject, sessionId);
            long egressTime = System.nanoTime();
            PerformanceMonitor.recordLatency(egressTime - ingressTime);
        } catch (Exception e) {
            System.err.println("Error sending FIX cancel reject: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Get or create an OrderBook for a specific symbol
     * Synchronized to ensure thread-safe creation
     */
    private synchronized OrderBook getOrCreateOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, k -> {
            System.out.println("Creating new OrderBook for symbol: " + symbol);
            return new OrderBook();
        });
    }

    private void acceptOrder(Message request, SessionID sessionId, long ingressTime) {
    try {
        // Create an ExecutionReport (MsgType = 8)
        quickfix.fix44.ExecutionReport ack = new quickfix.fix44.ExecutionReport();
        // Mandatory Fields mapping
        ack.set(new OrderID("GEN_" + System.currentTimeMillis())); // Server generated ID
        ack.set(new ExecID("EXEC_" + System.currentTimeMillis()));
        ack.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
        ack.set(new Symbol(request.getString(Symbol.FIELD)));
        ack.set(new Side(request.getChar(Side.FIELD)));
        // Status fields: "New"
        ack.set(new ExecType(ExecType.NEW));
        ack.set(new OrdStatus(OrdStatus.NEW));
        // Quantity accounting
        ack.set(new LeavesQty(request.getDouble(OrderQty.FIELD)));
        ack.set(new CumQty(0));
        ack.set(new AvgPx(0));
        // Send back to the specific session
        Session.sendToTarget(ack, sessionId);
        long egressTime = System.nanoTime(); // Stop Clock
        long latency = egressTime - ingressTime;
        PerformanceMonitor.recordLatency(latency);
    } 
    catch (Exception e) {
        e.printStackTrace();
    }
}

    private void sendFillReport(Execution trade, SessionID sessionId, long ingressTime) {
        try {
            quickfix.fix44.ExecutionReport fixTrade = new quickfix.fix44.ExecutionReport();
            // Critical Fields for a Fill — use aggressor's order ID for FIX report
            fixTrade.set(new OrderID(String.valueOf(trade.getAggressorOrderId())));
            fixTrade.set(new ExecID(String.valueOf(trade.getExecId())));
            fixTrade.set(new ExecType(ExecType.TRADE)); // 'F'
            fixTrade.set(new OrdStatus(OrdStatus.FILLED)); // '2'
            fixTrade.set(new Symbol(trade.getSymbol()));
            fixTrade.set(new Side(trade.getSide()));
            fixTrade.set(new ClOrdID("TRADE_" + trade.getExecId())); // Client order ID
            // Trade Details
            fixTrade.set(new LastPx(trade.getExecPrice()));
            fixTrade.set(new LastQty(trade.getExecQty()));
            fixTrade.set(new CumQty(trade.getExecQty()));
            fixTrade.set(new LeavesQty(0)); // Assuming full fill for simplicity
            fixTrade.set(new AvgPx(trade.getExecPrice()));
            Session.sendToTarget(fixTrade, sessionId);
            long egressTime = System.nanoTime(); // Stop Clock
            long latency = egressTime - ingressTime;
            PerformanceMonitor.recordLatency(latency);
        } catch (Exception e) {
            System.err.println("Error sending fill report: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void sendReject(Message request, SessionID sessionId, String reason, long ingressTime) {
    try {
        // Create an ExecutionReport (MsgType = 8) for rejection
        quickfix.fix44.ExecutionReport reject = new quickfix.fix44.ExecutionReport();
        // Mandatory Fields mapping
        reject.set(new OrderID("REJ_" + System.currentTimeMillis())); // Server generated ID
        reject.set(new ExecID("EXEC_" + System.currentTimeMillis()));
        reject.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
        reject.set(new Symbol(request.getString(Symbol.FIELD)));
        reject.set(new Side(request.getChar(Side.FIELD)));
        // Status fields: "Rejected"
        reject.set(new ExecType(ExecType.REJECTED));
        reject.set(new OrdStatus(OrdStatus.REJECTED));
        // Quantity accounting (zero for rejected orders)
        reject.set(new LeavesQty(0));
        reject.set(new CumQty(0));
        reject.set(new AvgPx(0));
        // Rejection reason
        reject.set(new Text(reason));
        System.out.println("ORDER REJECTED: " + reason);
        // Send back to the specific session
        Session.sendToTarget(reject, sessionId);
        long egressTime = System.nanoTime(); // Stop Clock
        long latency = egressTime - ingressTime;
        PerformanceMonitor.recordLatency(latency);
    } 
    catch (Exception e) {
        e.printStackTrace();
    }
}
}
