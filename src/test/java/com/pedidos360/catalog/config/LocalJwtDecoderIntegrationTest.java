package com.pedidos360.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "catalog.sample-data.enabled=false",
        "catalog.security.jwt.issuer=https://issuer.jwt.test",
        "catalog.security.jwt.audience=pedidos360-api-test",
        "catalog.security.jwt.hmac-secret=jwt-integration-test-secret-key-at-least-32-bytes"
})
class LocalJwtDecoderIntegrationTest {

    private static final String SECRET = "jwt-integration-test-secret-key-at-least-32-bytes";
    private static final String ISSUER = "https://issuer.jwt.test";
    private static final String AUDIENCE = "pedidos360-api-test";

    @Autowired
    private JwtDecoder decoder;

    @Test
    void acceptsValidLocallySignedToken() throws Exception {
        String token = token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60), null, SECRET);

        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("integration-user");
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    void rejectsWrongSignatureIssuerAudienceExpirationAndFutureNotBefore() throws Exception {
        String valid = token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60), null, SECRET);

        assertInvalid(token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60), null, "another-secret-key-that-is-also-long-enough"));
        assertInvalid(token("https://wrong-issuer.test", List.of(AUDIENCE), Instant.now().plusSeconds(60), null, SECRET));
        assertInvalid(token(ISSUER, List.of("wrong-audience"), Instant.now().plusSeconds(60), null, SECRET));
        assertInvalid(token(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(60), null, SECRET));
        assertInvalid(token(ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(60), Instant.now().plusSeconds(120), SECRET));
        org.assertj.core.api.Assertions.assertThat(valid).isNotBlank();
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    private String token(
            String issuer,
            List<String> audience,
            Instant expiration,
            Instant notBefore,
            String secret) throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("integration-user")
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiration))
                .notBeforeTime(notBefore == null ? null : Date.from(notBefore))
                .claim("roles", List.of("Cliente"))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
