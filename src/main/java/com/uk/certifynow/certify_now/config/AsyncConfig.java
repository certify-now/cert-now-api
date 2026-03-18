package com.uk.certifynow.certify_now.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "emailTaskExecutor")
  public Executor emailTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("email-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "authEventsExecutor")
  public Executor authEventsExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(200);
    executor.setThreadNamePrefix("auth-events-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "pdfTaskExecutor")
  public Executor pdfTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(5);
    executor.setQueueCapacity(25);
    executor.setThreadNamePrefix("pdf-gen-");
    executor.initialize();
    return executor;
  }

  /**
   * Provides a {@link RestClient.Builder} prototype bean. Spring Boot 4 does not auto-configure
   * this bean when only {@code spring-boot-starter-web} is present, so it must be declared
   * explicitly. Each call to {@code restClientBuilder()} returns a fresh builder instance (scope is
   * prototype by default for {@code @Bean} methods returning a builder type).
   */
  @Bean
  public RestClient.Builder restClientBuilder() {
    return RestClient.builder();
  }

  /**
   * General-purpose async executor used by the matching engine and any {@code @Async} methods that
   * do not explicitly name an executor. Named "taskExecutor" so Spring picks it up automatically
   * for unqualified {@code @Async} calls, silencing the multi-bean ambiguity warning.
   */
  @Bean(name = {"matchingTaskExecutor", "taskExecutor"})
  public Executor matchingTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("matching-");
    executor.initialize();
    return executor;
  }
}
