package com.pedidos360.catalog.messaging.kafka;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de dominio de pedido publicado por `orders` en
 * {@link KafkaTopics#ORDER_EVENTS} (contrato S2, `schemaVersion` 1).
 */
public record OrderDomainEvent(
        UUID eventId,
        EventType eventType,
        Integer schemaVersion,
        UUID orderId,
        OrderStatus status,
        OrderStatus previousStatus,
        List<Item> items,
        Instant occurredAt
) {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public enum EventType {
        ORDER_CREATED,
        ORDER_UPDATED,
        ORDER_ACCEPTED,
        ORDER_IN_PREPARATION,
        ORDER_DISPATCHED,
        ORDER_DELIVERED,
        ORDER_CANCELLED
    }

    public enum OrderStatus {
        CREADO,
        ACEPTADO,
        EN_PREPARACION,
        DESPACHADO,
        ENTREGADO,
        CANCELADO
    }

    public record Item(UUID productId, Integer quantity) {
    }
}
