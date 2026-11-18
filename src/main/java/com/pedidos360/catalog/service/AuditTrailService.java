package com.pedidos360.catalog.service;

import com.pedidos360.catalog.domain.AuditedOrderEvent;
import com.pedidos360.catalog.messaging.kafka.OrderDomainEvent;
import com.pedidos360.catalog.repository.AuditedOrderEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de auditoría: registra cada evento de dominio recibido por un
 * grupo separado (`pedidos360-audit`). Idempotente por `eventId`: una
 * reentrega no duplica. No participa en la proyección de ventas.
 */
@Service
public class AuditTrailService {

    private final AuditedOrderEventRepository repository;

    public AuditTrailService(AuditedOrderEventRepository repository) {
        this.repository = repository;
    }

    /** @return `true` si se registró; `false` si era un evento ya auditado. */
    @Transactional
    public boolean apply(OrderDomainEvent event, String rawPayload) {
        if (repository.existsByEventId(event.eventId())) {
            return false;
        }
        repository.save(new AuditedOrderEvent(
                event.eventId(), event.orderId(), event.eventType().name(), rawPayload));
        return true;
    }
}
