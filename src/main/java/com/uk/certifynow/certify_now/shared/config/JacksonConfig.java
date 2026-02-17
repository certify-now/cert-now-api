package com.uk.certifynow.certify_now.shared.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.PropertyNamingStrategies;

@Configuration("sharedJacksonConfig")
public class JacksonConfig {

  @Bean
  public JsonMapperBuilderCustomizer jsonNamingCustomizer() {
    return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
  }
}
