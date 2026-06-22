package com.fyp.bloodinventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuditTrailWebMvcConfig implements WebMvcConfigurer {

    private final AuditTrailWebInterceptor auditTrailWebInterceptor;

    public AuditTrailWebMvcConfig(AuditTrailWebInterceptor auditTrailWebInterceptor) {
        this.auditTrailWebInterceptor = auditTrailWebInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditTrailWebInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/logout",
                        "/error",
                        "/access-denied",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/webjars/**",
                        "/uploads/**",
                        "/actuator/**",
                        "/favicon.ico"
                );
    }
}
