package com.pedidos360.catalog.messaging.kafka;

import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.event;
import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pedidos360.catalog.api.dto.ProductSalesResponse;
import com.pedidos360.catalog.exception.ResourceNotFoundException;
import com.pedidos360.catalog.service.SalesProjectionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Reglas de la proyección de ventas (S4) contra la base H2 real: el evento entra
 * por el mismo método que invoca el contenedor de Kafka y se verifica por la
 * consulta pública del servicio. Cada test usa productos nuevos para no
 * depender del orden de ejecución.
 */
@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes"
})
class SalesProjectionIntegrationTest {

    @Autowired
    private SalesProjectionListener listener;

    @Autowired
    private SalesProjectionService salesService;

    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
    }

    @Test
    void acceptedOrderIncrementsUnitsSold() {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 3)));
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 2)));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 5, 0, 0, 5));
    }

    @Test
    void deliveredOrderIncrementsUnitsDelivered() {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 4)));
        listener.onOrderEvent(record(event("ORDER_DELIVERED", "ENTREGADO", "DESPACHADO", productId, 4)));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 4, 4, 0, 4));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACEPTADO", "EN_PREPARACION", "DESPACHADO"})
    void cancellationAfterAcceptanceIncrementsUnitsCancelled(String previousStatus) {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 3)));
        listener.onOrderEvent(record(event("ORDER_CANCELLED", "CANCELADO", previousStatus, productId, 3)));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 3, 0, 3, 0));
    }

    @Test
    void cancellationFromCreatedChangesNothing() {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 2)));
        listener.onOrderEvent(record(event("ORDER_CANCELLED", "CANCELADO", "CREADO", productId, 7)));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 2, 0, 0, 2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER_CREATED", "ORDER_UPDATED", "ORDER_IN_PREPARATION", "ORDER_DISPATCHED"})
    void otherEventTypesChangeNoCounter(String eventType) {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 2)));
        listener.onOrderEvent(record(event(eventType, "ACEPTADO", "ACEPTADO", productId, 9)));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 2, 0, 0, 2));
    }

    @Test
    void otherEventTypesAloneCreateNoStats() {
        listener.onOrderEvent(record(event("ORDER_CREATED", "CREADO", null, productId, 9)));

        assertThatThrownBy(() -> salesService.findByProductId(productId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void redeliveredEventIdIsAppliedOnlyOnce() {
        String accepted = event(UUID.randomUUID(), "ORDER_ACCEPTED", 1, UUID.randomUUID(),
                "ACEPTADO", "CREADO", productId, 3);

        listener.onOrderEvent(record(accepted));
        listener.onOrderEvent(record(accepted));

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 3, 0, 0, 3));
    }

    @Test
    void rejectedEventLeavesTheProjectionUnchanged() {
        listener.onOrderEvent(record(event("ORDER_ACCEPTED", "ACEPTADO", "CREADO", productId, 1)));

        String unsupported = event(UUID.randomUUID(), "ORDER_ACCEPTED", 2, UUID.randomUUID(),
                "ACEPTADO", "CREADO", productId, 5);
        assertThatThrownBy(() -> listener.onOrderEvent(record(unsupported)))
                .isInstanceOf(InvalidOrderEventException.class);

        assertThat(salesService.findByProductId(productId))
                .isEqualTo(new ProductSalesResponse(productId, 1, 0, 0, 1));
    }
}
