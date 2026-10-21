package com.pedidos360.catalog.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pedidos360.catalog.api.dto.ProductRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * El listado de productos crecía sin techo: con un catálogo grande la consulta
 * y el payload no tienen límite. Estos tests fijan la paginación.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.integration.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=integration-test-secret-key-at-least-32-bytes",
        // Base propia: los totales de la paginación tienen que ser exactos y no
        // depender de los productos que otras clases de test hayan dejado.
        "spring.datasource.url=jdbc:h2:mem:catalog_pagination;MODE=Oracle;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class ProductPaginationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void createCatalog() throws Exception {
        create("SKU-A", "Auriculares");
        create("SKU-B", "Bocina");
        create("SKU-C", "Cargador");
    }

    @Test
    void paginatesProductsAndReportsTheTotal() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                        .param("page", "0")
                        .param("size", "2")
                        .with(client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Auriculares"));

        mockMvc.perform(get("/api/catalog/products")
                        .param("page", "1")
                        .param("size", "2")
                        .with(client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Cargador"));
    }

    @Test
    void appliesDefaultPaginationWhenNoParametersAreGiven() throws Exception {
        mockMvc.perform(get("/api/catalog/products").with(client()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void rejectsPageRequestsOutsideTheAllowedRange() throws Exception {
        mockMvc.perform(get("/api/catalog/products").param("size", "101").with(client()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalog/products").param("size", "0").with(client()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/catalog/products").param("page", "-1").with(client()))
                .andExpect(status().isBadRequest());
    }

    private void create(String sku, String name) throws Exception {
        ProductRequest request = new ProductRequest(
                sku, name, "desc", new BigDecimal("1000.00"), 5, true);
        mockMvc.perform(post("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Operador")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor client() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente"));
    }
}
