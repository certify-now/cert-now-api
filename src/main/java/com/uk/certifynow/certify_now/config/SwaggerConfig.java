package com.uk.certifynow.certify_now.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CertifyNow API")
                                .version("1.0")
                                .description(
                                        """
                                                API for CertifyNow - Platform connecting property owners with certified engineers.

                                                ## Authentication
                                                Most endpoints require JWT authentication. To authenticate:
                                                1. Call POST /api/v1/auth/register or /api/v1/auth/login
                                                2. Copy the `access_token` from the response
                                                3. Click the 'Authorize' button (🔒) at the top
                                                4. Enter: Bearer <your_access_token>
                                                5. Click 'Authorize' and 'Close'

                                                ## Token Expiry
                                                Access tokens expire after 15 minutes. Use POST /api/v1/auth/refresh to get new tokens.

                                                ## Roles
                                                - CUSTOMER: Property owners booking certificates
                                                - ENGINEER: Certified professionals performing inspections
                                                - ADMIN: Platform administrators
                                                """)
                                .contact(
                                        new Contact()
                                                .name("CertifyNow Support")
                                                .email("support@certifynow.co.uk")
                                                .url("https://certifynow.co.uk"))
                                .license(
                                        new License().name("Proprietary").url("https://certifynow.co.uk/terms")))
                .servers(List.of(new Server().url(baseUrl).description("API Server")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Bearer Authentication",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                                .description(
                                                        "Enter JWT token obtained from /api/v1/auth/login or /api/v1/auth/register")
                                                .in(SecurityScheme.In.HEADER)
                                                .name("Authorization")));
    }
}
