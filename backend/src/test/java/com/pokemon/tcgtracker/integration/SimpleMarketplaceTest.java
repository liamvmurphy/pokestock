package com.pokemon.tcgtracker.integration;

import com.pokemon.tcgtracker.service.FacebookMarketplaceService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.LMStudioService;
import com.pokemon.tcgtracker.service.WebDriverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SimpleMarketplaceTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private WebDriverService webDriverService;

    @MockBean
    private GoogleSheetsService googleSheetsService;

    @MockBean
    private LMStudioService lmStudioService;

    @LocalServerPort
    private int port;

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceStatusEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/marketplace/status", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("Ready", body.get("status"));
        assertEquals(false, body.get("isRunning"));
        assertNotNull(body.get("searchTerms"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceSearchTermsEndpoint() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/marketplace/search-terms", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().get("searchTerms"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testStartMarketplaceMonitoring() throws Exception {
        // Mock the services
        doNothing().when(googleSheetsService).addMarketplaceListing(any());
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenReturn(new HashMap<>());

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/marketplace/start", null, Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("Marketplace monitoring started", body.get("message"));
        assertEquals("running", body.get("status"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceSearchEndpoint() {
        Map<String, String> searchRequest = new HashMap<>();
        searchRequest.put("searchTerm", "pokemon stellar crown");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/marketplace/search", 
                searchRequest, Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertTrue(body.get("message").toString().contains("Search started"));
        assertEquals("pokemon stellar crown", body.get("searchTerm"));
        assertEquals("searching", body.get("status"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testHealthEndpoint() {
        // Setup LM Studio mock
        when(lmStudioService.testConnection())
                .thenReturn("LM Studio connected successfully");
        when(googleSheetsService.getSpreadsheetUrl())
                .thenReturn("https://docs.google.com/spreadsheets/d/test/edit");

        ResponseEntity<Map> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/api/test/health", Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("OK", body.get("status"));
        assertNotNull(body.get("lmStudio"));
        assertNotNull(body.get("googleSheets"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceConfigurationEndpoint() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        config.put("interval", 1800000);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/marketplace/configure", 
                config, Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Configuration updated", response.getBody().get("message"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testTestMarketplaceEndpoint() throws Exception {
        // Mock Google Sheets service
        doNothing().when(googleSheetsService).addMarketplaceListing(any());
        when(googleSheetsService.getSpreadsheetUrl())
                .thenReturn("https://docs.google.com/spreadsheets/d/test/edit");

        Map<String, String> testData = new HashMap<>();
        testData.put("itemName", "Test Pokemon Card");
        testData.put("price", "50.00");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "http://localhost:" + port + "/api/test/test-marketplace", 
                testData, Map.class);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Test listing added to Google Sheets", response.getBody().get("message"));
        
        // Verify the service was called
        verify(googleSheetsService, times(1)).addMarketplaceListing(any());
    }
}