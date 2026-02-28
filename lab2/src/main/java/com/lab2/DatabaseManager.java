package com.lab2;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
public class DatabaseManager {
    private static final String URL = "jdbc:mysql://localhost:3306/trading_system";
    private static final String USER = "root"; // Use environment variables for credentials
    private static final String PASS = "root@fintech"; // Use env variables in production!
    public static void insertOrder(Order order) {
    String sql = "INSERT INTO orders (cl_ord_id, symbol, side, price, quantity, status) VALUES (?, ?, ?, ?, ?, ?)";
    try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
    PreparedStatement pstmt = conn.prepareStatement(sql)) {
    pstmt.setString(1, order.getClOrdID());
    pstmt.setString(2, order.getSymbol());
    pstmt.setString(3, String.valueOf(order.getSide()));
    pstmt.setDouble(4, order.getPrice());
    pstmt.setDouble(5, order.getQuantity());
    pstmt.setString(6, "NEW");
    pstmt.executeUpdate();
    } catch (SQLException e) {
    e.printStackTrace();
    }
    }
}
