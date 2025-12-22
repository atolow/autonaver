package com.example.auto.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 홈 페이지 및 플랫폼 선택 컨트롤러
 */
@Controller
public class HomeController {
    
    /**
     * 기본 페이지 - 플랫폼 선택
     */
    @GetMapping("/")
    public String home() {
        return "platform-select";
    }
    
    /**
     * 네이버 플랫폼 페이지
     */
    @GetMapping("/naver")
    public String naver() {
        return "naver";
    }
    
    /**
     * 쿠팡 플랫폼 페이지
     */
    @GetMapping("/coupang")
    public String coupang() {
        return "coupang";
    }
    
    /**
     * 11번가 플랫폼 페이지
     */
    @GetMapping("/ELEVEN_STREET")
    public String elevenStreet() {
        return "eleven-street";
    }
}

