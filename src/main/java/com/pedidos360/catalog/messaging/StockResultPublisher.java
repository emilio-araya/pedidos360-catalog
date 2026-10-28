package com.pedidos360.catalog.messaging;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publica el resultado de la reserva y espera la confirmación del broker.
 *
 * Si no hay confirmación, o el mensaje no tiene cola destino, lanza una
 * excepción: el listener la propaga, el mensaje original se reentrega y la
 * reserva, que es idempotente por `orderId`, se vuelve a informar.
 */
@Component
public class StockResultPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final long confirmTimeoutMs;

    public StockResultPublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${app.messaging.confirm-timeout-ms:5000}") long confirmTimeoutMs
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    public void publishReserved(UUID orderId) {
        publish(MessagingTopology.RESERVED_ROUTING_KEY,
                new StockResultMessage(orderId, StockResultMessage.RESERVED, null, null));
    }

    public void publishFailure(UUID orderId, String errorCode, String reason) {
        publish(MessagingTopology.FAILED_ROUTING_KEY,
                new StockResultMessage(orderId, StockResultMessage.FAILED, errorCode, reason));
    }

    private void publish(String routingKey, StockResultMessage message) {
        CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
        rabbitTemplate.convertAndSend(MessagingTopology.EVENTS_EXCHANGE, routingKey, message, correlation);

        CorrelationData.Confirm confirm = awaitConfirm(correlation, routingKey);
        if (!confirm.isAck()) {
            throw new IllegalStateException(
                    "El broker rechazó " + routingKey + " del pedido " + message.orderId()
                            + ": " + confirm.getReason());
        }
        if (correlation.getReturned() != null) {
            throw new IllegalStateException(
                    "El evento " + routingKey + " no tiene cola destino: "
                            + correlation.getReturned().getReplyText());
        }
    }

    private CorrelationData.Confirm awaitConfirm(CorrelationData correlation, String routingKey) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture()
                    .get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
            if (confirm == null) {
                throw new IllegalStateException("El broker no confirmó " + routingKey);
            }
            return confirm;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando la confirmación de " + routingKey, exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException(
                    "Sin confirmación de " + routingKey + " en " + confirmTimeoutMs + " ms", exception);
        }
    }
}
