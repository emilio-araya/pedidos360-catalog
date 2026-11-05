package com.pedidos360.catalog.messaging.kafka;

import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.event;
import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.record;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.pedidos360.catalog.service.SalesProjectionService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Eventos que nunca podrán aplicarse: se rechazan sin tocar la proyección. */
class SalesProjectionListenerTest {

    private final SalesProjectionService salesService = mock(SalesProjectionService.class);
    private final SalesProjectionListener listener =
            new SalesProjectionListener(JsonMapper.builder().findAndAddModules().build(), salesService);

    @Test
    void malformedJsonIsRejectedAsInvalid() {
        assertThatThrownBy(() -> listener.onOrderEvent(record("{\"eventId\": not-json")))
                .isInstanceOf(InvalidOrderEventException.class);
        verifyNoInteractions(salesService);
    }

    @Test
    void unknownEventTypeIsRejectedAsInvalid() {
        String payload = event(UUID.randomUUID(), "ORDER_REFUNDED", 1, UUID.randomUUID(),
                "CANCELADO", "ENTREGADO", UUID.randomUUID(), 1);

        assertThatThrownBy(() -> listener.onOrderEvent(record(payload)))
                .isInstanceOf(InvalidOrderEventException.class)
                .hasMessageContaining("eventType");
        verifyNoInteractions(salesService);
    }

    @Test
    void unsupportedSchemaVersionIsRejectedAsInvalid() {
        String payload = event(UUID.randomUUID(), "ORDER_ACCEPTED", 2, UUID.randomUUID(),
                "ACEPTADO", "CREADO", UUID.randomUUID(), 1);

        assertThatThrownBy(() -> listener.onOrderEvent(record(payload)))
                .isInstanceOf(InvalidOrderEventException.class)
                .hasMessageContaining("schemaVersion");
        verifyNoInteractions(salesService);
    }

    @Test
    void eventWithoutEventIdIsRejectedAsInvalid() {
        String payload = event(UUID.randomUUID(), "ORDER_ACCEPTED", 1, UUID.randomUUID(),
                "ACEPTADO", "CREADO", UUID.randomUUID(), 1)
                .replaceFirst("\"eventId\":\"[^\"]+\",", "");

        assertThatThrownBy(() -> listener.onOrderEvent(record(payload)))
                .isInstanceOf(InvalidOrderEventException.class)
                .hasMessageContaining("eventId");
        verifyNoInteractions(salesService);
    }
}
