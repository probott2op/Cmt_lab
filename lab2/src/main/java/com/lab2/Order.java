package com.lab2;

import java.time.Instant;

public class Order {
    private long orderId;
    private long clOrdID;            // BIGINT from MiniFIX client
    private String symbol;
    private char side;
    private double price;
    private double quantity;
    private double originalQuantity;
    // DB statuses: NEW, PARTIALLY_FILLED, FILLED, CANCELLED, PARTIALLY_CANCELLED
    // Transient UI-only: CANCEL_PENDING (not persisted)
    private String status;
    private long timestampMicros;    // Epoch microseconds
    private transient String cancelReason; // Not serialized to DB — used for WS responses

    public Order(long orderId, long clOrdID, String symbol, char side, double price, double quantity) {
        this.orderId = orderId;
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.originalQuantity = quantity;
        this.status = "NEW";
        Instant now = Instant.now();
        this.timestampMicros = now.getEpochSecond() * 1_000_000L + now.getNano() / 1_000L;
    }

    // Constructor for crash recovery (loading from DB)
    public Order(long orderId, long clOrdID, String symbol, char side, double price,
                 double quantity, double originalQuantity, String status, long timestampMicros) {
        this.orderId = orderId;
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.originalQuantity = originalQuantity;
        this.status = status;
        this.timestampMicros = timestampMicros;
    }

    public long getOrderId() {
        return orderId;
    }

    public void setOrderId(long orderId) {
        this.orderId = orderId;
    }

    public long getClOrdID() {
        return clOrdID;
    }

    public void setClOrdID(long clOrdID) {
        this.clOrdID = clOrdID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public char getSide() {
        return side;
    }

    public void setSide(char side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public double getOriginalQuantity() {
        return originalQuantity;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestampMicros() {
        return timestampMicros;
    }

    public void reduceQty(double qty) {
        this.quantity -= qty;
        if (this.quantity <= 0) {
            this.quantity = 0;
            this.status = "FILLED";
        } else {
            this.status = "PARTIALLY_FILLED";
        }
    }

    /**
     * Can this order be cancelled?
     * Only NEW or PARTIALLY_FILLED orders can be cancelled.
     */
    public boolean isCancellable() {
        return "NEW".equals(status) || "PARTIALLY_FILLED".equals(status);
    }

    /**
     * Cancel this order. 
     * Transitions to CANCELLED (was NEW) or PARTIALLY_CANCELLED (was partially filled).
     * Returns the previous status for audit trail.
     */
    public String cancel() {
        String previousStatus = this.status;
        if ("PARTIALLY_FILLED".equals(this.status)) {
            this.status = "PARTIALLY_CANCELLED";
        } else {
            this.status = "CANCELLED";
        }
        return previousStatus;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }
}
