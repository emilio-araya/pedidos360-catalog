package com.pedidos360.catalog.repository;

import com.pedidos360.catalog.domain.ReservationStatus;
import com.pedidos360.catalog.domain.StockReservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from StockReservation r where r.orderId = :orderId")
    Optional<StockReservation> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    long countByStatusAndItemsProductId(ReservationStatus status, UUID productId);
}
