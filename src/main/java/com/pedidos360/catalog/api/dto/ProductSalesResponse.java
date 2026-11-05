package com.pedidos360.catalog.api.dto;

import java.util.UUID;

/** Estadística de ventas de un producto; `netUnits = unitsSold - unitsCancelled`. */
public record ProductSalesResponse(
        UUID productId,
        long unitsSold,
        long unitsDelivered,
        long unitsCancelled,
        long netUnits
) {
}
