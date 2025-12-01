package com.example.auto.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 네이버 커머스 API 설정 프로퍼티
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "naver.commerce")
public class NaverCommerceProperties {
    
    private String clientId;
    private String clientSecret;
    private String apiBaseUrl;
}

