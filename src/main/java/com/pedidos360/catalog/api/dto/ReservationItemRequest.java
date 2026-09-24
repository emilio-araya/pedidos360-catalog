package com.pedidos360.catalog.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReservationItemRequest(
        @NotNull UUID productId,
        @Min(1) @Max(1_000_000) int quantity) {
}
