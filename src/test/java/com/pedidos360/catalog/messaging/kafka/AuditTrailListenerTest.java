package com.pedidos360.catalog.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pedidos360.catalog.domain.AuditedOrderEvent;
import com.pedidos360.catalog.repository.AuditedOrderEventRepository;
import com.pedidos360.catalog.service.AuditTrailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditTrailListenerTest {

    private final AuditedOrderEventRepository repository = mock(AuditedOrderEventRepository.class);
    private final AuditTrailService auditService = new AuditTrailService(repository);
    private final AuditTrailListener listener = new AuditTrailListener(
            new OrderEventParser(new ObjectMapper().findAndRegisterModules()), auditService);

    private static final String VALID_PAYLOAD = """
            {"eventId":"%s","eventType":"ORDER_ACCEPTED","schemaVersion":1,
             "orderId":"%s","status":"ACEPTADO","previousStatus":"CREADO",
             "items":[{"productId":"%s","quantity":2}],"occurredAt":"2026-11-18T20:26:05.336705228Z"}
            """;

    private final UUID eventId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private ConsumerRecord<String, String> recordFor(String payload) {
        return new ConsumerRecord<>("pedidos360.order-events", 1, 7L, orderId.toString(), payload);
    }

    @BeforeEach
    void resetMocks() {
        org.mockito.Mockito.reset(repository);
    }

    @Test
    void onOrderEventPersistsValidEvent() {
        when(repository.existsByEventId(eventId)).thenReturn(false);

        listener.onOrderEvent(recordFor(VALID_PAYLOAD.formatted(eventId, orderId, productId)));

        ArgumentCaptor<AuditedOrderEvent> captor = ArgumentCaptor.forClass(AuditedOrderEvent.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(eventId);
    }

    @Test
    void onOrderEventSkipsDuplicateEventId() {
        when(repository.existsByEventId(eventId)).thenReturn(true);

        listener.onOrderEvent(recordFor(VALID_PAYLOAD.formatted(eventId, orderId, productId)));

        verify(repository, never()).save(any());
    }

    @Test
    void onOrderEventRejectsInvalidPayloadWithoutTouchingTheStore() {
        assertThatThrownBy(() -> listener.onOrderEvent(recordFor("esto no es un evento")))
                .isInstanceOf(InvalidOrderEventException.class);
        verify(repository, never()).existsByEventId(any());
        verify(repository, never()).save(any());
    }
}
