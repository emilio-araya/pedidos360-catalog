package com.pedidos360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Registro de idempotencia del consumidor de Kafka: un `eventId` presente acá
 * ya se aplicó a la proyección. Se guarda en la misma transacción que los
 * contadores, así una reentrega nunca suma dos veces.
 */
@Entity
@Table(name = "processed_order_events")
public class ProcessedOrderEvent implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, updatable = false, length = 36)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    // El id es asignado: sin esta marca Spring Data haría merge (SELECT + INSERT)
    // y una carrera entre dos entregas no fallaría por clave primaria.
    @Transient
    private boolean newEntity = true;

    protected ProcessedOrderEvent() {
    }

    public ProcessedOrderEvent(UUID eventId, UUID orderId, String eventType) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }
}
