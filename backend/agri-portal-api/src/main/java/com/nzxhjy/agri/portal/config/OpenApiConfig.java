package com.nzxhjy.agri.portal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI agriPortalOpenAPI() {
        return new OpenAPI().info(new Info().title("农资现货交易系统门户接口").version("0.1.0"));
    }
}
