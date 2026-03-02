package com.lab2;

import java.util.*;
import java.util.concurrent.*;

public class OrderBook {
    // Bids: High Price first (Descending Order).
    // Key: Price, Value: List of Orders at that price (Time Priority)
    private NavigableMap<Double, List<Order>> bids = new ConcurrentSkipListMap<>(Collections.reverseOrder());
    
    // Asks: Low Price first (Ascending Order).
    private NavigableMap<Double, List<Order>> asks = new ConcurrentSkipListMap<>();

    // synchronized ensures only one thread can modify the book for this symbol at a time
    public synchronized List<Execution> match(Order incoming) {
        List<Execution> executions = new ArrayList<>();
        
        if (incoming.getSide() == '1') { // Buy Order
            // Attempt to match against the Asks (Sellers)
            matchOrder(incoming, asks, executions);
        } else { // Sell Order
            // Attempt to match against the Bids (Buyers)
            matchOrder(incoming, bids, executions);
        }
        
        // If the order is not fully filled after matching, add the remainder to the book
        if (incoming.getQuantity() > 0) {
            addToBook(incoming);
        }
        
        return executions;
    }
    
    // Helper to add resting orders to the correct map
    private void addToBook(Order order) {
        NavigableMap<Double, List<Order>> side = (order.getSide() == '1') ? bids : asks;
        // ComputeIfAbsent creates the list if this is the first order at this price
        side.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
    }

    private void matchOrder(Order incoming, NavigableMap<Double, List<Order>> oppositeSide, List<Execution> trades) {
        // Continue loop while:
        // 1. The incoming order still needs to be filled (Qty > 0)
        // 2. The opposite book is not empty (There is someone to trade with)
        while (incoming.getQuantity() > 0 && !oppositeSide.isEmpty()) {
            // Peek at the best available price on the other side
            Double bestPrice = oppositeSide.firstKey();
            
            // Check Price Logic: Does the limit price allow this trade?
            boolean isBuy = (incoming.getSide() == '1');
            
            // If Buying: We want to buy Low. If BestAsk > MyLimit, I can't afford it. Stop.
            if (isBuy && incoming.getPrice() < bestPrice) break;
            
            // If Selling: We want to sell High. If BestBid < MyLimit, they aren't paying enough. Stop.
            if (!isBuy && incoming.getPrice() > bestPrice) break;
            
            // If we are here, a match is possible!
            // Get the list of orders at this price level
            List<Order> ordersAtLevel = oppositeSide.get(bestPrice);
            
            // Match against the first order in the list (Time Priority / FIFO)
            Order resting = ordersAtLevel.get(0);
            
            // Calculate Trade Quantity: The max we can trade is the smaller of the two sizes
            double tradeQty = Math.min(incoming.getQuantity(), resting.getQuantity());
            
            // Create Execution Record.
            // CRITICAL: The trade happens at the RESTING order's price, not the aggressor's price.
            String execId = java.util.UUID.randomUUID().toString();
            Execution exec = new Execution(execId, incoming.getOrderId(), incoming.getSymbol(), incoming.getSide(), (int)tradeQty, bestPrice);
            trades.add(exec);
            
            // Update Order Objects (Decrement Qty) in memory
            incoming.reduceQty(tradeQty);
            resting.reduceQty(tradeQty);
            
            // Cleanup: Remove filled orders from the book to keep the map clean
            if (resting.getQuantity() == 0) {
                ordersAtLevel.remove(0); // Remove the filled order from the list
                // If that was the last order at that price, remove the price level entirely
                if (ordersAtLevel.isEmpty()) {
                    oppositeSide.remove(bestPrice);
                }
            }
        }
    }
}
