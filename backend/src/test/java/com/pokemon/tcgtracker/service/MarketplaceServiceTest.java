package com.pokemon.tcgtracker.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.WebDriver;

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
        assertTrue(searchTerms.contains("pokemon etb"));
        assertTrue(searchTerms.contains("pokemon elite trainer box"));
        assertTrue(searchTerms.contains("pokemon booster box"));
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
}