package com.pedidos360.catalog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.security.jwt")
public record LocalHmacProperties(
        @NotBlank @Size(min = 32) String hmacSecret) {
}
