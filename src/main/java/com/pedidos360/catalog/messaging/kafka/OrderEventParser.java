package com.pedidos360.catalog.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.EventType;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.Item;
import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Contrato del evento de pedido (`schemaVersion` 1). Fuente única de verdad
 * para los consumidores del tópico: lo inválido se rechaza con
 * {@link InvalidOrderEventException}, que el manejador convierte en DLT sin
 * reintentos.
 */
@Component
public class OrderEventParser {

    private final ObjectMapper objectMapper;

    public OrderEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OrderDomainEvent parse(String payload) {
        if (payload == null) {
            throw new InvalidOrderEventException("Order event without payload");
        }
        JsonNode tree;
        try {
            tree = objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new InvalidOrderEventException("Malformed order event JSON", exception);
        }
        if (tree == null || !tree.isObject()) {
            throw new InvalidOrderEventException("Order event must be a JSON object");
        }
        // Versión y tipo se validan antes de mapear, para que el motivo del
        // rechazo sea explícito en los headers de la DLT.
        JsonNode version = tree.path("schemaVersion");
        if (!version.isIntegralNumber() || version.asInt() != OrderDomainEvent.SUPPORTED_SCHEMA_VERSION) {
            throw new InvalidOrderEventException("Unsupported schemaVersion: " + version);
        }
        String eventType = tree.path("eventType").asText(null);
        if (eventType == null || Arrays.stream(EventType.values()).noneMatch(t -> t.name().equals(eventType))) {
            throw new InvalidOrderEventException("Unknown eventType: " + eventType);
        }
        OrderDomainEvent event;
        try {
            event = objectMapper.treeToValue(tree, OrderDomainEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidOrderEventException("Order event does not match the contract", exception);
        }
        validate(event);
        return event;
    }

    private void validate(OrderDomainEvent event) {
        if (event.eventId() == null) {
            throw new InvalidOrderEventException("Order event without eventId");
        }
        if (event.orderId() == null) {
            throw new InvalidOrderEventException("Order event without orderId");
        }
        if (event.items() == null) {
            throw new InvalidOrderEventException("Order event without items");
        }
        for (Item item : event.items()) {
            if (item == null || item.productId() == null || item.quantity() == null || item.quantity() < 1) {
                throw new InvalidOrderEventException("Order event with an invalid item: " + item);
            }
        }
    }
}
