package com.lab2;

import java.util.concurrent.BlockingQueue;

/**
 * Async persistence worker that consumes from the shared dbQueue.
 * Handles Orders, Executions, and OrderStatusUpdates.
 * 
 * Runs on a dedicated thread to keep the order processing hot path
 * free from DB I/O latency.
 */
public class OrderPersister implements Runnable {
    private final BlockingQueue<Object> queue;
    private volatile boolean running = true;

    public OrderPersister(BlockingQueue<Object> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        System.out.println("Persistence Worker Started...");
        while (running) {
            try {
                // take() blocks until an item is available
                Object obj = queue.take();
                
                if (obj instanceof Order) {
                    Order order = (Order) obj;
                    DatabaseManager.insertOrder(order);
                } else if (obj instanceof Execution) {
                    Execution execution = (Execution) obj;
                    DatabaseManager.insertExecution(execution);
                } else if (obj instanceof OrderStatusUpdate) {
                    OrderStatusUpdate update = (OrderStatusUpdate) obj;
                    DatabaseManager.updateOrderStatus(update.getOrderId(), update.getStatus(), update.getRemainingQty());
                } else if (obj instanceof AuditEvent) {
                    AuditEvent event = (AuditEvent) obj;
                    DatabaseManager.insertAuditEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void stop() {
        this.running = false;
    }
}
