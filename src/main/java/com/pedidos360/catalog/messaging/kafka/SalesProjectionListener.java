package com.pedidos360.catalog.messaging.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.EventType;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent.Item;
import com.pedidos360.catalog.service.SalesProjectionService;
import java.util.Arrays;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume los eventos de dominio de pedido y alimenta la proyección de ventas.
 * Valida el contrato antes de tocar la base: lo inválido se rechaza con
 * {@link InvalidOrderEventException} (DLT sin reintentos) y cualquier otra
 * excepción se propaga para que {@link KafkaConsumerConfiguration} reintente.
 *
 * El contenedor sólo arranca con `app.kafka.sales-projection.enabled=true`
 * (valor por defecto); la suite lo apaga para no necesitar broker.
 */
@Component
public class SalesProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(SalesProjectionListener.class);

    private final ObjectMapper objectMapper;
    private final SalesProjectionService salesService;

    public SalesProjectionListener(ObjectMapper objectMapper, SalesProjectionService salesService) {
        this.objectMapper = objectMapper;
        this.salesService = salesService;
    }

    @KafkaListener(
            id = "salesProjection",
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = KafkaTopics.SALES_PROJECTION_GROUP,
            autoStartup = "${app.kafka.sales-projection.enabled:true}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        OrderDomainEvent event = parse(record.value());
        boolean applied = salesService.apply(event);
        log.debug("Evento {} {} del pedido {} (partición {}, offset {}): {}",
                event.eventType(), event.eventId(), event.orderId(), record.partition(), record.offset(),
                applied ? "aplicado" : "duplicado, ignorado");
    }

    private OrderDomainEvent parse(String payload) {
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
