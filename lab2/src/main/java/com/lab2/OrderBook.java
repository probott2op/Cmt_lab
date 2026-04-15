package com.lab2;

import java.util.*;

/**
 * Price-Time Priority Order Book using a TreeMap (Red-Black Tree) for price levels
 * and LinkedList (Queue/FIFO) for time priority within each level.
 * 
 * Bids: TreeMap sorted descending (highest price first) — buyers want to pay the most.
 * Asks: TreeMap sorted ascending  (lowest price first) — sellers want the least.
 * 
 * Insert: O(log N) into the tree + O(1) queue append.
 * Match:  O(log N) to find best price + O(1) queue poll.
 */
public class OrderBook {
    // Bids: Highest price first (Descending)
    private final TreeMap<Double, Queue<Order>> bids = new TreeMap<>(Collections.reverseOrder());

    // Asks: Lowest price first (Ascending / Natural order)
    private final TreeMap<Double, Queue<Order>> asks = new TreeMap<>();

    /**
     * Match an incoming order against the opposite side.
     * Returns a MatchResult containing executions AND affected resting orders.
     * Any unfilled remainder is added to the book as a resting order.
     */
    public synchronized MatchResult match(Order incoming) {
        List<Execution> executions = new ArrayList<>();
        // Use LinkedHashSet for deduplication while preserving insertion order
        Set<Order> affectedRestingSet = new LinkedHashSet<>();

        if (incoming.getSide() == '1') { // Buy Order → match against Asks
            matchOrder(incoming, asks, executions, affectedRestingSet);
        } else { // Sell Order → match against Bids
            matchOrder(incoming, bids, executions, affectedRestingSet);
        }

        // If the order is not fully filled, add the remainder to the book
        if (incoming.getQuantity() > 0) {
            addToBook(incoming);
        }

        return new MatchResult(executions, new ArrayList<>(affectedRestingSet));
    }

    /**
     * Add a resting order directly to the book.
     * Used for crash recovery: re-inserting unfilled orders loaded from the database.
     */
    public synchronized void addOrder(Order order) {
        addToBook(order);
    }

    /**
     * Insert an order into the correct side of the book.
     * computeIfAbsent creates the queue if this is the first order at this price level.
     */
    private void addToBook(Order order) {
        TreeMap<Double, Queue<Order>> side = (order.getSide() == '1') ? bids : asks;
        side.computeIfAbsent(order.getPrice(), k -> new LinkedList<>()).add(order);
    }

    /**
     * Core matching loop.
     * Continuously matches the incoming order against the best price on the opposite side
     * until either the order is fully filled or no more matchable prices exist.
     * Tracks affected resting orders so their status updates can be broadcast + persisted.
     */
    private void matchOrder(Order incoming, TreeMap<Double, Queue<Order>> oppositeSide,
                            List<Execution> trades, Set<Order> affectedResting) {
        while (incoming.getQuantity() > 0 && !oppositeSide.isEmpty()) {
            // Peek at the best available price on the other side — O(log N)
            Map.Entry<Double, Queue<Order>> bestEntry = oppositeSide.firstEntry();
            double bestPrice = bestEntry.getKey();
            Queue<Order> queue = bestEntry.getValue();

            boolean isBuy = (incoming.getSide() == '1');

            // Price check: can this trade happen?
            if (isBuy && incoming.getPrice() < bestPrice) break;
            if (!isBuy && incoming.getPrice() > bestPrice) break;

            // Match against the front of the queue (Time Priority / FIFO) — O(1)
            Order resting = queue.peek();

            // Calculate trade quantity: min of the two remaining quantities
            double tradeQty = Math.min(incoming.getQuantity(), resting.getQuantity());

            // Determine buy and sell order IDs for the Execution record
            long buyOrderId = isBuy ? incoming.getOrderId() : resting.getOrderId();
            long sellOrderId = isBuy ? resting.getOrderId() : incoming.getOrderId();

            // Create Execution at the RESTING order's price (price improvement for aggressor)
            long execId = IdGenerator.next();
            Execution exec = new Execution(execId, buyOrderId, sellOrderId,
                    incoming.getSymbol(), incoming.getSide(), (int) tradeQty, bestPrice);
            trades.add(exec);

            // Decrement quantities (this also updates status via reduceQty)
            incoming.reduceQty(tradeQty);
            resting.reduceQty(tradeQty);

            // Track affected resting order (LinkedHashSet deduplicates)
            affectedResting.add(resting);

            // Cleanup: remove filled resting order from the queue
            if (resting.getQuantity() == 0) {
                queue.poll(); // Remove the filled order — O(1)
                // If queue is empty, remove the entire price level from the tree — O(log N)
                if (queue.isEmpty()) {
                    oppositeSide.pollFirstEntry();
                }
            }
        }
    }
}
