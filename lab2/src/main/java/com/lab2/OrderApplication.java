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
import java.util.concurrent.BlockingQueue;

public class OrderApplication implements Application {
    private OrderBroadcaster broadcaster;
    private BlockingQueue<Order> dbQueue;
    
    public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Order> dbQueue) {
        this.broadcaster = broadcaster;
        this.dbQueue = dbQueue;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
    System.out.println("Session Created: " + sessionId);
    }
    @Override
    public void onLogon(SessionID sessionId) {
    System.out.println("LOGON Success: " + sessionId);
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
        String msgType = message.getHeader().getString(MsgType.FIELD);
        if (msgType.equals(MsgType.ORDER_SINGLE)) {
        processNewOrder(message, sessionId);
        } else {
        System.out.println("Received unknown message type: " + msgType);
        }
    }

    private void processNewOrder(Message message, SessionID sessionId) {
        try {
            // 2. Extract Fields using QuickFIX types
            String clOrdId = message.getString(ClOrdID.FIELD);
            String symbol = message.getString(Symbol.FIELD);
            char side = message.getChar(Side.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);
            double price = message.getDouble(Price.FIELD);
            System.out.printf("ORDER RECEIVED: ID=%s Side=%s Sym=%s Px=%.2f Qty=%.0f%n",
            clOrdId, (side == '1' ? "BUY" : "SELL"), symbol, price, qty);
            
            // Create Order POJO
            Order order = new Order(clOrdId, symbol, side, price, qty);
            
            // Broadcast order to WebSocket clients
            broadcaster.broadcastOrder(order);
            
            // 3. Validation (Simple Rule: Price and Qty must be positive)
            if (qty <= 0 || price <= 0) {
            sendReject(message, sessionId, "Invalid Price or Qty");
            } else {
            // Send ACK first (Low Latency)
            acceptOrder(message, sessionId);
            // Then queue for storage (Async)
            dbQueue.offer(order);
        }
        } 
        catch (FieldNotFound e) {
            e.printStackTrace();
        }
    }

    private void acceptOrder(Message request, SessionID sessionId) {
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
    } 
    catch (Exception e) {
        e.printStackTrace();
    }
}

    private void sendReject(Message request, SessionID sessionId, String reason) {
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
    } 
    catch (Exception e) {
        e.printStackTrace();
    }
}
}
