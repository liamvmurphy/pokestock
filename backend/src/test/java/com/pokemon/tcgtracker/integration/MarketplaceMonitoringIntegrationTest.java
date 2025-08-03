package com.pokemon.tcgtracker.integration;

import com.pokemon.tcgtracker.service.FacebookMarketplaceService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.LMStudioService;
import com.pokemon.tcgtracker.service.WebDriverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketplaceMonitoringIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private FacebookMarketplaceService facebookMarketplaceService;

    @MockBean
    private WebDriverService webDriverService;

    @MockBean
    private GoogleSheetsService googleSheetsService;

    @MockBean
    private LMStudioService lmStudioService;

    @BeforeEach
    void setUpMocks() throws Exception {
        // Mock Google Sheets service
        doNothing().when(googleSheetsService).addMarketplaceListing(any());
        when(googleSheetsService.getSpreadsheetUrl()).thenReturn("https://docs.google.com/spreadsheets/d/test/edit");

        // Mock LM Studio service
        Map<String, Object> mockAnalysis = new HashMap<>();
        mockAnalysis.put("itemName", "Pokemon Stellar Crown Booster Box");
        mockAnalysis.put("set", "Stellar Crown");
        mockAnalysis.put("productType", "Booster Box");
        mockAnalysis.put("condition", "New");
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString())).thenReturn(mockAnalysis);
        when(lmStudioService.testConnection()).thenReturn("LM Studio connected successfully");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceStatusEndpoint() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/marketplace/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Ready"))
                .andExpect(jsonPath("$.isRunning").value(false))
                .andExpect(jsonPath("$.searchTerms").isArray())
                .andExpect(jsonPath("$.searchTerms.length()").value(7));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceSearchTermsEndpoint() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/marketplace/search-terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.searchTerms").isArray())
                .andExpect(jsonPath("$.searchTerms[0]").value("pokemon etb"))
                .andExpect(jsonPath("$.searchTerms[1]").value("pokemon elite trainer box"))
                .andExpect(jsonPath("$.searchTerms[2]").value("pokemon booster box"));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testStartMarketplaceMonitoring() throws Exception {
        // Mock WebDriver interactions
        mockWebDriverForSuccessfulScraping();

        // Start monitoring
        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Marketplace monitoring started"))
                .andExpect(jsonPath("$.status").value("running"));

        // Wait for async processing to complete
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(any());
                });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceSearchWithSpecificTerm() throws Exception {
        // Mock WebDriver for search
        mockWebDriverForSuccessfulScraping();

        String requestBody = """
                {
                    "searchTerm": "pokemon stellar crown"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/search")
                        .contentType("application/json")
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Search started for: pokemon stellar crown"))
                .andExpect(jsonPath("$.searchTerm").value("pokemon stellar crown"))
                .andExpect(jsonPath("$.status").value("searching"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceConfigurationEndpoint() throws Exception {
        String configBody = """
                {
                    "enabled": true,
                    "interval": 1800000,
                    "searchTerms": ["pokemon etb", "pokemon elite trainer box", "pokemon booster box"]
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/configure")
                        .contentType("application/json")
                        .content(configBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Configuration updated"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceStopEndpoint() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Marketplace monitoring stopped"))
                .andExpect(jsonPath("$.status").value("stopped"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceHistoryEndpoint() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/marketplace/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listings").isArray())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testEndToEndMarketplaceWorkflow() throws Exception {
        // Mock complete workflow
        mockWebDriverForSuccessfulScraping();

        // 1. Check initial status
        ResponseEntity<Map> statusResponse = restTemplate.getForEntity(
                getApiUrl("/marketplace/status"), Map.class);
        assertEquals(HttpStatus.OK, statusResponse.getStatusCode());
        assertFalse((Boolean) statusResponse.getBody().get("isRunning"));

        // 2. Start monitoring
        ResponseEntity<Map> startResponse = restTemplate.postForEntity(
                getApiUrl("/marketplace/start"), null, Map.class);
        assertEquals(HttpStatus.OK, startResponse.getStatusCode());
        assertEquals("running", startResponse.getBody().get("status"));

        // 3. Wait for processing and verify interactions
        await().atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(any());
                    verify(lmStudioService, atLeastOnce()).analyzeMarketplaceListing(anyString(), anyString());
                });

        // 4. Verify data was processed correctly
        verify(googleSheetsService, times(7)).addMarketplaceListing(argThat(listing -> {
            Map<String, Object> listingMap = (Map<String, Object>) listing;
            return listingMap.containsKey("itemName") && 
                   listingMap.containsKey("price") &&
                   listingMap.containsKey("seller");
        }));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceMonitoringWithError() throws Exception {
        // Mock WebDriver to throw exception
        when(webDriverService.getWebDriver()).thenThrow(new RuntimeException("Chrome not available"));

        // Start monitoring should still respond OK but handle error gracefully
        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Marketplace monitoring started"))
                .andExpect(jsonPath("$.status").value("running"));

        // Verify no data was saved due to error
        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, never()).addMarketplaceListing(any());
                });
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testMarketplaceMonitoringWithLMStudioError() throws Exception {
        // Mock WebDriver success but LM Studio failure
        mockWebDriverForSuccessfulScraping();
        when(lmStudioService.analyzeMarketplaceListing(anyString(), anyString()))
                .thenThrow(new RuntimeException("LM Studio connection failed"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/marketplace/start"))
                .andExpect(status().isOk());

        // Should still save data to sheets even if AI analysis fails
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    verify(googleSheetsService, atLeastOnce()).addMarketplaceListing(any());
                });
    }

    private void mockWebDriverForSuccessfulScraping() {
        // Mock WebDriver and elements for successful scraping
        org.openqa.selenium.WebDriver mockDriver = mock(org.openqa.selenium.WebDriver.class);
        org.openqa.selenium.WebElement mockElement = mock(org.openqa.selenium.WebElement.class);
        org.openqa.selenium.TakesScreenshot mockScreenshot = mock(org.openqa.selenium.TakesScreenshot.class);

        when(webDriverService.getWebDriver()).thenReturn(mockDriver);
        when(mockDriver.getCurrentUrl()).thenReturn("https://www.facebook.com/marketplace/search/");
        when(mockDriver.getPageSource()).thenReturn(createMockPageSource());
        when(mockDriver.findElements(any())).thenReturn(Arrays.asList(mockElement));
        
        // Mock element interactions
        when(mockElement.getAttribute("href")).thenReturn("https://facebook.com/marketplace/item/123456");
        when(mockElement.getText()).thenReturn("Pokemon Stellar Crown Booster Box\n$165\nSeller Name");
        when(mockElement.findElements(any())).thenReturn(Arrays.asList(mockElement));
        
        // Mock screenshot
        when(mockScreenshot.getScreenshotAs(any())).thenReturn("mock-screenshot-data".getBytes());
        when(webDriverService.takeScreenshot(any())).thenReturn("bW9jay1zY3JlZW5zaG90LWRhdGE=");

        doNothing().when(webDriverService).navigateToUrl(any(), anyString());
        doNothing().when(webDriverService).humanLikeScroll(any());
        doNothing().when(webDriverService).closeWebDriver(any());
        when(webDriverService.isBlocked(any())).thenReturn(false);
    }

    private String createMockPageSource() {
        return """
                <html>
                <body>
                    <div data-testid="marketplace-item">
                        <a href="https://facebook.com/marketplace/item/123456">
                            <span>Pokemon Stellar Crown Booster Box</span>
                            <span>$165</span>
                            <span>Great condition</span>
                        </a>
                    </div>
                    <div data-testid="marketplace-item">
                        <a href="https://facebook.com/marketplace/item/789012">
                            <span>Pokemon Paradox Rift Elite Trainer Box</span>
                            <span>$55</span>
                            <span>Brand new</span>
                        </a>
                    </div>
                </body>
                </html>
                """;
    }
}