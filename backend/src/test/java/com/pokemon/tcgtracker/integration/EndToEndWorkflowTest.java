package com.pokemon.tcgtracker.integration;

import com.pokemon.tcgtracker.service.FacebookMarketplaceService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.LMStudioService;
import com.pokemon.tcgtracker.service.WebDriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive end-to-end tests for the complete marketplace monitoring workflow
 */
class EndToEndWorkflowTest extends BaseIntegrationTest {

    @Autowired
    private FacebookMarketplaceService facebookMarketplaceService;

    @MockBean
    private WebDriverService webDriverService;

    @MockBean
    private GoogleSheetsService googleSheetsService;

    @MockBean
    private LMStudioService lmStudioService;

    private WebDriver mockDriver;
    private List<WebElement> mockElements;

    @BeforeEach
    void setUpCompleteWorkflow() throws Exception {
        // Set up comprehensive mocks for the entire workflow
        setupMockWebDriver();
        setupMockGoogleSheets();
        setupMockLMStudio();
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void testCompleteMarketplaceMonitoringWorkflow() throws Exception {
        // Step 1: Verify initial system state
        ResponseEntity<Map> healthResponse = restTemplate.getForEntity(
                getApiUrl("/test/health"), Map.class);
        assertEquals(HttpStatus.OK, healthResponse.getStatusCode());
        
        Map<String, Object> health = healthResponse.getBody();
        assertTrue(health.get("lmStudio").toString().contains("connected"));

        // Step 2: Check marketplace status
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                getApiUrl("/marketplace/status"), Map.class);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        
        Map<String, Object> status = statusResponse.getBody();
        assertEquals("Ready", status.get("status"));
        assertFalse((Boolean) status.get("isRunning"));

        // Step 3: Start marketplace monitoring
        ResponseEntity<Map> startResponse = restTemplate.postForEntity(
                getApiUrl("/marketplace/start"), null, Map.class);
        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        assertEquals("running", startResponse.getBody().get("status"));

        // Step 4: Wait for and verify the complete workflow execution
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Verify WebDriver interactions
                    verify(webDriverService, atLeastOnce()).getWebDriver();
                    verify(webDriverService, atLeastOnce()).navigateToUrl(any(), contains("facebook.com/marketplace"));
                    verify(webDriverService, atLeastOnce()).takeScreenshot(any());
                    verify(webDriverService, atLeastOnce()).closeWebDriver(any());
                });

        // Step 5: Verify data processing workflow
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    // Verify AI analysis was called for each search term
                    verify(lmStudioService, times(7)).analyzeMarketplaceListing(anyString(), anyString());
                    
                    // Verify data was saved to Google Sheets
                    verify(googleSheetsService, times(7)).addMarketplaceListing(any());
                });

        // Step 6: Verify the quality of data being saved
        ArgumentCaptor<Map<String, Object>> listingCaptor = ArgumentCaptor.forClass(Map.class);
        verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(listingCaptor.capture());
        
        List<Map<String, Object>> savedListings = listingCaptor.getAllValues();
        assertFalse(savedListings.isEmpty(), "Should have saved at least one listing");
        
        Map<String, Object> sampleListing = savedListings.get(0);
        assertAll("Saved listing should have all required fields",
            () -> assertNotNull(sampleListing.get("itemName"), "Should have item name"),
            () -> assertNotNull(sampleListing.get("price"), "Should have price"),
            () -> assertNotNull(sampleListing.get("seller"), "Should have seller"),
            () -> assertNotNull(sampleListing.get("marketplaceUrl"), "Should have URL"),
            () -> assertNotNull(sampleListing.get("dateFound"), "Should have date found"),
            () -> assertEquals("Facebook Marketplace", sampleListing.get("source"), "Should have correct source")
        );
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testMarketplaceSearchWithSpecificProductAnalysis() throws Exception {
        // Configure mocks for specific product analysis
        Map<String, Object> detailedAnalysis = new HashMap<>();
        detailedAnalysis.put("itemName", "Pokemon Stellar Crown Booster Box Japanese");
        detailedAnalysis.put("set", "Stellar Crown");
        detailedAnalysis.put("productType", "Booster Box");
        detailedAnalysis.put("condition", "New");
        detailedAnalysis.put("language", "Japanese");
        detailedAnalysis.put("authenticity", "Authentic");
        
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenReturn(detailedAnalysis);

        // Search for specific term
        Map<String, String> searchRequest = Map.of("searchTerm", "pokemon stellar crown");
        ResponseEntity<Map> searchResponse = restTemplate.postForEntity(
                getApiUrl("/marketplace/search"), searchRequest, Map.class);
        
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        assertEquals("searching", searchResponse.getBody().get("status"));

        // Verify detailed analysis workflow
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(captor.capture());
                    
                    Map<String, Object> analyzedListing = captor.getValue();
                    assertEquals("Stellar Crown", analyzedListing.get("set"));
                    assertEquals("Booster Box", analyzedListing.get("productType"));
                    assertEquals("Japanese", analyzedListing.get("language"));
                });
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testErrorHandlingInCompleteWorkflow() throws Exception {
        // Simulate various error conditions
        
        // 1. WebDriver fails initially but recovers
        when(webDriverService.getWebDriver())
                .thenThrow(new RuntimeException("Chrome startup failed"))
                .thenReturn(mockDriver);

        // 2. Some LM Studio calls fail
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenThrow(new RuntimeException("AI analysis timeout"))
                .thenReturn(Map.of("itemName", "Recovered Analysis"));

        // 3. Google Sheets occasionally fails
        doThrow(new RuntimeException("Sheets quota exceeded"))
                .doNothing()
                .when(googleSheetsService).addMarketplaceListing(any());

        // Start monitoring despite errors
        ResponseEntity<Map> response = restTemplate.postForEntity(
                getApiUrl("/marketplace/start"), null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // System should continue operating and eventually succeed
        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(any());
                });
    }

    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    void testMarketplaceDataQualityValidation() throws Exception {
        // Set up mocks to return various data quality scenarios
        setupDataQualityScenarios();

        ResponseEntity<Map> response = restTemplate.postForEntity(
                getApiUrl("/marketplace/start"), null, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(captor.capture());
                    
                    List<Map<String, Object>> allListings = captor.getAllValues();
                    
                    // Verify data quality standards
                    for (Map<String, Object> listing : allListings) {
                        validateListingQuality(listing);
                    }
                });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentMarketplaceOperations() throws Exception {
        // Test multiple concurrent operations
        List<ResponseEntity<Map>> responses = new ArrayList<>();
        
        // Start multiple searches concurrently
        for (int i = 0; i < 3; i++) {
            Map<String, String> request = Map.of("searchTerm", "pokemon tcg " + i);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    getApiUrl("/marketplace/search"), request, Map.class);
            responses.add(response);
        }

        // All should succeed
        for (ResponseEntity<Map> response : responses) {
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("searching", response.getBody().get("status"));
        }

        // Verify system handles concurrent operations correctly
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, atLeast(3)).addMarketplaceListing(any());
                });
    }

    private void setupMockWebDriver() {
        mockDriver = mock(WebDriver.class);
        mockElements = createMockElements();

        when(webDriverService.getWebDriver()).thenReturn(mockDriver);
        when(mockDriver.getCurrentUrl()).thenReturn("https://www.facebook.com/marketplace/search/");
        when(mockDriver.getPageSource()).thenReturn(createDetailedMockPageSource());
        when(mockDriver.findElements(any(By.class))).thenReturn(mockElements);
        when(webDriverService.takeScreenshot(mockDriver)).thenReturn("bW9jay1zY3JlZW5zaG90LWRhdGE=");
        when(webDriverService.isBlocked(mockDriver)).thenReturn(false);

        doNothing().when(webDriverService).navigateToUrl(any(), anyString());
        doNothing().when(webDriverService).humanLikeScroll(any());
        doNothing().when(webDriverService).closeWebDriver(any());
    }

    private void setupMockGoogleSheets() throws Exception {
        doNothing().when(googleSheetsService).addMarketplaceListing(any());
        when(googleSheetsService.getSpreadsheetUrl())
                .thenReturn("https://docs.google.com/spreadsheets/d/test-integration/edit");
    }

    private void setupMockLMStudio() {
        Map<String, Object> aiAnalysis = new HashMap<>();
        aiAnalysis.put("itemName", "Pokemon Stellar Crown Booster Box");
        aiAnalysis.put("set", "Stellar Crown");
        aiAnalysis.put("productType", "Booster Box");
        aiAnalysis.put("condition", "New");
        aiAnalysis.put("confidence", 0.95);

        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenReturn(aiAnalysis);
        when(lmStudioService.testConnection())
                .thenReturn("LM Studio connected successfully");
    }

    private List<WebElement> createMockElements() {
        List<WebElement> elements = new ArrayList<>();
        
        String[] listings = {
            "Pokemon Stellar Crown Booster Box|$165|Premium Seller",
            "Pokemon Paradox Rift Elite Trainer Box|$55|Card Shop",
            "Pokemon 151 Ultra Premium Collection|$149|Collector"
        };

        for (int i = 0; i < listings.length; i++) {
            WebElement element = mock(WebElement.class);
            String[] parts = listings[i].split("\\|");
            
            when(element.getAttribute("href"))
                    .thenReturn("https://facebook.com/marketplace/item/12345" + i);
            when(element.getText())
                    .thenReturn(parts[0] + "\n" + parts[1] + "\n" + parts[2]);
            when(element.findElements(any(By.class)))
                    .thenReturn(Arrays.asList(element));
                    
            elements.add(element);
        }
        
        return elements;
    }

    private String createDetailedMockPageSource() {
        return """
                <html>
                <head><title>Facebook Marketplace</title></head>
                <body>
                    <div id="marketplace-content">
                        <div data-testid="marketplace-item">
                            <a href="https://facebook.com/marketplace/item/123456">
                                <div>
                                    <span>Pokemon Stellar Crown Booster Box</span>
                                    <span>$165</span>
                                    <span>Brand new, sealed</span>
                                    <span>Premium Seller</span>
                                </div>
                            </a>
                        </div>
                        <div data-testid="marketplace-item">
                            <a href="https://facebook.com/marketplace/item/789012">
                                <div>
                                    <span>Pokemon Paradox Rift Elite Trainer Box</span>
                                    <span>$55</span>
                                    <span>Mint condition</span>
                                    <span>Card Shop</span>
                                </div>
                            </a>
                        </div>
                    </div>
                </body>
                </html>
                """;
    }

    private void setupDataQualityScenarios() {
        // Create various data quality scenarios for testing
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenReturn(createHighQualityAnalysis())
                .thenReturn(createPartialAnalysis())
                .thenReturn(createLowConfidenceAnalysis());
    }

    private Map<String, Object> createHighQualityAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("itemName", "Pokemon Stellar Crown Booster Box Japanese");
        analysis.put("set", "Stellar Crown");
        analysis.put("productType", "Booster Box");
        analysis.put("condition", "New");
        analysis.put("confidence", 0.98);
        analysis.put("language", "Japanese");
        analysis.put("authenticity", "Verified Authentic");
        return analysis;
    }

    private Map<String, Object> createPartialAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("itemName", "Pokemon ETB Bundle");
        analysis.put("productType", "Bundle");
        analysis.put("confidence", 0.75);
        return analysis;
    }

    private Map<String, Object> createLowConfidenceAnalysis() {
        Map<String, Object> analysis = new HashMap<>();
        analysis.put("itemName", "Trading Cards");
        analysis.put("confidence", 0.45);
        analysis.put("notes", "Unable to identify specific Pokemon products");
        return analysis;
    }

    private void validateListingQuality(Map<String, Object> listing) {
        // Validate required fields
        assertNotNull(listing.get("itemName"), "Item name is required");
        assertNotNull(listing.get("marketplaceUrl"), "Marketplace URL is required");
        assertNotNull(listing.get("dateFound"), "Date found is required");
        
        // Validate data types
        if (listing.get("price") != null) {
            assertTrue(listing.get("price") instanceof Number, "Price should be numeric");
        }
        
        // Validate URL format
        String url = (String) listing.get("marketplaceUrl");
        if (url != null) {
            assertTrue(url.startsWith("https://facebook.com/marketplace/"), 
                      "URL should be valid Facebook Marketplace URL");
        }
        
        // Validate item name is Pokemon-related
        String itemName = (String) listing.get("itemName");
        if (itemName != null) {
            assertTrue(itemName.toLowerCase().contains("pokemon") || 
                      itemName.toLowerCase().contains("tcg") ||
                      itemName.toLowerCase().contains("cards"),
                      "Item name should be Pokemon TCG related");
        }
    }
}