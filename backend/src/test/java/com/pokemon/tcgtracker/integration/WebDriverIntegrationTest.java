package com.pokemon.tcgtracker.integration;

import com.pokemon.tcgtracker.service.WebDriverService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for WebDriver functionality.
 * These tests require Chrome to be installed and available.
 * Run with -Dwebdriver.tests.enabled=true to enable these tests.
 */
@TestPropertySource(properties = {
    "selenium.headless=true",
    "chrome.user.data.dir=",
    "chrome.debugger.port=9223"
})
class WebDriverIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WebDriverService webDriverService;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "webdriver.tests.enabled", matches = "true")
    void testWebDriverCreation() {
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            assertNotNull(driver, "WebDriver should be created successfully");
            
            // Test basic navigation
            webDriverService.navigateToUrl(driver, "https://www.google.com");
            assertTrue(driver.getCurrentUrl().contains("google.com"), 
                      "Should navigate to Google successfully");
                      
        } catch (Exception e) {
            // If Chrome is not available, skip the test
            org.junit.jupiter.api.Assumptions.assumeFalse(
                e.getMessage().contains("chrome") || e.getMessage().contains("driver"),
                "Chrome/ChromeDriver not available: " + e.getMessage()
            );
            throw e;
        } finally {
            if (driver != null) {
                webDriverService.closeWebDriver(driver);
            }
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "webdriver.tests.enabled", matches = "true")
    void testScreenshotCapture() {
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            webDriverService.navigateToUrl(driver, "https://www.google.com");
            
            String screenshot = webDriverService.takeScreenshot(driver);
            assertNotNull(screenshot, "Screenshot should be captured");
            assertFalse(screenshot.isEmpty(), "Screenshot should not be empty");
            
            // Verify it's base64 encoded
            assertTrue(screenshot.matches("^[A-Za-z0-9+/]*={0,2}$"), 
                      "Screenshot should be valid base64");
                      
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                e.getMessage().contains("chrome") || e.getMessage().contains("driver"),
                "Chrome/ChromeDriver not available: " + e.getMessage()
            );
            throw e;
        } finally {
            if (driver != null) {
                webDriverService.closeWebDriver(driver);
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "webdriver.tests.enabled", matches = "true")
    void testHumanLikeScrolling() {
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            webDriverService.navigateToUrl(driver, "https://example.com");
            
            // This should not throw an exception
            final WebDriver finalDriver = driver;
            assertDoesNotThrow(() -> webDriverService.humanLikeScroll(finalDriver),
                             "Human-like scrolling should not throw exception");
                             
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                e.getMessage().contains("chrome") || e.getMessage().contains("driver"),
                "Chrome/ChromeDriver not available: " + e.getMessage()
            );
            throw e;
        } finally {
            if (driver != null) {
                webDriverService.closeWebDriver(driver);
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @EnabledIfSystemProperty(named = "webdriver.tests.enabled", matches = "true")
    void testBlockDetection() {
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            
            // Test with normal page
            webDriverService.navigateToUrl(driver, "https://example.com");
            assertFalse(webDriverService.isBlocked(driver), 
                       "Should not be blocked on example.com");
                       
        } catch (Exception e) {
            org.junit.jupiter.api.Assumptions.assumeFalse(
                e.getMessage().contains("chrome") || e.getMessage().contains("driver"),
                "Chrome/ChromeDriver not available: " + e.getMessage()
            );
            throw e;
        } finally {
            if (driver != null) {
                webDriverService.closeWebDriver(driver);
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testWebDriverServiceWithoutChrome() {
        // This test should work even when Chrome is not available
        // by handling the exception gracefully
        
        assertThrows(RuntimeException.class, () -> {
            WebDriver driver = webDriverService.getWebDriver();
            // If we get here, Chrome is available, so close the driver
            webDriverService.closeWebDriver(driver);
        }, "Should throw RuntimeException when Chrome is not properly configured");
    }
}