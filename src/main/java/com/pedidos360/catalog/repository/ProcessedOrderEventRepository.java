package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.domain.ProcessedOrderEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEvent, UUID> {
}
