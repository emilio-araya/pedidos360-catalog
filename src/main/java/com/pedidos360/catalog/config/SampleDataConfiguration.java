package com.pedidos360.catalog.config;

import com.pedidos360.catalog.domain.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@ConditionalOnProperty(
        name = "catalog.sample-data.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SampleDataConfiguration {

    @Bean
    ApplicationRunner loadSampleProducts(ProductRepository productRepository) {
        return arguments -> {
            if (productRepository.count() > 0) {
                return;
            }
            productRepository.saveAll(List.of(
                    new Product(
                            "P-001",
                            "Mouse inalámbrico",
                            "Mouse compacto con receptor USB",
                            new BigDecimal("19990.00"),
                            25,
                            true),
                    new Product(
                            "P-002",
                            "Teclado mecánico",
                            "Teclado mecánico en español",
                            new BigDecimal("45990.00"),
                            40,
                            true),
                    new Product(
                            "P-003",
                            "Monitor 24 pulgadas",
                            "Monitor Full HD con panel IPS",
                            new BigDecimal("129990.00"),
                            10,
                            true)));
        };
    }
}
