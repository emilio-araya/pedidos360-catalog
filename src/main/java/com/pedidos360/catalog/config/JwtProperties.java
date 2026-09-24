package com.pedidos360.catalog.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.security.jwt")
public record JwtProperties(@NotBlank String issuer, @NotBlank String audience) {
}
