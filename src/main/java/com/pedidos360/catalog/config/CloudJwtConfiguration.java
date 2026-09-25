package com.pedidos360.catalog.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@Profile("!local")
public class CloudJwtConfiguration {

    @Bean
    @Qualifier("entraJwtDecoder")
    JwtDecoder entraJwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(properties.issuer()).build();
        List<org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt>> validators =
                new ArrayList<>();
        validators.add(JwtValidators.createDefault());
        validators.add(new IssuerValidator(properties.issuer()));
        validators.add(new AudienceValidator(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

    @Bean
    @Qualifier("cognitoJwtDecoder")
    JwtDecoder cognitoJwtDecoder(CognitoJwtProperties properties) {
        if (!properties.isConfigured()) {
            return token -> {
                throw new JwtException("Cognito no está configurado para este ambiente");
            };
        }
        NimbusJwtDecoder decoder = properties.jwkSetUri() == null || properties.jwkSetUri().isBlank()
                ? (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(properties.issuer())
                : NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                new IssuerValidator(properties.issuer()),
                new CognitoAudienceValidator(properties.audience()),
                new AccessTokenValidator()
        ));
        return decoder;
    }
}
