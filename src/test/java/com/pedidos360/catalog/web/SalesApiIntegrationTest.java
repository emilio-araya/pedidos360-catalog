package com.pedidos360.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.messaging.kafka.SalesProjectionListener;
import com.pedidos360.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes"
})
class SalesApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SalesProjectionListener listener;

    @Autowired
    private ProductRepository productRepository;

    private UUID productId;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        apply("ORDER_ACCEPTED", "ACEPTADO", "CREADO", 5);
        apply("ORDER_DELIVERED", "ENTREGADO", "DESPACHADO", 3);
        apply("ORDER_CANCELLED", "CANCELADO", "ACEPTADO", 2);
    }

    @Test
    void adminReadsTheExactSalesStatsOfAProduct() throws Exception {
        byte[] body = mockMvc.perform(get("/api/catalog/sales/products/{productId}", productId).with(role("Admin")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, Object> stats = objectMapper.readValue(body, new TypeReference<>() { });
        assertThat(stats).containsExactlyInAnyOrderEntriesOf(Map.of(
                "productId", productId.toString(),
                "unitsSold", 5,
                "unitsDelivered", 3,
                "unitsCancelled", 2,
                "netUnits", 3));
    }

    @Test
    void operadorListsAllSalesStats() throws Exception {
        byte[] body = mockMvc.perform(get("/api/catalog/sales").with(role("Operador")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        List<Map<String, Object>> stats = objectMapper.readValue(body, new TypeReference<>() { });
        assertThat(stats).contains(Map.of(
                "productId", productId.toString(),
                "unitsSold", 5,
                "unitsDelivered", 3,
                "unitsCancelled", 2,
                "netUnits", 3));
    }

    @Test
    void aProductWithoutSalesAppearsInTheListWithZeroedStats() throws Exception {
        Product fresh = productRepository.save(new Product(
                "SKU-NO-SALES", "Producto sin ventas", null, new BigDecimal("1000.00"), 7, true, null));

        byte[] body = mockMvc.perform(get("/api/catalog/sales").with(role("Operador")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        List<Map<String, Object>> stats = objectMapper.readValue(body, new TypeReference<>() { });
        assertThat(stats).contains(Map.of(
                "productId", fresh.getId().toString(),
                "unitsSold", 0,
                "unitsDelivered", 0,
                "unitsCancelled", 0,
                "netUnits", 0));
    }

    @Test
    void aProductWithoutSalesReturnsZeroedStatsInsteadOfNotFound() throws Exception {
        Product fresh = productRepository.save(new Product(
                "SKU-NO-SALES-BY-ID", "Otro producto sin ventas", null, new BigDecimal("500.00"), 3, true, null));

        mockMvc.perform(get("/api/catalog/sales/products/{productId}", fresh.getId()).with(role("Admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(fresh.getId().toString()))
                .andExpect(jsonPath("$.unitsSold").value(0))
                .andExpect(jsonPath("$.unitsDelivered").value(0))
                .andExpect(jsonPath("$.unitsCancelled").value(0))
                .andExpect(jsonPath("$.netUnits").value(0));
    }

    @Test
    void clienteIsForbidden() throws Exception {
        mockMvc.perform(get("/api/catalog/sales").with(role("Cliente")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/catalog/sales/products/{productId}", productId).with(role("Cliente")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/catalog/sales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/catalog/sales/products/{productId}", productId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void unknownProductStatsReturnTheProblemFormat() throws Exception {
        UUID unknown = UUID.randomUUID();
        mockMvc.perform(get("/api/catalog/sales/products/{productId}", unknown).with(role("Admin")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PRODUCT_SALES_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/api/catalog/sales/products/" + unknown));
    }

    @Test
    void cognitoNamespaceMirrorsTheSameRoles() throws Exception {
        mockMvc.perform(get("/aws/api/catalog/sales/products/{productId}", productId).with(role("Operador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netUnits").value(3));
        mockMvc.perform(get("/aws/api/catalog/sales").with(role("Cliente")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/aws/api/catalog/sales"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productListingStaysPublic() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/aws/api/catalog/products"))
                .andExpect(status().isOk());
    }

    private void apply(String eventType, String status, String previousStatus, int quantity) {
        String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + eventType + "\","
                + "\"schemaVersion\":1,\"orderId\":\"" + UUID.randomUUID() + "\",\"status\":\"" + status + "\","
                + "\"previousStatus\":\"" + previousStatus + "\","
                + "\"items\":[{\"productId\":\"" + productId + "\",\"quantity\":" + quantity + "}],"
                + "\"occurredAt\":\"2026-11-05T12:00:00Z\"}";
        listener.onOrderEvent(new ConsumerRecord<>("pedidos360.order-events", 0, 0L, "key", payload));
    }

    private static RequestPostProcessor role(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
