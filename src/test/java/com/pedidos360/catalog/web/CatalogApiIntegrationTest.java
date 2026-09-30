package com.pedidos360.catalog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedidos360.catalog.api.dto.ProductRequest;
import com.pedidos360.catalog.api.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
class CatalogApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void productCrudEnforcesAuthenticationAndExactRoles() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        ProductRequest request = new ProductRequest(
                "int-001", "Producto integration", "Descripción", new BigDecimal("12.50"), 7, true);

        mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        byte[] response = mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/catalog/products/")))
                .andExpect(jsonPath("$.sku").value("INT-001"))
                .andExpect(jsonPath("$.stock").value(7))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        ProductResponse created = objectMapper.readValue(response, ProductResponse.class);

        mockMvc.perform(get("/api/catalog/products/{id}", created.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.id().toString()));

        ProductRequest updatedRequest = new ProductRequest(
                "int-001", "Producto actualizado", null, new BigDecimal("13.00"), 9, false);
        mockMvc.perform(patch("/api/catalog/products/{id}/stock", created.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":11}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(11));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/catalog/products/{id}", created.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updatedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Producto actualizado"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(delete("/api/catalog/products/{id}", created.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/catalog/products/{id}", created.id())
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void cognitoNamespaceUsesTheSameRoleContract() throws Exception {
        mockMvc.perform(get("/aws/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isOk());

        ProductRequest request = new ProductRequest(
                "aws-001", "Producto AWS", "Descripción", new BigDecimal("15.00"), 3, true);
        mockMvc.perform(post("/aws/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/aws/api/catalog/products/")));
    }

    @Test
    void rejectsInvalidPayloadWithProblemDetails() throws Exception {
        mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\":\"\",\"price\":-1,\"stock\":-2,\"active\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void malformedProductIdIsAProblemDetail() throws Exception {
        mockMvc.perform(get("/api/catalog/products/not-a-uuid")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }
}
