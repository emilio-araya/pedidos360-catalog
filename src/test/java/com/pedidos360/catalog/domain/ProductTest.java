package com.pedidos360.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void adjustsStockAndNeverMakesItNegative() {
        Product product = new Product(
                "TEST-001", "Producto", null, new BigDecimal("10.00"), 5, true);

        product.adjustStock(-2);
        assertThat(product.getStock()).isEqualTo(3);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> product.adjustStock(-4))
                .withMessage("El stock no puede ser negativo");
        assertThat(product.getStock()).isEqualTo(3);
    }
}
