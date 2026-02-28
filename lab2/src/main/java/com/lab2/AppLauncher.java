package com.lab2;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AppLauncher {
    public static void main(String[] args) {
        try {
            // Start WebSocket Server
            OrderBroadcaster server = new OrderBroadcaster(8080);
            server.start();
            System.out.println("WebSocket Server started on port 8080");
            
            // 1. Create the shared queue
            BlockingQueue<Order> dbQueue = new LinkedBlockingQueue<>();
            
            // 2. Start the Consumer (Worker)
            OrderPersister persister = new OrderPersister(dbQueue);
            new Thread(persister).start();
            System.out.println("Database persistence worker started");
            
            SessionSettings settings = new SessionSettings("order-service.cfg");
            // 3. Pass the Queue to the Producer (Application)
            OrderApplication application = new OrderApplication(server, dbQueue);
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