package com.pedidos360.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class EntraTokenValidatorTest {

    @Test
    void acceptsV1IssuerAndApiUriAudience() {
        Jwt jwt = jwt(
                "https://sts.windows.net/1feca74f-8331-414a-bd8d-2d687b22a7b3/",
                List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01")
        );
        IssuerValidator issuerValidator = new IssuerValidator(
                "https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0"
        );
        AudienceValidator audienceValidator = new AudienceValidator(
                "150f51db-4084-4979-b1a1-e6a6e7893a01"
        );

        assertThat(issuerValidator.validate(jwt).hasErrors()).isFalse();
        assertThat(audienceValidator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherTenantIssuer() {
        Jwt jwt = jwt(
                "https://sts.windows.net/00000000-0000-0000-0000-000000000000/",
                List.of("api://150f51db-4084-4979-b1a1-e6a6e7893a01")
        );
        IssuerValidator validator = new IssuerValidator(
                "https://login.microsoftonline.com/1feca74f-8331-414a-bd8d-2d687b22a7b3/v2.0"
        );

        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private Jwt jwt(String issuer, List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
