package com.pedidos360.catalog.api.dto;

import java.util.UUID;

public record ReservationItemResponse(UUID productId, int quantity) {
}
