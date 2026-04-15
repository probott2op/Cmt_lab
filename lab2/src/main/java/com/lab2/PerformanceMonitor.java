package com.lab2;

import java.util.concurrent.atomic.AtomicLong;
public class PerformanceMonitor {

        private static AtomicLong totalLatency = new AtomicLong(0);
        private static AtomicLong count = new AtomicLong(0);
        public static void recordLatency(long nanos) {
        totalLatency.addAndGet(nanos);
        long currentCount = count.incrementAndGet();
        if (currentCount % 1000 == 0) { // Log every 1000 orders
            double avgMicros = (totalLatency.get() / currentCount) / 1000.0;
            System.out.printf("Processed %d orders. Avg Latency: %.2f us%n", currentCount, avgMicros);
        }
    }
}
