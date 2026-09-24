package com.pedidos360.catalog.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReservationCreateRequest(
        @NotNull UUID orderId,
        @NotEmpty @Size(max = 100) List<@Valid ReservationItemRequest> items) {

    public ReservationCreateRequest {
        items = items == null ? null : List.copyOf(items);
    }
}
