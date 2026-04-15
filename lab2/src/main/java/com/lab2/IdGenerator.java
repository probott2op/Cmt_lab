package com.lab2;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe ID generator using timestamp-based IDs.
 * Format: currentTimeMillis * 10000 + sequence (mod 10000)
 * 
 * This gives ~10,000 unique IDs per millisecond, which is more than
 * sufficient for our matching engine throughput.
 */
public class IdGenerator {
    private static final AtomicLong sequence = new AtomicLong(0);

    public static long next() {
        return System.currentTimeMillis() * 10000L + sequence.getAndIncrement() % 10000;
    }
}
