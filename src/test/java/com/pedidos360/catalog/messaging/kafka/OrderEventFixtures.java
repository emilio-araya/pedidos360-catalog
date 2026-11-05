package com.pedidos360.catalog.messaging.kafka;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/** Construye eventos de pedido con el contrato JSON que publica `orders` (S2). */
final class OrderEventFixtures {

    private OrderEventFixtures() {
    }

    static String event(
            UUID eventId,
            String eventType,
            int schemaVersion,
            UUID orderId,
            String status,
            String previousStatus,
            UUID productId,
            int quantity
    ) {
        String previous = previousStatus == null ? "null" : "\"" + previousStatus + "\"";
        return "{\"eventId\":\"" + eventId + "\","
                + "\"eventType\":\"" + eventType + "\","
                + "\"schemaVersion\":" + schemaVersion + ","
                + "\"orderId\":\"" + orderId + "\","
                + "\"status\":\"" + status + "\","
                + "\"previousStatus\":" + previous + ","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}],"
                + "\"occurredAt\":\"2026-11-05T12:00:00Z\"}";
    }

    static String event(String eventType, String status, String previousStatus, UUID productId, int quantity) {
        return event(UUID.randomUUID(), eventType, 1, UUID.randomUUID(), status, previousStatus, productId, quantity);
    }

    static ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>(KafkaTopics.ORDER_EVENTS, 0, 0L, "order-key", payload);
    }
}
