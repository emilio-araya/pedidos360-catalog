package com.pedidos360.catalog.messaging;

import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de transporte: recibe el mensaje crudo de RabbitMQ y lo entrega al
 * handler de negocio a través de {@link ResilientConsumer}, que decide ack,
 * reintento o DLQ. El handler no conoce el broker.
 */
@Component
public class MessagingEntryPoints {

    private final ResilientConsumer consumer;
    private final StockReservationListener listener;

    public MessagingEntryPoints(ResilientConsumer consumer, StockReservationListener listener) {
        this.consumer = consumer;
        this.listener = listener;
    }

    @RabbitListener(queues = MessagingTopology.RESERVE_QUEUE)
    public void onOrderAccepted(Message message, Channel channel) throws IOException {
        consumer.consume(message, channel, MessagingTopology.RESERVE_QUEUE,
                ReservationCreateRequest.class, listener::onOrderAccepted);
    }

    @RabbitListener(queues = MessagingTopology.RELEASE_QUEUE)
    public void onOrderCancelled(Message message, Channel channel) throws IOException {
        consumer.consume(message, channel, MessagingTopology.RELEASE_QUEUE,
                OrderCancelledMessage.class, listener::onOrderCancelled);
    }
}
