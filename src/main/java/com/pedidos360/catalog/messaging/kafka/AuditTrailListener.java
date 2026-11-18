package com.pedidos360.catalog.messaging.kafka;

import com.pedidos360.catalog.service.AuditTrailService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de auditoría del tópico de dominio. Comparten los mensajes con
 * `catalog-sales-projection` pero NO el grupo: `pedidos360-audit` lleva sus
 * propios offsets y escribe sólo la tabla `audited_order_events`.
 *
 * El contenedor sólo arranca con `app.kafka.audit-trail.enabled=true` (valor
 * por defecto); la suite lo apaga para no necesitar broker.
 */
@Component
public class AuditTrailListener {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailListener.class);

    private final OrderEventParser parser;
    private final AuditTrailService auditService;

    public AuditTrailListener(OrderEventParser parser, AuditTrailService auditService) {
        this.parser = parser;
        this.auditService = auditService;
    }

    @KafkaListener(
            id = "auditTrail",
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = KafkaTopics.AUDIT_GROUP,
            autoStartup = "${app.kafka.audit-trail.enabled:true}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        OrderDomainEvent event = parser.parse(record.value());
        boolean audited = auditService.apply(event, record.value());
        log.debug("Auditoría {} {} del pedido {} (partición {}, offset {}): {}",
                event.eventType(), event.eventId(), event.orderId(), record.partition(), record.offset(),
                audited ? "registrada" : "duplicada, ignorada");
    }
}
