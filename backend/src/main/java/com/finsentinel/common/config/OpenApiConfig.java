package com.finsentinel.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finSentinelOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinSentinel AI API")
                        .version("v1")
                        .description("Smart Financial Risk Analyzer and Adaptive Payment Advisor REST API"));
    }
}
