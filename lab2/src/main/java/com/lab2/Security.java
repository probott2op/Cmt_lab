package com.lab2;

public class Security {
    private String symbol;
    private String securityType;
    private String description;
    private String underlying;
    private int lotSize;

    public Security(String symbol, String securityType, String description, String underlying, int lotSize) {
        this.symbol = symbol;
        this.securityType = securityType;
        this.description = description;
        this.underlying = underlying;
        this.lotSize = lotSize;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSecurityType() {
        return securityType;
    }

    public void setSecurityType(String securityType) {
        this.securityType = securityType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUnderlying() {
        return underlying;
    }

    public void setUnderlying(String underlying) {
        this.underlying = underlying;
    }

    public int getLotSize() {
        return lotSize;
    }

    public void setLotSize(int lotSize) {
        this.lotSize = lotSize;
    }

    @Override
    public String toString() {
        return "Security{" +
                "symbol='" + symbol + '\'' +
                ", securityType='" + securityType + '\'' +
                ", description='" + description + '\'' +
                ", underlying='" + underlying + '\'' +
                ", lotSize=" + lotSize +
                '}';
    }
}
