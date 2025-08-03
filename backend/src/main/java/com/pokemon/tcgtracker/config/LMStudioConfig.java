package com.pokemon.tcgtracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class LMStudioConfig {

    @Value("${lmstudio.api.url}")
    private String apiUrl;

    @Bean(name = "lmStudioRestTemplate")
    public RestTemplate lmStudioRestTemplate(RestTemplateBuilder builder) {
        return builder
                .rootUri(apiUrl)
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofMinutes(5)) // Vision models can be slow
                .build();
    }
    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}