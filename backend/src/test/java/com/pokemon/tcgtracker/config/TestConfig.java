package com.pokemon.tcgtracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.LMStudioService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestConfiguration
@Profile("test")
public class TestConfig {

    @Bean
    @Primary
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @Primary
    public GoogleSheetsService mockGoogleSheetsService() throws Exception {
        GoogleSheetsService mock = mock(GoogleSheetsService.class);
        
        // Configure default behavior
        doNothing().when(mock).addMarketplaceListing(any());
        when(mock.getSpreadsheetUrl())
                .thenReturn("https://docs.google.com/spreadsheets/d/test-sheet/edit");
        
        return mock;
    }

    @Bean
    @Primary
    public LMStudioService mockLMStudioService() {
        LMStudioService mock = mock(LMStudioService.class);
        
        // Configure default successful responses
        Map<String, Object> defaultAnalysis = new HashMap<>();
        defaultAnalysis.put("itemName", "Pokemon TCG Product");
        defaultAnalysis.put("set", "Unknown");
        defaultAnalysis.put("productType", "Cards");
        defaultAnalysis.put("condition", "Unknown");
        defaultAnalysis.put("confidence", 0.8);
        
        when(mock.analyzeMarketplaceListing(anyString(), anyString()))
                .thenReturn(defaultAnalysis);
        when(mock.testConnection())
                .thenReturn("LM Studio connected successfully. Test mode active.");
        
        return mock;
    }
}