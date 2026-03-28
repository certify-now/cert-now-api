package com.uk.certifynow.certify_now.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaginationProperties.class)
public class PaginationConfig {}
