package com.ravalgroups.forms.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI formsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Raval Group Forms API")
                        .description(
                                "Central Forms / Questionnaire Service. Authenticate with an IAM service token "
                                        + "(POST /api/v1/auth/context on IAM with applicationCode=forms, "
                                        + "serviceCode=forms-api). Identity tokens (aud=iam-auth) are rejected.")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
