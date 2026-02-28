package com.lab2;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
public class AppLauncher {
    public static void main(String[] args) {
        try {
            // Start WebSocket Server
            OrderBroadcaster server = new OrderBroadcaster(8080);
            server.start();
            System.out.println("WebSocket Server started on port 8080");
            
            SessionSettings settings = new SessionSettings("order-service.cfg");
            OrderApplication application = new OrderApplication(server);
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            ScreenLogFactory logFactory = new ScreenLogFactory(settings);
            DefaultMessageFactory messageFactory = new DefaultMessageFactory();
            SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings,
            logFactory, messageFactory);
            acceptor.start();
            System.out.println("Order Service started. Listening on port 9876...");
            // Keep the process running
            System.in.read();
            acceptor.stop();
        } 
        catch (Exception e) {
        e.printStackTrace();
        }
    }
}