package com.pedidos360.catalog.api.dto;

import com.pedidos360.catalog.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservationResponse(
        UUID orderId,
        ReservationStatus status,
        List<ReservationItemResponse> items,
        Instant reservedAt,
        Instant releasedAt) {

    public StockReservationResponse {
        items = List.copyOf(items);
    }
}
