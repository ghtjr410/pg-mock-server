package com.pgmock.common.chaos;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ChaosProperties.class)
public class ChaosAutoConfiguration implements WebMvcConfigurer {

    private final ChaosProperties chaosProperties;

    public ChaosAutoConfiguration(ChaosProperties chaosProperties) {
        this.chaosProperties = chaosProperties;
    }

    @Bean
    public ChaosInterceptor chaosInterceptor() {
        return new ChaosInterceptor(chaosProperties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(chaosInterceptor()).addPathPatterns("/**");
    }

    @Bean
    public ChaosController chaosController() {
        return new ChaosController(chaosProperties);
    }
}
