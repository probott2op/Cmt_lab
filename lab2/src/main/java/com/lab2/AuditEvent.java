package com.lab2;

import java.time.Instant;

/**
 * Audit trail event for order lifecycle tracking.
 * Queued to OrderPersister for async DB writes.
 * 
 * Event types:
 *   ORDER_NEW, ORDER_ACK, ORDER_REJECTED,
 *   ORDER_PARTIAL_FILL, ORDER_FILLED,
 *   CANCEL_REQUESTED, CANCEL_ACCEPTED, CANCEL_REJECTED
 */
public class AuditEvent {
    private final long eventId;
    private final long orderId;
    private final String eventType;
    private final String fromStatus;
    private final String toStatus;
    private final String detail;
    private final long timestampMicros;

    public AuditEvent(long eventId, long orderId, String eventType,
                      String fromStatus, String toStatus, String detail) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.detail = detail;
        Instant now = Instant.now();
        this.timestampMicros = now.getEpochSecond() * 1_000_000L + now.getNano() / 1_000L;
    }

    // Constructor for loading from DB
    public AuditEvent(long eventId, long orderId, String eventType,
                      String fromStatus, String toStatus, String detail, long timestampMicros) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.detail = detail;
        this.timestampMicros = timestampMicros;
    }

    public long getEventId() { return eventId; }
    public long getOrderId() { return orderId; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getDetail() { return detail; }
    public long getTimestampMicros() { return timestampMicros; }

    @Override
    public String toString() {
        return String.format("AuditEvent[eventId=%d, orderId=%d, type=%s, %s→%s, detail=%s]",
                eventId, orderId, eventType, fromStatus, toStatus, detail);
    }
}
