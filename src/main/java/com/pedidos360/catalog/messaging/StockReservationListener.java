package com.pedidos360.catalog.messaging;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.exception.CatalogException;
import com.pedidos360.catalog.service.StockReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler de negocio de los eventos de pedido: opera la reserva de stock. No
 * conoce el broker; el ack, los reintentos y la DLQ los decide
 * {@link ResilientConsumer} desde {@link MessagingEntryPoints}.
 *
 * Reutiliza {@link StockReservationService}, que ya es idempotente: una
 * reentrega del mismo `orderId` devuelve la reserva existente sin volver a
 * descontar stock, y liberar una reserva inexistente no hace nada.
 */
@Component
public class StockReservationListener {

    private static final Logger log = LoggerFactory.getLogger(StockReservationListener.class);

    private final StockReservationService reservationService;
    private final StockResultPublisher resultPublisher;

    public StockReservationListener(
            StockReservationService reservationService,
            StockResultPublisher resultPublisher
    ) {
        this.reservationService = reservationService;
        this.resultPublisher = resultPublisher;
    }

    /**
     * Un rechazo de negocio (stock insuficiente, producto inexistente o
     * inactivo) es una respuesta, no un error: se informa con un evento y el
     * mensaje se da por procesado. Cualquier otra excepción se propaga para que
     * el broker reentregue el mensaje.
     */
    public void onOrderAccepted(ReservationCreateRequest request) {
        if (request.orderId() == null) {
            throw new PoisonMessageException("Evento de pedido aceptado sin orderId");
        }
        try {
            reservationService.reserve(request);
        } catch (CatalogException rejection) {
            log.warn("Reserva rechazada para el pedido {}: {} ({})",
                    request.orderId(), rejection.getMessage(), rejection.getErrorCode());
            resultPublisher.publishFailure(request.orderId(), rejection.getErrorCode(), rejection.getMessage());
            return;
        }
        // Fuera del try: si la confirmación falla, la excepción reentrega el mensaje.
        resultPublisher.publishReserved(request.orderId());
    }

    /** Atiende `pedido.cancelado` y `pedido.reserva-huerfana`: ambos piden liberar. */
    public void onOrderCancelled(OrderCancelledMessage message) {
        if (message.orderId() == null) {
            throw new PoisonMessageException("Evento de liberación sin orderId");
        }
        reservationService.release(message.orderId());
    }
}
