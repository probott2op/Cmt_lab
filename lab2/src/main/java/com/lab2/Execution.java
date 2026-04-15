package com.lab2;

import java.time.Instant;

public class Execution {
    private long execId;
    private long buyOrderId;        // FK → orders.order_id (buy side)
    private long sellOrderId;       // FK → orders.order_id (sell side)
    private String symbol;
    private char side;              // Aggressor side ('1' = Buy aggressor, '2' = Sell aggressor)
    private int execQty;
    private double execPrice;
    private long matchTimeMicros;   // Epoch microseconds

    // Full constructor
    public Execution(long execId, long buyOrderId, long sellOrderId, String symbol,
                     char side, int execQty, double execPrice, long matchTimeMicros) {
        this.execId = execId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.side = side;
        this.execQty = execQty;
        this.execPrice = execPrice;
        this.matchTimeMicros = matchTimeMicros;
    }

    // Constructor without matchTime — auto-captures microsecond timestamp
    public Execution(long execId, long buyOrderId, long sellOrderId, String symbol,
                     char side, int execQty, double execPrice) {
        this.execId = execId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.side = side;
        this.execQty = execQty;
        this.execPrice = execPrice;
        Instant now = Instant.now();
        this.matchTimeMicros = now.getEpochSecond() * 1_000_000L + now.getNano() / 1_000L;
    }

    // Getters and Setters
    public long getExecId() {
        return execId;
    }

    public void setExecId(long execId) {
        this.execId = execId;
    }

    public long getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(long buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public long getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(long sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    /**
     * Get the aggressor's order ID (for FIX message compatibility).
     * If the aggressor was a buyer, return buyOrderId; otherwise sellOrderId.
     */
    public long getAggressorOrderId() {
        return (side == '1') ? buyOrderId : sellOrderId;
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

    public int getExecQty() {
        return execQty;
    }

    public void setExecQty(int execQty) {
        this.execQty = execQty;
    }

    public double getExecPrice() {
        return execPrice;
    }

    public void setExecPrice(double execPrice) {
        this.execPrice = execPrice;
    }

    public long getMatchTimeMicros() {
        return matchTimeMicros;
    }

    public void setMatchTimeMicros(long matchTimeMicros) {
        this.matchTimeMicros = matchTimeMicros;
    }

    @Override
    public String toString() {
        return String.format("Execution[execId=%d, buyOrderId=%d, sellOrderId=%d, symbol=%s, side=%c, execQty=%d, execPrice=%.2f, matchTimeMicros=%d]",
                execId, buyOrderId, sellOrderId, symbol, side, execQty, execPrice, matchTimeMicros);
    }
}
