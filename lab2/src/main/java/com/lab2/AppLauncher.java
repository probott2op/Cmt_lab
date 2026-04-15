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
        LtpKafkaProducer ltpProducer = null;
        try {
            // Start WebSocket Server
            OrderBroadcaster server = new OrderBroadcaster(8081);
            server.start();
            System.out.println("WebSocket Server started on port 8081");
            
            // 1. Create the shared queue for Orders, Executions, and OrderStatusUpdates
            BlockingQueue<Object> dbQueue = new LinkedBlockingQueue<>();
            
            // 2. Start the Consumer (Persistence Worker)
            OrderPersister persister = new OrderPersister(dbQueue);
            new Thread(persister).start();
            System.out.println("Database persistence worker started");

            // 3. Initialize Kafka LTP Producer
            ltpProducer = new LtpKafkaProducer("localhost:9092");
            
            // 4. Create OrderApplication with all dependencies
            SessionSettings settings = new SessionSettings("order-service.cfg");
            OrderApplication application = new OrderApplication(server, dbQueue, ltpProducer);
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            // ScreenLogFactory logFactory = new ScreenLogFactory(settings);
            NoOpLogFactory logFactory = new NoOpLogFactory(); // Disable FIX logging for cleaner output
            DefaultMessageFactory messageFactory = new DefaultMessageFactory();
            SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);

            // 5. Register shutdown hook for clean Kafka producer close
            final LtpKafkaProducer producerRef = ltpProducer;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down...");
                if (producerRef != null) {
                    producerRef.close();
                }
                persister.stop();
                acceptor.stop();
            }));

            acceptor.start();
            System.out.println("Order Service started. Listening on port 9876...");
            // Keep the process running
            System.in.read();
            acceptor.stop();
        } 
        catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ltpProducer != null) {
                ltpProducer.close();
            }
        }
    }
}