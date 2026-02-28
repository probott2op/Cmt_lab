package com.lab2;

import java.util.concurrent.BlockingQueue;

public class OrderPersister implements Runnable {
    private final BlockingQueue<Order> queue;
    private volatile boolean running = true;
    public OrderPersister(BlockingQueue<Order> queue) {
    this.queue = queue;
    }
    @Override
    public void run() {
    System.out.println("Persistence Worker Started...");
    while (running) {
    try {
    // take() blocks until an item is available
    Order order = queue.take();
    DatabaseManager.insertOrder(order);
    System.out.println("Persisted Order: " + order.getClOrdID());
    } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    }
    }
    }
    public void stop() {
    this.running = false;
    }
}
