package com.pedidos360.catalog.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * El prefijo de {@link CognitoJwtProperties} debe coincidir con el anidamiento
 * del archivo de configuracion. Si cognito queda como hermano de security, los
 * valores se enlazan a catalog.cognito, la clase lee catalog.security.cognito,
 * isConfigured() devuelve false y el decodificador de Cognito lanza para todo
 * token de /aws/api/**.
 */
class CognitoPropertiesNestingTest {

    private static final List<String> REQUIRED_KEYS = List.of(
            "catalog.security.cognito.issuer",
            "catalog.security.cognito.audience",
            "catalog.security.cognito.jwk-set-uri");

    @Test
    void cloudProfileDeclaresCognitoUnderTheSecurityPrefix() throws Exception {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("cloud", new ClassPathResource("application-cloud.yml"));

        assertThat(sources).as("application-cloud.yml debe cargarse").isNotEmpty();
        PropertySource<?> source = sources.get(0);

        for (String key : REQUIRED_KEYS) {
            assertThat(source.containsProperty(key))
                    .as("falta la propiedad %s en application-cloud.yml; CognitoJwtProperties lee catalog.security.cognito", key)
                    .isTrue();
        }
    }

    @Test
    void cloudProfileDoesNotDeclareCognitoOutsideSecurity() throws Exception {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("cloud", new ClassPathResource("application-cloud.yml"));
        PropertySource<?> source = sources.get(0);

        assertThat(source.containsProperty("catalog.cognito.issuer"))
                .as("cognito no debe quedar como hermano de security: nadie lee catalog.cognito")
                .isFalse();
    }
}
