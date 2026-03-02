package com.lab2;

import java.util.concurrent.BlockingQueue;

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
                    System.out.println("Persisted Order: " + order.getClOrdID());
                } else if (obj instanceof Execution) {
                    Execution execution = (Execution) obj;
                    DatabaseManager.insertExecution(execution);
                    System.out.println("Persisted Execution: " + execution.getExecId());
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
