package com.pedidos360.catalog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.catalog.api.dto.ProductRequest;
import com.pedidos360.catalog.api.dto.ProductResponse;
import com.pedidos360.catalog.api.dto.ReservationCreateRequest;
import com.pedidos360.catalog.api.dto.ReservationItemRequest;
import com.pedidos360.catalog.service.ProductService;
import com.pedidos360.catalog.service.StockReservationService;
import com.pedidos360.catalog.service.StockReservationService.ReservationResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes"
})
class StockReservationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private StockReservationService reservationService;

    @Test
    void reservationIsIdempotentAndReleaseRestoresStockOnlyOnce() throws Exception {
        ProductResponse product = productService.create(new ProductRequest(
                "stock-001", "Producto stock", null, new BigDecimal("20.00"), 5, true));
        UUID orderId = UUID.randomUUID();
        ReservationCreateRequest request = new ReservationCreateRequest(
                orderId, List.of(new ReservationItemRequest(product.id(), 2)));

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("RESERVED"));
        assertStock(product.id(), 3);

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
        assertStock(product.id(), 3);

        ReservationCreateRequest conflictingRequest = new ReservationCreateRequest(
                orderId, List.of(new ReservationItemRequest(product.id(), 1)));
        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(conflictingRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_IDEMPOTENCY_CONFLICT"));
        assertStock(product.id(), 3);

        mockMvc.perform(delete("/internal/catalog/stock/reservations/{orderId}", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
                .andExpect(status().isNoContent());
        assertStock(product.id(), 5);

        mockMvc.perform(delete("/internal/catalog/stock/reservations/{orderId}", orderId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador"))))
                .andExpect(status().isNoContent());
        assertStock(product.id(), 5);

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        assertStock(product.id(), 5);
    }

    @Test
    void concurrentIdenticalRequestsDeductAndRestoreStockOnlyOnce() throws Exception {
        ProductResponse product = productService.create(new ProductRequest(
                "stock-concurrent", "Producto concurrente", null, new BigDecimal("8.00"), 5, true));
        UUID orderId = UUID.randomUUID();
        ReservationCreateRequest request = new ReservationCreateRequest(
                orderId, List.of(new ReservationItemRequest(product.id(), 2)));

        List<ReservationResult> reserveResults = runConcurrently(
                () -> reservationService.reserve(request),
                () -> reservationService.reserve(request));
        org.assertj.core.api.Assertions.assertThat(reserveResults)
                .filteredOn(ReservationResult::created)
                .hasSize(1);
        assertStock(product.id(), 3);

        List<Boolean> releaseResults = runConcurrently(
                () -> reservationService.release(orderId),
                () -> reservationService.release(orderId));
        org.assertj.core.api.Assertions.assertThat(releaseResults)
                .containsExactlyInAnyOrder(true, false);
        assertStock(product.id(), 5);
    }

    @Test
    void insufficientStockDoesNotCreateReservationAndEndpointRequiresAuthentication() throws Exception {
        ProductResponse product = productService.create(new ProductRequest(
                "stock-002", "Producto sin stock", null, new BigDecimal("5.00"), 1, true));
        ReservationCreateRequest request = new ReservationCreateRequest(
                UUID.randomUUID(), List.of(new ReservationItemRequest(product.id(), 2)));

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/catalog/stock/reservations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
        assertStock(product.id(), 1);
    }

    private <T> List<T> runConcurrently(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier startBarrier = new CyclicBarrier(2);
            Future<T> firstResult = executor.submit(awaitStart(first, startBarrier));
            Future<T> secondResult = executor.submit(awaitStart(second, startBarrier));
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private <T> Callable<T> awaitStart(Callable<T> task, CyclicBarrier startBarrier) {
        return () -> {
            startBarrier.await();
            return task.call();
        };
    }

    private void assertStock(UUID productId, int expectedStock) {
        ProductResponse response = productService.findById(productId);
        org.assertj.core.api.Assertions.assertThat(response.stock()).isEqualTo(expectedStock);
    }
}
