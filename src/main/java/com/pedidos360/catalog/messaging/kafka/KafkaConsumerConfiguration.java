package com.pedidos360.catalog.messaging.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;

/**
 * Manejo de errores del consumidor de eventos de pedido. Spring Boot aplica
 * este {@link DefaultErrorHandler} a la fábrica de contenedores por defecto:
 * <ul>
 *   <li>{@link InvalidOrderEventException}: directo a la DLT, sin reintentos.</li>
 *   <li>Cualquier otro fallo (base caída, conflicto de versión): reintentos con
 *       backoff exponencial y, agotados, a la DLT.</li>
 * </ul>
 * El offset se confirma recién cuando el evento se aplicó o quedó en la DLT.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfiguration {

    @Bean
    public DefaultErrorHandler salesProjectionErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.sales-projection.retry.max-retries:4}") int maxRetries,
            @Value("${app.kafka.sales-projection.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${app.kafka.sales-projection.retry.max-interval-ms:10000}") long maxIntervalMs
    ) {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialIntervalMs);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(maxIntervalMs);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate, KafkaConsumerConfiguration::deadLetterDestination);
        return salesProjectionErrorHandler(recoverer, backOff);
    }

    static DefaultErrorHandler salesProjectionErrorHandler(ConsumerRecordRecoverer recoverer, BackOff backOff) {
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidOrderEventException.class);
        return handler;
    }

    /**
     * Partición negativa: el productor elige la partición por la clave
     * (`orderId`), así la DLT no necesita la misma cantidad de particiones.
     */
    static TopicPartition deadLetterDestination(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(KafkaTopics.ORDER_EVENTS_DLT, -1);
    }
}
