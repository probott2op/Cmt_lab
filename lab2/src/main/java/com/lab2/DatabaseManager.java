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
        String sql = "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, original_quantity, status, timestamp_micros) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, order.getOrderId());
            pstmt.setLong(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getQuantity());
            pstmt.setDouble(7, order.getOriginalQuantity());
            pstmt.setString(8, order.getStatus());
            pstmt.setLong(9, order.getTimestampMicros());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update the fill status and remaining quantity of an order.
     * Called asynchronously by the OrderPersister worker.
     */
    public static void updateOrderStatus(long orderId, String status, double remainingQty) {
        String sql = "UPDATE orders SET status = ?, quantity = ? WHERE order_id = ?";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setDouble(2, remainingQty);
            pstmt.setLong(3, orderId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating order status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Load all unfilled orders from the database for crash recovery.
     * Orders with status NEW or PARTIALLY_FILLED are re-inserted into the OrderBook.
     */
    public static List<Order> loadUnfilledOrders() {
        List<Order> orders = new ArrayList<>();
        String sql = "SELECT order_id, cl_ord_id, symbol, side, price, quantity, original_quantity, status, timestamp_micros FROM orders WHERE status IN ('NEW', 'PARTIALLY_FILLED')";

        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Order order = new Order(
                    rs.getLong("order_id"),
                    rs.getLong("cl_ord_id"),
                    rs.getString("symbol"),
                    rs.getString("side").charAt(0),
                    rs.getDouble("price"),
                    rs.getDouble("quantity"),
                    rs.getDouble("original_quantity"),
                    rs.getString("status"),
                    rs.getLong("timestamp_micros")
                );
                orders.add(order);
            }
            System.out.println("Loaded " + orders.size() + " unfilled orders for recovery");
        } catch (SQLException e) {
            System.err.println("Error loading unfilled orders: " + e.getMessage());
            e.printStackTrace();
        }

        return orders;
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
     * Insert execution into executions table with buy/sell order IDs and microsecond timestamp
     * @param execution Execution object to persist
     */
    public static void insertExecution(Execution execution) {
        String sql = "INSERT INTO executions (exec_id, buy_order_id, sell_order_id, symbol, side, exec_qty, exec_price, match_time_micros) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, execution.getExecId());
            pstmt.setLong(2, execution.getBuyOrderId());
            pstmt.setLong(3, execution.getSellOrderId());
            pstmt.setString(4, execution.getSymbol());
            pstmt.setString(5, String.valueOf(execution.getSide()));
            pstmt.setInt(6, execution.getExecQty());
            pstmt.setDouble(7, execution.getExecPrice());
            pstmt.setLong(8, execution.getMatchTimeMicros());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting execution: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
