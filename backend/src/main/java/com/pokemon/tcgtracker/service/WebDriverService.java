package com.pokemon.tcgtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebDriverService {

    private final ApplicationContext applicationContext;

    /**
     * Get a new WebDriver instance
     */
    public WebDriver getWebDriver() {
        return applicationContext.getBean(WebDriver.class);
    }

    /**
     * Navigate to URL with human-like behavior
     */
    public void navigateToUrl(WebDriver driver, String url) {
        try {
            log.info("Navigating to: {}", url);
            driver.get(url);
            
            // Wait for page to load
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(webDriver -> ((JavascriptExecutor) webDriver)
                            .executeScript("return document.readyState").equals("complete"));
            
            // Random delay to simulate human behavior
            Thread.sleep(ThreadLocalRandom.current().nextInt(600, 1800));  // Reduced by 40% from 1000-3000ms
            
        } catch (Exception e) {
            log.error("Failed to navigate to URL: {}", url, e);
            throw new RuntimeException("Navigation failed", e);
        }
    }

    /**
     * Take a screenshot and return as base64
     */
    public String takeScreenshot(WebDriver driver) {
        try {
            TakesScreenshot takesScreenshot = (TakesScreenshot) driver;
            byte[] screenshot = takesScreenshot.getScreenshotAs(OutputType.BYTES);
            return Base64.getEncoder().encodeToString(screenshot);
        } catch (Exception e) {
            log.error("Failed to take screenshot", e);
            throw new RuntimeException("Screenshot failed", e);
        }
    }

    /**
     * Save screenshot to file and return base64
     */
    public String takeScreenshotToFile(WebDriver driver, String filename) {
        try {
            TakesScreenshot takesScreenshot = (TakesScreenshot) driver;
            File sourceFile = takesScreenshot.getScreenshotAs(OutputType.FILE);
            
            File targetFile = new File("screenshots/" + filename);
            targetFile.getParentFile().mkdirs();
            
            Files.copy(sourceFile.toPath(), targetFile.toPath());
            log.info("Screenshot saved to: {}", targetFile.getAbsolutePath());
            
            return Base64.getEncoder().encodeToString(Files.readAllBytes(targetFile.toPath()));
        } catch (IOException e) {
            log.error("Failed to save screenshot to file: {}", filename, e);
            return takeScreenshot(driver);
        }
    }

    /**
     * Scroll page to simulate human browsing
     */
    public void humanLikeScroll(WebDriver driver) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            
            // Get current scroll position and page height
            Long currentScroll = (Long) js.executeScript("return window.pageYOffset");
            Long pageHeight = (Long) js.executeScript("return document.body.scrollHeight");
            Long viewportHeight = (Long) js.executeScript("return window.innerHeight");
            
            // Determine how much to scroll
            long remainingHeight = pageHeight - currentScroll - viewportHeight;
            
            if (remainingHeight > 0) {
                // Scroll down in natural chunks
                int scrolls = ThreadLocalRandom.current().nextInt(2, 5);
                for (int i = 0; i < scrolls; i++) {
                    int scrollY = ThreadLocalRandom.current().nextInt(300, 600);
                    js.executeScript("window.scrollBy({top: " + scrollY + ", behavior: 'smooth'})");
                    Thread.sleep(ThreadLocalRandom.current().nextInt(200, 400));  // Shorter delays for faster scrolling
                }
            } else {
                // We're at the bottom, do a small scroll up and down to trigger any lazy loading
                js.executeScript("window.scrollBy({top: -200, behavior: 'smooth'})");
                Thread.sleep(300);  // Reduced by 40% from 500ms
                js.executeScript("window.scrollBy({top: 300, behavior: 'smooth'})");
                Thread.sleep(300);  // Reduced by 40% from 500ms
            }
            
        } catch (Exception e) {
            log.warn("Failed to perform human-like scrolling", e);
        }
    }

    /**
     * Wait for element with custom timeout
     */
    public WebElement waitForElement(WebDriver driver, By locator, Duration timeout) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, timeout);
            return wait.until(webDriver -> webDriver.findElement(locator));
        } catch (TimeoutException e) {
            log.warn("Element not found within timeout: {}", locator);
            return null;
        }
    }

    /**
     * Safe click with retry
     */
    public boolean safeClick(WebDriver driver, WebElement element) {
        for (int i = 0; i < 3; i++) {
            try {
                // Scroll element into view
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
                Thread.sleep(300);  // Reduced by 40% from 500ms
                
                // Try normal click first
                element.click();
                return true;
                
            } catch (ElementNotInteractableException e) {
                try {
                    // Try JavaScript click
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                    return true;
                } catch (Exception jsException) {
                    log.warn("Click attempt {} failed, retrying...", i + 1);
                    try {
                        Thread.sleep(600);  // Reduced by 40% from 1000ms
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            } catch (Exception e) {
                log.warn("Unexpected error during click attempt {}: {}", i + 1, e.getMessage());
            }
        }
        return false;
    }

    /**
     * Get page source safely
     */
    public String getPageSource(WebDriver driver) {
        try {
            return driver.getPageSource();
        } catch (Exception e) {
            log.error("Failed to get page source", e);
            return "";
        }
    }

    /**
     * Close WebDriver safely
     */
    public void closeWebDriver(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
                log.info("WebDriver closed successfully");
            } catch (Exception e) {
                log.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
    }

    /**
     * Add random delay to simulate human behavior
     */
    public void humanDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(600, 1800));  // Reduced by 40% from 1000-3000ms
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Check if we're being blocked or rate limited
     */
    public boolean isBlocked(WebDriver driver) {
        String pageSource = driver.getPageSource().toLowerCase();
        String title = driver.getTitle().toLowerCase();
        
        // More specific blocking patterns to avoid false positives
        boolean blocked = pageSource.contains("you are blocked") || 
                         pageSource.contains("access denied") ||
                         pageSource.contains("temporarily blocked");
        boolean rateLimit = pageSource.contains("rate limit") || 
                           pageSource.contains("too many requests");
        boolean captcha = pageSource.contains("please complete the captcha") ||
                         pageSource.contains("security check required") ||
                         pageSource.contains("verify that you're human");
        boolean tryAgain = pageSource.contains("please try again later");
        boolean errorTitle = title.contains("error") || title.contains("blocked");
        
        boolean isBlocked = blocked || rateLimit || captcha || tryAgain || errorTitle;
        
        log.info("🚫 Block check result: {} (blocked: {}, rate: {}, captcha: {}, tryAgain: {}, errorTitle: {})", 
                isBlocked, blocked, rateLimit, captcha, tryAgain, errorTitle);
        
        return isBlocked;
    }

    /**
     * Open a new tab in the current browser session
     */
    public String openNewTab(WebDriver driver) {
        try {
            // Store current window handle and count
            String originalWindow = driver.getWindowHandle();
            Set<String> originalHandles = driver.getWindowHandles();
            int originalCount = originalHandles.size();
            
            log.debug("Current window handles: {}, count: {}", originalHandles, originalCount);
            
            // Try multiple methods to open a new tab
            boolean tabOpened = false;
            
            // Method 1: JavaScript window.open
            try {
                ((JavascriptExecutor) driver).executeScript("window.open('about:blank','_blank');");
                Thread.sleep(600);  // Reduced by 40% from 1000ms
                
                Set<String> newHandles = driver.getWindowHandles();
                if (newHandles.size() > originalCount) {
                    tabOpened = true;
                    log.debug("Method 1 (window.open) succeeded");
                }
            } catch (Exception e) {
                log.debug("Method 1 (window.open) failed: {}", e.getMessage());
            }
            
            // Method 2: Ctrl+T simulation if Method 1 failed
            if (!tabOpened) {
                try {
                    driver.findElement(By.tagName("body")).sendKeys(Keys.CONTROL + "t");
                    Thread.sleep(600);  // Reduced by 40% from 1000ms
                    
                    Set<String> newHandles = driver.getWindowHandles();
                    if (newHandles.size() > originalCount) {
                        tabOpened = true;
                        log.debug("Method 2 (Ctrl+T) succeeded");
                    }
                } catch (Exception e) {
                    log.debug("Method 2 (Ctrl+T) failed: {}", e.getMessage());
                }
            }
            
            // Method 3: Alternative JavaScript approach
            if (!tabOpened) {
                try {
                    ((JavascriptExecutor) driver).executeScript(
                        "var newTab = window.open(); newTab.document.write('<html><body>Loading...</body></html>');"
                    );
                    Thread.sleep(600);  // Reduced by 40% from 1000ms
                    
                    Set<String> newHandles = driver.getWindowHandles();
                    if (newHandles.size() > originalCount) {
                        tabOpened = true;
                        log.debug("Method 3 (alternative JS) succeeded");
                    }
                } catch (Exception e) {
                    log.debug("Method 3 (alternative JS) failed: {}", e.getMessage());
                }
            }
            
            if (!tabOpened) {
                log.error("All tab opening methods failed");
                return null;
            }
            
            // Wait a bit more for the tab to fully initialize
            Thread.sleep(500);
            
            // Get all window handles and find the new one
            Set<String> windowHandles = driver.getWindowHandles();
            log.debug("New window handles: {}, count: {}", windowHandles, windowHandles.size());
            
            // Find the new window handle
            String newWindow = null;
            for (String windowHandle : windowHandles) {
                if (!originalHandles.contains(windowHandle)) {
                    newWindow = windowHandle;
                    break;
                }
            }
            
            if (newWindow != null) {
                log.info("Successfully opened new tab with handle: {}", newWindow);
                return newWindow;
            } else {
                log.error("Failed to identify new tab handle");
                return null;
            }
            
        } catch (Exception e) {
            log.error("Failed to open new tab", e);
            return null;
        }
    }

    /**
     * Open a new tab and navigate to a URL
     */
    public boolean openNewTabWithUrl(WebDriver driver, String url) {
        try {
            log.info("Opening new tab and navigating to: {}", url);
            
            // Method 1: Try to open tab directly with URL
            try {
                ((JavascriptExecutor) driver).executeScript("window.open(arguments[0], '_blank');", url);
                Thread.sleep(1200);  // Reduced by 40% from 2000ms
                
                // Switch to the new tab
                Set<String> handles = driver.getWindowHandles();
                String newHandle = null;
                for (String handle : handles) {
                    driver.switchTo().window(handle);
                    if (driver.getCurrentUrl().equals(url) || driver.getCurrentUrl().contains("/marketplace/item/")) {
                        newHandle = handle;
                        break;
                    }
                }
                
                if (newHandle != null) {
                    log.info("Successfully opened tab with URL using Method 1");
                    return true;
                }
            } catch (Exception e) {
                log.debug("Method 1 (direct URL open) failed: {}", e.getMessage());
            }
            
            // Method 2: Open blank tab then navigate
            String newTabHandle = openNewTab(driver);
            if (newTabHandle != null) {
                driver.switchTo().window(newTabHandle);
                
                // Use JavaScript navigation instead of driver.get()
                try {
                    ((JavascriptExecutor) driver).executeScript("window.location.href = arguments[0];", url);
                    Thread.sleep(1800);  // Reduced by 40% from 3000ms
                    
                    String currentUrl = driver.getCurrentUrl();
                    log.info("After JS navigation, current URL: {}", currentUrl);
                    
                    if (currentUrl.contains("/marketplace/item/")) {
                        log.info("Successfully navigated using JavaScript");
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("JavaScript navigation failed: {}", e.getMessage());
                }
                
                // Method 3: Standard navigation
                try {
                    navigateToUrl(driver, url);
                    return true;
                } catch (Exception e) {
                    log.debug("Standard navigation failed: {}", e.getMessage());
                }
            }
            
            return false;
        } catch (Exception e) {
            log.error("Failed to open new tab with URL: {}", url, e);
            return false;
        }
    }

    /**
     * Switch to a specific tab by window handle
     */
    public boolean switchToTab(WebDriver driver, String windowHandle) {
        try {
            driver.switchTo().window(windowHandle);
            log.info("Switched to tab: {}", windowHandle);
            return true;
        } catch (Exception e) {
            log.error("Failed to switch to tab: {}", windowHandle, e);
            return false;
        }
    }

    /**
     * Close current tab and switch to another
     */
    public boolean closeCurrentTab(WebDriver driver, String switchToHandle) {
        try {
            String currentHandle = driver.getWindowHandle();
            
            // Don't close if it's the only tab
            if (driver.getWindowHandles().size() <= 1) {
                log.warn("Cannot close the only remaining tab");
                return false;
            }
            
            driver.close();
            log.info("Closed tab: {}", currentHandle);
            
            // Switch to specified tab
            if (switchToHandle != null) {
                return switchToTab(driver, switchToHandle);
            }
            
            // Or switch to any remaining tab
            String remainingTab = driver.getWindowHandles().iterator().next();
            return switchToTab(driver, remainingTab);
            
        } catch (Exception e) {
            log.error("Failed to close current tab", e);
            return false;
        }
    }

    /**
     * Get all open tab handles
     */
    public Set<String> getAllTabHandles(WebDriver driver) {
        return driver.getWindowHandles();
    }

    /**
     * Get current tab handle
     */
    public String getCurrentTabHandle(WebDriver driver) {
        return driver.getWindowHandle();
    }
}