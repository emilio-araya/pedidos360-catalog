package com.pedidos360.catalog.config;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({JwtProperties.class, CognitoJwtProperties.class})
public class SecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain entraSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("entraJwtDecoder") JwtDecoder decoder,
            SecurityProblemSupport problemSupport
    ) throws Exception {
        return configure(http, new String[]{"/api/**", "/internal/**"}, decoder, problemSupport);
    }

    @Bean
    @Order(2)
    SecurityFilterChain cognitoSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("cognitoJwtDecoder") JwtDecoder decoder,
            SecurityProblemSupport problemSupport
    ) throws Exception {
        return configure(http, new String[]{"/aws/api/**"}, decoder, problemSupport);
    }

    @Bean
    @Order(3)
    SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    private SecurityFilterChain configure(
            HttpSecurity http,
            String[] matchers,
            JwtDecoder decoder,
            SecurityProblemSupport problemSupport
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityMatcher(matchers)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // La lectura tambien exige un rol conocido, no solo estar
                        // autenticado. El caso define tres roles y un token de
                        // Cognito sin grupo debe recibir 403 en lugar de poder
                        // leer el catalogo. Con .authenticated() un usuario sin
                        // grupoReconocido tendria acceso de lectura.
                        .requestMatchers(HttpMethod.GET, publicReadPaths())
                        .hasAnyRole("Admin", "Operador", "Cliente")
                        .requestMatchers(mutationPaths())
                        .hasAnyRole("Admin", "Operador")
                        .anyRequest().hasAnyRole("Admin", "Operador", "Cliente"))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint((request, response, exception) -> problemSupport.write(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "UNAUTHORIZED",
                                "Se requiere un token Bearer válido"))
                        .accessDeniedHandler((request, response, exception) -> problemSupport.write(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "FORBIDDEN",
                                "El token no tiene permisos para esta operación"))
                        .jwt(jwt -> jwt
                                .decoder(decoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    private String[] publicReadPaths() {
        return new String[]{
                "/api/catalog/products",
                "/api/catalog/products/**",
                "/aws/api/catalog/products",
                "/aws/api/catalog/products/**"
        };
    }

    private String[] mutationPaths() {
        return new String[]{
                "/api/catalog/products/**",
                "/aws/api/catalog/products/**",
                "/api/internal/catalog/stock/reservations",
                "/api/internal/catalog/stock/reservations/**",
                "/aws/api/internal/catalog/stock/reservations",
                "/aws/api/internal/catalog/stock/reservations/**",
                "/internal/catalog/stock/reservations",
                "/internal/catalog/stock/reservations/**"
        };
    }

    private Converter<Jwt, ? extends org.springframework.security.authentication.AbstractAuthenticationToken>
            jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter entraRoleConverter = new JwtGrantedAuthoritiesConverter();
        entraRoleConverter.setAuthoritiesClaimName("roles");
        entraRoleConverter.setAuthorityPrefix("ROLE_");
        JwtGrantedAuthoritiesConverter cognitoRoleConverter = new JwtGrantedAuthoritiesConverter();
        cognitoRoleConverter.setAuthoritiesClaimName("cognito:groups");
        cognitoRoleConverter.setAuthorityPrefix("ROLE_");
        JwtGrantedAuthoritiesConverter scopeConverter = new JwtGrantedAuthoritiesConverter();
        scopeConverter.setAuthoritiesClaimName("scope");
        scopeConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            addAuthorities(authorities, entraRoleConverter.convert(jwt));
            addAuthorities(authorities, cognitoRoleConverter.convert(jwt));
            addAuthorities(authorities, scopeConverter.convert(jwt));
            return authorities;
        });
        return converter;
    }

    private void addAuthorities(
            Collection<GrantedAuthority> target,
            Collection<GrantedAuthority> authorities
    ) {
        if (authorities != null) target.addAll(authorities);
    }
}
