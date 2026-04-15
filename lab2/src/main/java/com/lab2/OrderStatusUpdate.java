package com.lab2;

/**
 * Lightweight POJO queued to the persistence worker (OrderPersister)
 * whenever an order's fill status changes after matching.
 * 
 * This keeps the hot path (order processing) fast by deferring
 * the DB UPDATE to the async worker thread.
 */
public class OrderStatusUpdate {
    private final long orderId;
    private final String status;
    private final double remainingQty;
    private final double originalQty;

    public OrderStatusUpdate(long orderId, String status, double remainingQty, double originalQty) {
        this.orderId = orderId;
        this.status = status;
        this.remainingQty = remainingQty;
        this.originalQty = originalQty;
    }

    public long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public double getRemainingQty() {
        return remainingQty;
    }

    public double getOriginalQty() {
        return originalQty;
    }

    @Override
    public String toString() {
        return String.format("OrderStatusUpdate[orderId=%d, status=%s, remainingQty=%.0f, originalQty=%.0f]",
                orderId, status, remainingQty, originalQty);
    }
}
