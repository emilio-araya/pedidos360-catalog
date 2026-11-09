package com.pedidos360.catalog.messaging.kafka;

import static com.pedidos360.catalog.messaging.kafka.OrderEventFixtures.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pedidos360.catalog.api.dto.ProductSalesResponse;
import com.pedidos360.catalog.service.SalesProjectionService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Cableado real contra un broker embebido: el listener consume
 * `pedidos360.order-events` con el grupo `catalog-sales-projection`, aplica los
 * eventos válidos y manda a `pedidos360.order-events.DLT` los inválidos.
 */
@SpringBootTest
@ActiveProfiles("local")
@DirtiesContext
@EmbeddedKafka(partitions = 3, topics = {KafkaTopics.ORDER_EVENTS, KafkaTopics.ORDER_EVENTS_DLT})
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes",
        "app.kafka.sales-projection.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
class SalesProjectionKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Autowired
    private SalesProjectionService salesService;

    @Test
    void appliesValidEventsAndDeadLettersInvalidOnes() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        String malformed = "{\"eventId\": not-json";
        String unknownType = event(UUID.randomUUID(), "ORDER_REFUNDED", 1, orderId,
                "CANCELADO", "ENTREGADO", productId, 1);
        String futureSchema = event(UUID.randomUUID(), "ORDER_ACCEPTED", 2, orderId,
                "ACEPTADO", "CREADO", productId, 1);

        for (String payload : List.of(malformed, unknownType, futureSchema)) {
            kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, orderId.toString(), payload).get();
        }
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, orderId.toString(),
                event(UUID.randomUUID(), "ORDER_ACCEPTED", 1, orderId, "ACEPTADO", "CREADO", productId, 4)).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(salesService.findAll()).contains(new ProductSalesResponse(productId, 4, 0, 0, 4)));

        try (Consumer<String, String> dltConsumer = dltConsumer()) {
            broker.consumeFromAnEmbeddedTopic(dltConsumer, KafkaTopics.ORDER_EVENTS_DLT);
            List<String> deadLetters = new ArrayList<>();
            for (ConsumerRecord<String, String> deadLetter
                    : KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(30), 3)) {
                assertThat(deadLetter.key()).isEqualTo(orderId.toString());
                deadLetters.add(deadLetter.value());
            }
            assertThat(deadLetters).containsExactlyInAnyOrder(malformed, unknownType, futureSchema);
        }
    }

    private Consumer<String, String> dltConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-inspector", "false", broker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new StringDeserializer())
                .createConsumer();
    }
}
