package com.pedidos360.catalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * El resultado de la reserva sólo se da por enviado con la confirmación del
 * broker y una cola destino. Si no, se lanza una excepción para que el mensaje
 * original se reentregue y la reserva idempotente se vuelva a informar.
 */
class StockResultPublisherTest {

    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private RabbitTemplate rabbitTemplate;
    private StockResultPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new StockResultPublisher(rabbitTemplate, 200);
    }

    @Test
    void publishesTheReservationConfirmation() {
        answerWith(true, false, MessagingTopology.RESERVED_ROUTING_KEY);

        publisher.publishReserved(ORDER_ID);

        assertThat(sent(MessagingTopology.RESERVED_ROUTING_KEY))
                .isEqualTo(new StockResultMessage(ORDER_ID, StockResultMessage.RESERVED, null, null));
    }

    @Test
    void publishesTheFailureWithItsCodeAndReason() {
        answerWith(true, false, MessagingTopology.FAILED_ROUTING_KEY);

        publisher.publishFailure(ORDER_ID, "INSUFFICIENT_STOCK", "Stock insuficiente");

        assertThat(sent(MessagingTopology.FAILED_ROUTING_KEY))
                .isEqualTo(new StockResultMessage(
                        ORDER_ID, StockResultMessage.FAILED, "INSUFFICIENT_STOCK", "Stock insuficiente"));
    }

    @Test
    void failsWhenTheBrokerNacks() {
        answerWith(false, false, MessagingTopology.RESERVED_ROUTING_KEY);

        assertThatThrownBy(() -> publisher.publishReserved(ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenTheMessageHasNoDestinationQueue() {
        answerWith(true, true, MessagingTopology.RESERVED_ROUTING_KEY);

        assertThatThrownBy(() -> publisher.publishReserved(ORDER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void failsWhenTheBrokerDoesNotConfirmInTime() {
        // Sin respuesta: el futuro de la confirmación nunca se completa.
        assertThatThrownBy(() -> publisher.publishReserved(ORDER_ID))
                .isInstanceOf(IllegalStateException.class);
    }

    private void answerWith(boolean ack, boolean returned, String routingKey) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            if (returned) {
                correlation.setReturned(new ReturnedMessage(
                        new Message(new byte[0]), 312, "NO_ROUTE",
                        MessagingTopology.EVENTS_EXCHANGE, routingKey));
            }
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(MessagingTopology.EVENTS_EXCHANGE),
                eq(routingKey),
                any(StockResultMessage.class),
                any(CorrelationData.class));
    }

    private StockResultMessage sent(String routingKey) {
        ArgumentCaptor<StockResultMessage> message = ArgumentCaptor.forClass(StockResultMessage.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MessagingTopology.EVENTS_EXCHANGE),
                eq(routingKey),
                message.capture(),
                any(CorrelationData.class));
        return message.getValue();
    }
}
