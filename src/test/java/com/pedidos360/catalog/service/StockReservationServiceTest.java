package com.pedidos360.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.ReservationItemRequest;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.domain.ReservationStatus;
import com.pedidos360.catalog.domain.StockReservation;
import com.pedidos360.catalog.exception.ConflictException;
import com.pedidos360.catalog.repository.ProductRepository;
import com.pedidos360.catalog.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StockReservationServiceTest {

    private StockReservationRepository reservationRepository;
    private ProductRepository productRepository;
    private StockReservationService service;
    private Product product;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        reservationRepository = mock(StockReservationRepository.class);
        productRepository = mock(ProductRepository.class);
        service = new StockReservationService(reservationRepository, productRepository);
        product = new Product(
                "TEST-001", "Producto", null, new BigDecimal("10.00"), 5, true);
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        orderId = UUID.randomUUID();
    }

    @Test
    void reservesEachProductOnlyOnce() {
        ReservationCreateRequest request = request(2);
        when(reservationRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(productRepository.findAllByIdInForUpdate(List.of(product.getId())))
                .thenReturn(List.of(product));
        when(reservationRepository.saveAndFlush(any(StockReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StockReservationService.ReservationResult result = service.reserve(request);

        assertThat(result.created()).isTrue();
        assertThat(result.reservation().status()).isEqualTo(ReservationStatus.RESERVED);
        assertThat(product.getStock()).isEqualTo(3);
    }

    @Test
    void replayDoesNotTouchStockAgain() {
        ReservationCreateRequest request = request(2);
        StockReservation existing = new StockReservation(
                orderId, List.of(new StockReservation.ReservationLine(product.getId(), 2)));
        ReflectionTestUtils.setField(existing, "status", ReservationStatus.RESERVED);
        when(reservationRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(existing));

        StockReservationService.ReservationResult result = service.reserve(request);

        assertThat(result.created()).isFalse();
        assertThat(product.getStock()).isEqualTo(5);
        verify(productRepository, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void rejectsReuseOfOrderIdWithDifferentItems() {
        ReservationCreateRequest request = request(3);
        StockReservation existing = new StockReservation(
                orderId, List.of(new StockReservation.ReservationLine(product.getId(), 2)));
        when(reservationRepository.findByOrderIdForUpdate(orderId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.reserve(request))
                .isInstanceOf(ConflictException.class)
                .extracting(exception -> ((ConflictException) exception).getErrorCode())
                .isEqualTo("RESERVATION_IDEMPOTENCY_CONFLICT");
    }

    private ReservationCreateRequest request(int quantity) {
        return new ReservationCreateRequest(
                orderId,
                List.of(new ReservationItemRequest(product.getId(), quantity)));
    }
}
