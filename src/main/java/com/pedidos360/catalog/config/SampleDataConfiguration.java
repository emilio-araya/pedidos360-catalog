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
                            true,
                            "/img/mouse.jpg"),
                    new Product(
                            "P-002",
                            "Teclado mecánico",
                            "Teclado mecánico en español",
                            new BigDecimal("45990.00"),
                            40,
                            true,
                            "/img/teclado.jpg"),
                    new Product(
                            "P-003",
                            "Monitor 24 pulgadas",
                            "Monitor Full HD con panel IPS",
                            new BigDecimal("129990.00"),
                            10,
                            true,
                            "/img/monitor.jpg"),
                    new Product(
                            "P-004",
                            "Auriculares inalámbricos",
                            "Auriculares circumaurales con cancelación de ruido",
                            new BigDecimal("89990.00"),
                            12,
                            true,
                            "/img/auriculares.jpg"),
                    new Product(
                            "P-005",
                            "Webcam HD",
                            "Cámara 720p con micrófono integrado",
                            new BigDecimal("39990.00"),
                            18,
                            true,
                            "/img/webcam.jpg"),
                    new Product(
                            "P-006",
                            "Micrófono USB",
                            "Micrófono condensador para streaming",
                            new BigDecimal("129990.00"),
                            6,
                            true,
                            "/img/microfono.jpg"),
                    new Product(
                            "P-007",
                            "Parlante Bluetooth",
                            "Parlante portátil resistente al agua",
                            new BigDecimal("79990.00"),
                            9,
                            true,
                            "/img/parlante.jpg"),
                    new Product(
                            "P-008",
                            "Dock USB-C",
                            "Dock con HDMI, USB 3.0 y lector de tarjetas",
                            new BigDecimal("64990.00"),
                            15,
                            true,
                            "/img/hub.jpg"),
                    new Product(
                            "P-009",
                            "Memoria USB 4 GB",
                            "Pendrive USB 2.0 con tapa deslizante",
                            new BigDecimal("7990.00"),
                            30,
                            true,
                            "/img/pendrive.jpg")));
        };
    }
}
