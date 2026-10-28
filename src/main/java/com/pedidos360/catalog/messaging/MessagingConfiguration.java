package com.pedidos360.catalog.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología del catálogo: declara el exchange compartido y las colas que
 * consume. La cola de resultados la declara `orders`, que es quien la consume.
 */
@Configuration
public class MessagingConfiguration {

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(MessagingTopology.EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue reserveQueue() {
        return RetryTopology.mainQueue(MessagingTopology.RESERVE_QUEUE);
    }

    @Bean
    public Queue releaseQueue() {
        return RetryTopology.mainQueue(MessagingTopology.RELEASE_QUEUE);
    }

    @Bean
    public Declarables reserveRetryTopology() {
        return RetryTopology.declarables(MessagingTopology.RESERVE_QUEUE);
    }

    @Bean
    public Declarables releaseRetryTopology() {
        return RetryTopology.declarables(MessagingTopology.RELEASE_QUEUE);
    }

    @Bean
    public Binding reserveBinding(Queue reserveQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(reserveQueue).to(eventsExchange)
                .with(MessagingTopology.ACCEPTED_ROUTING_KEY);
    }

    @Bean
    public Binding releaseBinding(Queue releaseQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(releaseQueue).to(eventsExchange)
                .with(MessagingTopology.CANCELLED_ROUTING_KEY);
    }

    @Bean
    public Binding orphanReleaseBinding(Queue releaseQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(releaseQueue).to(eventsExchange)
                .with(MessagingTopology.ORPHAN_ROUTING_KEY);
    }

    /**
     * Serializa los resultados que publica el catálogo con el mismo ObjectMapper
     * que la API HTTP. La lectura de los eventos entrantes no pasa por acá: la
     * hace {@link ResilientConsumer}, para poder mandar a la DLQ lo ilegible.
     */
    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
