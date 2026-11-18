package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.domain.AuditedOrderEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Rastreo de auditoría; `event_id` es su clave natural. */
public interface AuditedOrderEventRepository extends JpaRepository<AuditedOrderEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
