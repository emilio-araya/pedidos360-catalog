package com.pedidos360.catalog.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.ReservationItemRequest;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Contrato con `orders`: el cuerpo es el JSON que `orders` guarda en su outbox
 * y publica tal cual. Se lee por el mismo camino que en producción
 * ({@link ResilientConsumer}); si este test se rompe, los eventos de pedido
 * dejan de procesarse en el catálogo.
 */
class OrderEventContractTest {

    private static final long TAG = 7L;
    private static final UUID ORDER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final ResilientConsumer consumer =
            new ResilientConsumer(mock(RabbitTemplate.class), new ObjectMapper(), 200);
    private final Channel channel = mock(Channel.class);

    @Test
    void readsTheAcceptedOrderEventAsAReservationRequest() throws IOException {
        List<ReservationCreateRequest> received = new ArrayList<>();

        consumer.consume(
                fromOrders("{\"orderId\":\"" + ORDER_ID + "\",\"items\":[{\"productId\":\""
                        + PRODUCT_ID + "\",\"quantity\":3}]}", MessageProperties.CONTENT_TYPE_JSON),
                channel, MessagingTopology.RESERVE_QUEUE, ReservationCreateRequest.class, received::add);

        assertThat(received).containsExactly(
                new ReservationCreateRequest(ORDER_ID, List.of(new ReservationItemRequest(PRODUCT_ID, 3))));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void readsTheCancelledAndOrphanReleaseEvents() throws IOException {
        List<OrderCancelledMessage> received = new ArrayList<>();

        consumer.consume(
                fromOrders("{\"orderId\":\"" + ORDER_ID + "\"}", MessageProperties.CONTENT_TYPE_JSON),
                channel, MessagingTopology.RELEASE_QUEUE, OrderCancelledMessage.class, received::add);

        assertThat(received).containsExactly(new OrderCancelledMessage(ORDER_ID));
        verify(channel).basicAck(TAG, false);
    }

    @Test
    void sendsAnEventWithoutTheJsonContentTypeToTheDeadLetterQueue() throws IOException {
        // Documenta por qué `orders` debe fijar el content type: como text/plain
        // el pedido no se procesa y el mensaje termina en la DLQ.
        List<OrderCancelledMessage> received = new ArrayList<>();

        consumer.consume(
                fromOrders("{\"orderId\":\"" + ORDER_ID + "\"}", MessageProperties.CONTENT_TYPE_TEXT_PLAIN),
                channel, MessagingTopology.RELEASE_QUEUE, OrderCancelledMessage.class, received::add);

        assertThat(received).isEmpty();
        verify(channel).basicNack(TAG, false, false);
    }

    private Message fromOrders(String json, String contentType) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(contentType);
        properties.setContentEncoding(UTF_8.name());
        properties.setDeliveryTag(TAG);
        return new Message(json.getBytes(UTF_8), properties);
    }
}
