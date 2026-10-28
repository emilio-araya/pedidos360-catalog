package com.pedidos360.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.ReservationItemRequest;
import com.pedidos360.catalog.exception.ConflictException;
import com.pedidos360.catalog.service.StockReservationService;
import com.pedidos360.catalog.service.StockReservationService.ReservationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * Un fallo de negocio se informa con un evento explícito y el mensaje se da por
 * procesado. Un fallo transitorio se propaga para que el broker lo reentregue.
 * Ninguno de los dos se descarta en silencio.
 */
class StockReservationListenerTest {

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private StockReservationService reservationService;
    private StockResultPublisher resultPublisher;
    private StockReservationListener listener;

    @BeforeEach
    void setUp() {
        reservationService = mock(StockReservationService.class);
        resultPublisher = mock(StockResultPublisher.class);
        listener = new StockReservationListener(reservationService, resultPublisher);
    }

    @Test
    void reservesTheStockAndPublishesTheConfirmation() {
        ReservationCreateRequest request = request();
        when(reservationService.reserve(request)).thenReturn(new ReservationResult(null, true));

        listener.onOrderAccepted(request);

        verify(reservationService).reserve(request);
        verify(resultPublisher).publishReserved(ORDER_ID);
        verify(resultPublisher, never()).publishFailure(any(), any(), any());
    }

    @Test
    void publishesAnExplicitFailureWhenTheReservationIsRejected() {
        ReservationCreateRequest request = request();
        when(reservationService.reserve(request))
                .thenThrow(new ConflictException("INSUFFICIENT_STOCK", "Stock insuficiente"));

        listener.onOrderAccepted(request);

        verify(resultPublisher).publishFailure(ORDER_ID, "INSUFFICIENT_STOCK", "Stock insuficiente");
        verify(resultPublisher, never()).publishReserved(any());
    }

    @Test
    void propagatesATransientFailureSoTheBrokerRedeliversTheMessage() {
        ReservationCreateRequest request = request();
        when(reservationService.reserve(request))
                .thenThrow(new DataAccessResourceFailureException("base de datos caída"));

        assertThatThrownBy(() -> listener.onOrderAccepted(request))
                .isInstanceOf(DataAccessResourceFailureException.class);

        verifyNoInteractions(resultPublisher);
    }

    @Test
    void propagatesAFailedConfirmationSoTheReservationIsRepublished() {
        ReservationCreateRequest request = request();
        when(reservationService.reserve(request)).thenReturn(new ReservationResult(null, true));
        doThrow(new IllegalStateException("sin confirmación del broker"))
                .when(resultPublisher).publishReserved(ORDER_ID);

        assertThatThrownBy(() -> listener.onOrderAccepted(request))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releasesTheReservationWhenTheOrderIsCancelled() {
        listener.onOrderCancelled(new OrderCancelledMessage(ORDER_ID));

        verify(reservationService).release(ORDER_ID);
        verifyNoInteractions(resultPublisher);
    }

    @Test
    void treatsAnAcceptedEventWithoutOrderIdAsPoison() {
        ReservationCreateRequest withoutOrder =
                new ReservationCreateRequest(null, List.of(new ReservationItemRequest(PRODUCT_ID, 2)));

        assertThatThrownBy(() -> listener.onOrderAccepted(withoutOrder))
                .isInstanceOf(PoisonMessageException.class);
        verifyNoInteractions(reservationService, resultPublisher);
    }

    @Test
    void treatsACancelledEventWithoutOrderIdAsPoison() {
        assertThatThrownBy(() -> listener.onOrderCancelled(new OrderCancelledMessage(null)))
                .isInstanceOf(PoisonMessageException.class);
        verifyNoInteractions(reservationService);
    }

    private ReservationCreateRequest request() {
        return new ReservationCreateRequest(ORDER_ID, List.of(new ReservationItemRequest(PRODUCT_ID, 2)));
    }
}
