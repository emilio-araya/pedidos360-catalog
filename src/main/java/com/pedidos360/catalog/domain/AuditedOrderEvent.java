package com.pedidos360.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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
 * Rastro de auditoría de eventos de dominio. `event_id` es la clave: una
 * reentrega del consumidor `pedidos360-audit` no duplica registros. El
 * consumidor escribe sólo esta tabla; nunca toca la proyección de ventas.
 */
@Entity
@Table(name = "audited_order_events")
public class AuditedOrderEvent implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private UUID eventId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "order_id", nullable = false, updatable = false, length = 36)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 40)
    private String eventType;

    @Lob
    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private Instant consumedAt;

    // Ver Persistable semantics in ProcessedOrderEvent: prevents merge-based
    // double-writes when the same event is delivered twice concurrently.
    @Transient
    private boolean newEntity = true;

    protected AuditedOrderEvent() {
    }

    public AuditedOrderEvent(UUID eventId, UUID orderId, String eventType, String payload) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.eventType = eventType;
        this.payload = payload;
        this.consumedAt = Instant.now();
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
