package com.pokemon.tcgtracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock
    private WebDriverService webDriverService;

    @Mock
    private LMStudioService lmStudioService;

    @Mock
    private GoogleSheetsService googleSheetsService;

    @InjectMocks
    private FacebookMarketplaceService facebookMarketplaceService;

    @BeforeEach
    void setUp() throws Exception {
        // Reset all mocks before each test
        reset(webDriverService, lmStudioService, googleSheetsService);
    }

    @Test
    void testGetMonitoringStatus() {
        Map<String, Object> status = facebookMarketplaceService.getMonitoringStatus();
        
        assertNotNull(status);
        assertFalse((Boolean) status.get("isRunning"));
        assertEquals("Ready", status.get("status"));
        assertNotNull(status.get("searchTerms"));
        assertNotNull(status.get("lastRun"));
    }

    @Test
    void testMonitoringStatusHasCorrectSearchTerms() {
        Map<String, Object> status = facebookMarketplaceService.getMonitoringStatus();
        
        @SuppressWarnings("unchecked")
        java.util.List<String> searchTerms = (java.util.List<String>) status.get("searchTerms");
        
        assertNotNull(searchTerms);
        assertEquals(3, searchTerms.size());
        assertTrue(searchTerms.contains("Pokemon ETB"));
        assertTrue(searchTerms.contains("Pokemon Elite Trainer Box"));
        assertTrue(searchTerms.contains("Pokemon Booster Box"));
    }

    @Test
    void testStartMarketplaceMonitoringCallsServices() throws Exception {
        // Setup mocks for this specific test using lenient stubbing
        WebDriver mockDriver = mock(WebDriver.class);
        lenient().when(webDriverService.getWebDriver()).thenReturn(mockDriver);
        lenient().doNothing().when(webDriverService).closeWebDriver(any());
        lenient().doNothing().when(webDriverService).navigateToUrl(any(), anyString());
        lenient().doNothing().when(webDriverService).humanDelay();
        lenient().when(webDriverService.isBlocked(any())).thenReturn(false);
        lenient().doNothing().when(webDriverService).humanLikeScroll(any());
        
        // This should not throw an exception  
        assertDoesNotThrow(() -> {
            facebookMarketplaceService.startMarketplaceMonitoring();
        });
        
        // Verify the services were called
        verify(webDriverService, times(1)).getWebDriver();
        verify(webDriverService, times(1)).closeWebDriver(mockDriver);
    }

    @Test
    void testCleanMarketplaceUrl() throws Exception {
        // Use reflection to access the private method
        Method cleanUrlMethod = FacebookMarketplaceService.class.getDeclaredMethod("cleanMarketplaceUrl", String.class);
        cleanUrlMethod.setAccessible(true);
        
        // Test URL with query parameters
        String urlWithQuery = "https://www.facebook.com/marketplace/item/123456789?ref=messenger_banner&extra=param";
        String cleanedUrl = (String) cleanUrlMethod.invoke(facebookMarketplaceService, urlWithQuery);
        assertEquals("https://www.facebook.com/marketplace/item/123456789", cleanedUrl);
        
        // Test URL with fragment
        String urlWithFragment = "https://www.facebook.com/marketplace/item/123456789#section";
        String cleanedFragment = (String) cleanUrlMethod.invoke(facebookMarketplaceService, urlWithFragment);
        assertEquals("https://www.facebook.com/marketplace/item/123456789", cleanedFragment);
        
        // Test URL with both query and fragment
        String urlWithBoth = "https://www.facebook.com/marketplace/item/123456789?ref=test#section";
        String cleanedBoth = (String) cleanUrlMethod.invoke(facebookMarketplaceService, urlWithBoth);
        assertEquals("https://www.facebook.com/marketplace/item/123456789", cleanedBoth);
        
        // Test clean URL (no changes needed)
        String cleanUrl = "https://www.facebook.com/marketplace/item/123456789";
        String unchangedUrl = (String) cleanUrlMethod.invoke(facebookMarketplaceService, cleanUrl);
        assertEquals("https://www.facebook.com/marketplace/item/123456789", unchangedUrl);
        
        // Test null and empty URLs
        String nullResult = (String) cleanUrlMethod.invoke(facebookMarketplaceService, (String) null);
        assertNull(nullResult);
        
        String emptyResult = (String) cleanUrlMethod.invoke(facebookMarketplaceService, "");
        assertEquals("", emptyResult);
    }
}