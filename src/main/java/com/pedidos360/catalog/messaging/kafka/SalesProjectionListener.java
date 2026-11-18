package com.pedidos360.catalog.messaging.kafka;

import com.pedidos360.catalog.service.SalesProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consume los eventos de dominio de pedido y alimenta la proyección de ventas.
 * El contrato lo valida {@link OrderEventParser}: lo inválido se rechaza con
 * {@link InvalidOrderEventException} (DLT sin reintentos) y cualquier otra
 * excepción se propaga para que {@link KafkaConsumerConfiguration} reintente.
 *
 * El contenedor sólo arranca con `app.kafka.sales-projection.enabled=true`
 * (valor por defecto); la suite lo apaga para no necesitar broker.
 */
@Component
public class SalesProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(SalesProjectionListener.class);

    private final OrderEventParser parser;
    private final SalesProjectionService salesService;

    public SalesProjectionListener(OrderEventParser parser, SalesProjectionService salesService) {
        this.parser = parser;
        this.salesService = salesService;
    }

    @KafkaListener(
            id = "salesProjection",
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = KafkaTopics.SALES_PROJECTION_GROUP,
            autoStartup = "${app.kafka.sales-projection.enabled:true}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        OrderDomainEvent event = parser.parse(record.value());
        boolean applied = salesService.apply(event);
        log.debug("Evento {} {} del pedido {} (partición {}, offset {}): {}",
                event.eventType(), event.eventId(), event.orderId(), record.partition(), record.offset(),
                applied ? "aplicado" : "duplicado, ignorado");
    }
}
