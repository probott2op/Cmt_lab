package com.lab2;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.Gson;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.function.Supplier;

public class OrderBroadcaster extends WebSocketServer {
    
    private final Gson gson = new Gson();
    // Supplier to fetch current active orders for streaming to newly connected clients
    private Supplier<Collection<Order>> activeOrdersSupplier;

    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
    }

    /**
     * Set the supplier that provides the current list of active orders.
     * Called by OrderApplication after startup + recovery.
     * When a new UI client connects, all active orders are sent immediately.
     */
    public void setActiveOrdersSupplier(Supplier<Collection<Order>> supplier) {
        this.activeOrdersSupplier = supplier;
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
        // We generally don't expect messages FROM the UI in this lab
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
     * Sent after matching for BOTH the incoming order AND affected resting orders.
     */
    public void sendOrderStatusUpdate(Order order) {
        String json = String.format(
            "{\"type\":\"ORDER_UPDATE\",\"data\":{\"orderId\":%d,\"status\":\"%s\",\"remainingQty\":%.0f,\"originalQuantity\":%.0f}}",
            order.getOrderId(), order.getStatus(), order.getQuantity(), order.getOriginalQuantity()
        );
        broadcast(json);
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
