package com.lab2;

import java.math.BigDecimal;

public class Customer {
    private String customerCode;
    private String customerName;
    private String customerType;
    private BigDecimal creditLimit;

    public Customer(String customerCode, String customerName, String customerType, BigDecimal creditLimit) {
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.customerType = customerType;
        this.creditLimit = creditLimit;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    @Override
    public String toString() {
        return "Customer{" +
                "customerCode='" + customerCode + '\'' +
                ", customerName='" + customerName + '\'' +
                ", customerType='" + customerType + '\'' +
                ", creditLimit=" + creditLimit +
                '}';
    }
}
