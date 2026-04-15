package com.lab2;

import java.util.List;

/**
 * Result of matching an incoming order against the order book.
 * Contains the list of executions (trades) and the list of
 * resting orders whose status changed (needed to broadcast
 * ORDER_UPDATE events to the UI and persist via the async worker).
 */
public class MatchResult {
    private final List<Execution> executions;
    private final List<Order> affectedRestingOrders;

    public MatchResult(List<Execution> executions, List<Order> affectedRestingOrders) {
        this.executions = executions;
        this.affectedRestingOrders = affectedRestingOrders;
    }

    public List<Execution> getExecutions() {
        return executions;
    }

    public List<Order> getAffectedRestingOrders() {
        return affectedRestingOrders;
    }

    public boolean hasTrades() {
        return !executions.isEmpty();
    }
}
