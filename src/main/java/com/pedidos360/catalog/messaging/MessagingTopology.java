package com.pedidos360.catalog.messaging;

/**
 * Nombres de la topología compartida con `orders`. Son contrato entre servicios:
 * cambiar uno sin cambiar el otro deja mensajes sin destino.
 */
public final class MessagingTopology {

    public static final String EVENTS_EXCHANGE = "pedidos360.events";

    /** Colas que consume el catálogo; las declara él mismo. */
    public static final String RESERVE_QUEUE = "stock.reserve";
    public static final String RELEASE_QUEUE = "stock.release";

    /** Eventos que publica `orders`. */
    public static final String ACCEPTED_ROUTING_KEY = "pedido.aceptado";
    public static final String CANCELLED_ROUTING_KEY = "pedido.cancelado";
    /** Reserva confirmada para un pedido ya cancelado: hay que liberarla. */
    public static final String ORPHAN_ROUTING_KEY = "pedido.reserva-huerfana";

    /** Eventos que publica el catálogo; la cola de destino la declara `orders`. */
    public static final String RESERVED_ROUTING_KEY = "stock.reservado";
    public static final String FAILED_ROUTING_KEY = "stock.reserva-fallida";

    private MessagingTopology() {
    }
}
