package com.pedidos360.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * El id de correlación tiene que sobrevivir el viaje: el cliente puede mandarlo,
 * pero si falta o es inseguro el servicio genera uno propio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes"
})
class RequestIdFilterIntegrationTest {

    private static final String HEADER = "X-Request-Id";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesARequestIdWhenTheClientDoesNotSendOne() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/catalog/products").with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER))
                .as("sin header entrante el servicio debe generar un UUID")
                .isNotBlank()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void preservesAValidIncomingRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/catalog/products")
                        .header(HEADER, "checkout-abc.123")
                        .with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER)).isEqualTo("checkout-abc.123");
    }

    @Test
    void replacesAnUnsafeRequestIdInsteadOfLoggingIt() throws Exception {
        String injected = "abc\ndef";

        MvcResult result = mockMvc.perform(get("/api/catalog/products")
                        .header(HEADER, injected)
                        .with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader(HEADER))
                .as("un id con salto de línea permitiría inyectar líneas falsas en el log")
                .isNotEqualTo(injected)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        String tooLong = "a".repeat(65);
        MvcResult longResult = mockMvc.perform(get("/api/catalog/products")
                        .header(HEADER, tooLong)
                        .with(client()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(longResult.getResponse().getHeader(HEADER)).isNotEqualTo(tooLong);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor client() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"));
    }
}
