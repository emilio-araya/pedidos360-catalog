package com.pedidos360.catalog.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

@Configuration(proxyBeanMethods = false)
@Profile("local")
@EnableConfigurationProperties(LocalHmacProperties.class)
public class LocalHmacConfiguration {

    @Bean
    @Qualifier("entraJwtDecoder")
    JwtDecoder entraJwtDecoder(JwtProperties jwtProperties, LocalHmacProperties localProperties) {
        byte[] secretBytes = localProperties.hmacSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("El secreto HMAC local debe tener al menos 32 bytes");
        }
        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                JwtValidators.createDefault(),
                new IssuerValidator(jwtProperties.issuer()),
                new AudienceValidator(jwtProperties.audience())));
        return decoder;
    }

    @Bean
    @Qualifier("cognitoJwtDecoder")
    JwtDecoder cognitoJwtDecoder() {
        return token -> {
            throw new JwtException("Cognito no está disponible en el perfil local");
        };
    }
}
