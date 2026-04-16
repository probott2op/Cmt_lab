package com.lab2;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe ID generator using timestamp-based IDs.
 * Format: currentTimeMillis * 10000 + sequence (mod 10000)
 * 
 * This gives ~10,000 unique IDs per millisecond, which is more than
 * sufficient for our matching engine throughput.
 * 
 * Two separate sequences ensure audit event ID generation doesn't
 * shift order/execution IDs (which caused off-by-1 bugs).
 */
public class IdGenerator {
    // Primary sequence for orders and executions (business entities)
    private static final AtomicLong orderSequence = new AtomicLong(0);
    // Separate sequence for audit events (non-business, observational)
    private static final AtomicLong auditSequence = new AtomicLong(0);

    /**
     * Generate next ID for orders and executions.
     */
    public static long next() {
        return System.currentTimeMillis() * 10000L + orderSequence.getAndIncrement() % 10000;
    }

    /**
     * Generate next ID for audit trail events.
     * Uses a separate counter so it never shifts order/execution IDs.
     */
    public static long nextAuditId() {
        return System.currentTimeMillis() * 10000L + 5000L + auditSequence.getAndIncrement() % 5000;
    }
}
