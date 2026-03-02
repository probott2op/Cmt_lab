package com.lab2;

import java.sql.Timestamp;

public class Execution {
    private String execId;
    private String orderId;
    private String symbol;
    private char side;
    private int execQty;
    private double execPrice;
    private Timestamp matchTime;

    // Constructor
    public Execution(String execId, String orderId, String symbol, char side, int execQty, double execPrice, Timestamp matchTime) {
        this.execId = execId;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.execQty = execQty;
        this.execPrice = execPrice;
        this.matchTime = matchTime;
    }

    // Constructor without matchTime (will be set by database)
    public Execution(String execId, String orderId, String symbol, char side, int execQty, double execPrice) {
        this.execId = execId;
        this.orderId = orderId;
        this.symbol = symbol;
        this.side = side;
        this.execQty = execQty;
        this.execPrice = execPrice;
        this.matchTime = new Timestamp(System.currentTimeMillis());
    }

    // Getters and Setters
    public String getExecId() {
        return execId;
    }

    public void setExecId(String execId) {
        this.execId = execId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public Timestamp getMatchTime() {
        return matchTime;
    }

    public void setMatchTime(Timestamp matchTime) {
        this.matchTime = matchTime;
    }

    @Override
    public String toString() {
        return String.format("Execution[execId=%s, orderId=%s, symbol=%s, side=%c, execQty=%d, execPrice=%.2f, matchTime=%s]",
                execId, orderId, symbol, side, execQty, execPrice, matchTime);
    }
}
