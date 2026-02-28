package com.lab2;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
public class OrderBroadcaster extends WebSocketServer {
    
    public OrderBroadcaster(int port) {
    super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    System.out.println("UI Connected: " + conn.getRemoteSocketAddress());
    }
    @Override
    public void onMessage(WebSocket conn, String message) {
    // We generally don't expect messages FROM the UI in this lab
    }
    
    public void broadcastOrder(Order order) {
    // Convert Order object to JSON string
    String json = new com.google.gson.Gson().toJson(order);
    // Send to all connected UIs
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
