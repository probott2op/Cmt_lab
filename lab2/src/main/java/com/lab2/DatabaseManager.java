package com.lab2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/trading_system";
    private static final String USER = "root"; // Use environment variables for credentials
    private static final String PASS = "root@fintech"; // Use env variables in production!
    
    public static void insertOrder(Order order) {
        
        String sql = "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getQuantity());
            pstmt.setString(7, "NEW");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Load all securities from security_master table
     * @return List of Security objects
     */
    public static List<Security> loadAllSecurities() {
        List<Security> securities = new ArrayList<>();
        String sql = "SELECT symbol, security_type, description, underlying, lot_size FROM security_master";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Security security = new Security(
                    rs.getString("symbol"),
                    rs.getString("security_type"),
                    rs.getString("description"),
                    rs.getString("underlying"),
                    rs.getInt("lot_size")
                );
                securities.add(security);
            }
            System.out.println("Loaded " + securities.size() + " securities from database");
        } catch (SQLException e) {
            System.err.println("Error loading securities: " + e.getMessage());
            e.printStackTrace();
        }
        
        return securities;
    }
    
    /**
     * Load all customers from customer_master table
     * @return List of Customer objects
     */
    public static List<Customer> loadAllCustomers() {
        List<Customer> customers = new ArrayList<>();
        String sql = "SELECT customer_code, customer_name, customer_type, credit_limit FROM customer_master";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Customer customer = new Customer(
                    rs.getString("customer_code"),
                    rs.getString("customer_name"),
                    rs.getString("customer_type"),
                    rs.getBigDecimal("credit_limit")
                );
                customers.add(customer);
            }
            System.out.println("Loaded " + customers.size() + " customers from database");
        } catch (SQLException e) {
            System.err.println("Error loading customers: " + e.getMessage());
            e.printStackTrace();
        }
        
        return customers;
    }
    
    /**
     * Insert execution into executions table
     * @param execution Execution object to persist
     */
    public static void insertExecution(Execution execution) {
        String sql = "INSERT INTO executions (exec_id, order_id, symbol, side, exec_qty, exec_price) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, execution.getExecId());
            pstmt.setString(2, execution.getOrderId());
            pstmt.setString(3, execution.getSymbol());
            pstmt.setString(4, String.valueOf(execution.getSide()));
            pstmt.setInt(5, execution.getExecQty());
            pstmt.setDouble(6, execution.getExecPrice());
            pstmt.executeUpdate();
            System.out.println("Persisted Execution: " + execution.getExecId());
        } catch (SQLException e) {
            System.err.println("Error inserting execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
