package com.lab2;

public class Order {
    private String clOrdID;
    private String symbol;
    private char side;
    private double price;
    private double quantity;

    public Order(String clOrdID, String symbol, char side, double price, double quantity) {
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
    }
    public String getClOrdID() {
        return clOrdID;
    }
    
    public void setClOrdID(String clOrdID) {
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
}
