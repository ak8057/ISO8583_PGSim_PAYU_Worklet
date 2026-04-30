package com.payu.pgsim.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ISO 8583 Simulator API")
                        .description("REST API aligned with Wibmo BRD-style configuration and runtime endpoints")
                        .version("1.0"));
    }
}
