package com.pokemon.tcgtracker.config;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class SeleniumConfig {

    @Value("${selenium.headless:false}")
    private boolean headless;

    @Value("${chrome.user.data.dir:}")
    private String chromeUserDataDir;

    @Value("${chrome.profile.name:Default}")
    private String chromeProfileName;

    @Value("${chrome.debugger.port:9222}")
    private int chromeDebuggerPort;

    @Bean
    @Scope("prototype")
    public WebDriver webDriver() {
        try {
            // ONLY connect to existing Chrome instance - never start a new one
            if (!chromeUserDataDir.isEmpty()) {
                log.info("Attempting to connect to existing Chrome instance on port {}", chromeDebuggerPort);
                
                // When connecting to existing Chrome, use minimal options
                ChromeOptions options = new ChromeOptions();
                
                // ONLY set the debugger address - no other arguments
                options.setExperimentalOption("debuggerAddress", "localhost:" + chromeDebuggerPort);
                
                log.info("Connecting to Chrome DevTools at localhost:{}", chromeDebuggerPort);
                
                WebDriver driver = new ChromeDriver(options);
                log.info("Successfully connected to existing Chrome instance");
                return configureDriver(driver);
                
            } else {
                // If no user data dir specified, throw error instead of starting new Chrome
                log.error("No Chrome user data directory configured. Cannot start WebDriver.");
                throw new RuntimeException("Chrome user data directory must be configured to use existing Chrome session. " +
                    "Please set chrome.user.data.dir property and start Chrome with debugging enabled.");
            }
            
        } catch (Exception e) {
            log.error("Failed to connect to existing Chrome on port {}: {}", chromeDebuggerPort, e.getMessage());
            throw new RuntimeException("Cannot connect to existing Chrome session. Please ensure Chrome is running with: " +
                "chrome.exe --remote-debugging-port=" + chromeDebuggerPort + 
                " --user-data-dir=\"" + chromeUserDataDir + "\"", e);
        }
    }

    private WebDriver configureDriver(WebDriver driver) {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        
        // Set window size to 1152x1152 instead of maximizing
        try {
            org.openqa.selenium.Dimension dimension = new org.openqa.selenium.Dimension(1152, 1152);
            driver.manage().window().setSize(dimension);
            log.info("Set browser window size to 1152x1152");
        } catch (Exception e) {
            log.warn("Could not resize window to 1152x1152, using maximize instead: {}", e.getMessage());
            driver.manage().window().maximize();
        }
        
        // Execute script to remove webdriver detection
        try {
            ((ChromeDriver) driver).executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
        } catch (Exception e) {
            log.warn("Could not execute anti-detection script: {}", e.getMessage());
        }
        
        return driver;
    }
}