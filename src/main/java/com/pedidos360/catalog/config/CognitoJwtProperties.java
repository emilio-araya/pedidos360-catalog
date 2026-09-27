package com.pedidos360.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.security.cognito")
public record CognitoJwtProperties(
        String issuer,
        String audience,
        String jwkSetUri
) {
    public boolean isConfigured() {
        return issuer != null && !issuer.isBlank()
                && audience != null && !audience.isBlank();
    }
}
