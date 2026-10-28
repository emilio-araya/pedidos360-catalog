package com.pedidos360.catalog.messaging;

import java.util.UUID;

/**
 * Resultado de una reserva de stock, publicado para `orders`.
 *
 * @param orderId pedido al que pertenece la reserva
 * @param outcome {@link #RESERVED} o {@link #FAILED}
 * @param errorCode código de negocio del rechazo; nulo si se reservó
 * @param reason mensaje legible del rechazo; nulo si se reservó
 */
public record StockResultMessage(UUID orderId, String outcome, String errorCode, String reason) {

    public static final String RESERVED = "RESERVED";
    public static final String FAILED = "FAILED";
}
