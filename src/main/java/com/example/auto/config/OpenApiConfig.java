package com.example.auto.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 설정
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("네이버 스토어 상품 업로드 자동화 API")
                        .description("엑셀 파일을 업로드하여 네이버 스토어에 상품을 자동으로 등록하는 API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Auto Service")
                                .email("support@example.com")));
    }
}

