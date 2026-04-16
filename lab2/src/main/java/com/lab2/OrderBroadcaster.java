package com.lab2;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class OrderBroadcaster extends WebSocketServer {
    
    /**
     * Custom Gson instance that serializes all long/Long values as JSON strings.
     * 
     * WHY: Order IDs like 17761535713005333 exceed JavaScript's Number.MAX_SAFE_INTEGER
     * (9007199254740991). When parsed as JSON numbers, JS silently rounds them
     * (e.g., 5333 → 5332), causing "Order not found" on cancel requests.
     * Sending as strings preserves exact precision in the browser.
     */
    private final Gson gson = new GsonBuilder()
        .registerTypeAdapter(long.class, (JsonSerializer<Long>) (src, typeOfSrc, context) ->
            new JsonPrimitive(String.valueOf(src)))
        .registerTypeAdapter(Long.class, (JsonSerializer<Long>) (src, typeOfSrc, context) ->
            new JsonPrimitive(String.valueOf(src)))
        .create();

    // Supplier to fetch current active orders for streaming to newly connected clients
    private Supplier<Collection<Order>> activeOrdersSupplier;
    // Handler for cancel requests from UI (delegated to OrderApplication)
    private BiConsumer<WebSocket, Long> cancelHandler;
    // Handler for audit trail requests from UI (on-demand loading)
    private Function<Long, List<AuditEvent>> auditTrailLoader;

    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    /**
     * Set the supplier that provides the current list of active orders.
     * Called by OrderApplication after startup + recovery.
     */
    public void setActiveOrdersSupplier(Supplier<Collection<Order>> supplier) {
        this.activeOrdersSupplier = supplier;
    }

    /**
     * Set the handler for UI cancel requests.
     * OrderApplication wires this up to processCancelRequest().
     */
    public void setCancelHandler(BiConsumer<WebSocket, Long> handler) {
        this.cancelHandler = handler;
    }

    /**
     * Set the handler for loading audit trail on demand.
     */
    public void setAuditTrailLoader(Function<Long, List<AuditEvent>> loader) {
        this.auditTrailLoader = loader;
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("UI Connected: " + conn.getRemoteSocketAddress());
        // Stream all active orders (recovered + current session) to the newly connected client
        if (activeOrdersSupplier != null) {
            Collection<Order> activeOrders = activeOrdersSupplier.get();
            for (Order order : activeOrders) {
                String json = "{\"type\":\"ORDER\",\"data\":" + gson.toJson(order) + "}";
                conn.send(json);
            }
            System.out.println("Streamed " + activeOrders.size() + " active orders to new client");
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String type = json.get("type").getAsString();

            switch (type) {
                case "CANCEL_REQUEST": {
                    // orderId may arrive as string (from our serialization) or number — handle both
                    JsonElement orderIdElem = json.getAsJsonObject("data").get("orderId");
                    long orderId = orderIdElem.isJsonPrimitive() && orderIdElem.getAsJsonPrimitive().isString()
                        ? Long.parseLong(orderIdElem.getAsString())
                        : orderIdElem.getAsLong();
                    System.out.println("Cancel request from UI for order: " + orderId);
                    if (cancelHandler != null) {
                        cancelHandler.accept(conn, orderId);
                    } else {
                        sendToClient(conn, "CANCEL_REJECTED", orderId, "Cancel handler not available");
                    }
                    break;
                }
                case "AUDIT_REQUEST": {
                    JsonElement orderIdElem = json.getAsJsonObject("data").get("orderId");
                    long orderId = orderIdElem.isJsonPrimitive() && orderIdElem.getAsJsonPrimitive().isString()
                        ? Long.parseLong(orderIdElem.getAsString())
                        : orderIdElem.getAsLong();
                    System.out.println("Audit trail request for order: " + orderId);
                    if (auditTrailLoader != null) {
                        List<AuditEvent> events = auditTrailLoader.apply(orderId);
                        // Use %s for orderId to emit it as a quoted string
                        String responseJson = String.format(
                            "{\"type\":\"AUDIT_TRAIL\",\"data\":{\"orderId\":\"%s\",\"events\":%s}}",
                            orderId, gson.toJson(events)
                        );
                        conn.send(responseJson);
                    }
                    break;
                }
                default:
                    System.out.println("Unknown message type from UI: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error parsing UI message: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void broadcastOrder(Order order) {
        String json = "{\"type\":\"ORDER\",\"data\":" + gson.toJson(order) + "}";
        broadcast(json);
    }
    
    public void sendTradeUpdate(Execution execution) {
        String json = "{\"type\":\"TRADE\",\"data\":" + gson.toJson(execution) + "}";
        broadcast(json);
    }

    /**
     * Broadcast an order status update (fill state change) to all connected UIs.
     * IDs are emitted as strings to preserve precision in JavaScript.
     */
    public void sendOrderStatusUpdate(Order order) {
        // %s with String.valueOf for IDs to emit as quoted JSON strings
        String json = String.format(
            "{\"type\":\"ORDER_UPDATE\",\"data\":{\"orderId\":\"%s\",\"status\":\"%s\",\"remainingQty\":%.0f,\"originalQuantity\":%.0f}}",
            order.getOrderId(), order.getStatus(), order.getQuantity(), order.getOriginalQuantity()
        );
        broadcast(json);
    }

    /**
     * Send a cancel response to a specific client (the one who requested the cancel).
     */
    public void sendCancelResponse(WebSocket requestor, long orderId, String status, String reason) {
        String json = String.format(
            "{\"type\":\"CANCEL_RESPONSE\",\"data\":{\"orderId\":\"%s\",\"status\":\"%s\",\"reason\":\"%s\"}}",
            orderId, status, reason != null ? reason : ""
        );
        if (requestor != null && requestor.isOpen()) {
            requestor.send(json);
        }
    }

    /**
     * Broadcast a cancel status update to ALL connected UIs.
     */
    public void broadcastCancelUpdate(long orderId, String status, double remainingQty, double originalQty) {
        String json = String.format(
            "{\"type\":\"ORDER_UPDATE\",\"data\":{\"orderId\":\"%s\",\"status\":\"%s\",\"remainingQty\":%.0f,\"originalQuantity\":%.0f}}",
            orderId, status, remainingQty, originalQty
        );
        broadcast(json);
    }

    /**
     * Broadcast an audit event to all connected UIs.
     */
    public void broadcastAuditEvent(AuditEvent event) {
        String json = "{\"type\":\"AUDIT_EVENT\",\"data\":" + gson.toJson(event) + "}";
        broadcast(json);
    }

    /**
     * Send a message to a specific client.
     */
    private void sendToClient(WebSocket conn, String type, long orderId, String reason) {
        String json = String.format(
            "{\"type\":\"%s\",\"data\":{\"orderId\":\"%s\",\"reason\":\"%s\"}}",
            type, orderId, reason
        );
        if (conn.isOpen()) {
            conn.send(json);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("UI Disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket Error: " + ex.getMessage());
        if (conn != null) {
            System.err.println("Connection: " + conn.getRemoteSocketAddress());
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket Server started successfully on port " + getPort());
    }
}
