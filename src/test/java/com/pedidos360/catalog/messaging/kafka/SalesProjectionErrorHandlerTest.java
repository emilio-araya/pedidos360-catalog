package com.pedidos360.catalog.messaging.kafka;

import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.record;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Clasificación de fallos del consumidor: lo inválido va directo a la DLT sin
 * reintentos; lo transitorio se reintenta antes de rendirse.
 */
class SalesProjectionErrorHandlerTest {

    private final ConsumerRecordRecoverer recoverer = mock(ConsumerRecordRecoverer.class);
    private final DefaultErrorHandler handler =
            KafkaConsumerConfiguration.salesProjectionErrorHandler(recoverer, new FixedBackOff(0L, 3L));
    private final ConsumerRecord<String, String> record = record("{}");

    @Test
    void invalidEventGoesToTheDeadLetterTopicWithoutRetry() {
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new InvalidOrderEventException("Unsupported schemaVersion: 2"));

        boolean recovered = handler.handleOne(failure, record, mock(Consumer.class),
                mock(MessageListenerContainer.class));

        assertThat(recovered).isTrue();
        verify(recoverer).accept(same(record), any());
    }

    @Test
    void transientFailureIsRetriedBeforeRecovery() {
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new QueryTimeoutException("database timeout"));

        boolean recovered = handler.handleOne(failure, record, mock(Consumer.class),
                mock(MessageListenerContainer.class));

        assertThat(recovered).isFalse();
        verify(recoverer, never()).accept(any(), any());
    }

    @Test
    void deadLetterDestinationIsTheSharedDltTopic() {
        TopicPartition destination = KafkaConsumerConfiguration.deadLetterDestination(
                record, new InvalidOrderEventException("bad"));

        assertThat(destination.topic()).isEqualTo("pedidos360.order-events.DLT");
        assertThat(destination.partition()).isNegative();
    }
}
