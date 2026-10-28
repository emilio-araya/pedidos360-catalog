package com.pedidos360.catalog.messaging;

import java.util.UUID;

/** Cuerpo del evento `pedido.cancelado` que publica `orders`. */
public record OrderCancelledMessage(UUID orderId) {
}
