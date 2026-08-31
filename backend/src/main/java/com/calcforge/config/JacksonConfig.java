package com.calcforge.config;

import com.fasterxml.jackson.core.JsonGenerator;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ensures every {@code BigDecimal} in a JSON response is written as a plain decimal
 * (e.g. {@code 0.0000001}) rather than Java's default scientific notation for some
 * scales (e.g. {@code 1E-7}) - the frontend should never have to parse scientific
 * notation out of an API response.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalPlainStringCustomizer() {
        return builder -> builder.featuresToEnable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }
}
