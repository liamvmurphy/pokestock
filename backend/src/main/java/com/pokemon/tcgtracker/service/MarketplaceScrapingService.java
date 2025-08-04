package com.pokemon.tcgtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketplaceScrapingService {

    private final WebDriverService webDriverService;
    private final ObjectMapper objectMapper;
    private final LMStudioService lmStudioService;
    private final ConfigurationService configurationService;
    private final GoogleSheetsService googleSheetsService;
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * Scrape Facebook Marketplace with scalable tab-based approach
     */
    public List<Map<String, Object>> scrapeMarketplaceItems(String searchTerm, int maxItems) {
        WebDriver driver = null;
        List<Map<String, Object>> results = new ArrayList<>();
        String originalTab = null;
        
        try {
            driver = webDriverService.getWebDriver();
            originalTab = webDriverService.getCurrentTabHandle(driver);
            
            log.info("Starting scalable marketplace scraping for: {} (max {} items)", searchTerm, maxItems);
            
            // Step 1: Navigate to search results and collect URLs
            List<String> itemUrls = collectItemUrls(driver, searchTerm, maxItems);
            log.info("Collected {} item URLs", itemUrls.size());
            
            if (itemUrls.isEmpty()) {
                return results;
            }
            
            // Step 2: Process items in batches using tabs
            results = processItemsInParallel(driver, itemUrls, originalTab);
            
            // Step 3: Save to local JSON file
            saveToLocalJson(results, searchTerm);
            
            // Step 4: Save to Google Sheets
            saveToGoogleSheets(results);
            
            return results;
            
        } catch (Exception e) {
            log.error("Failed to scrape marketplace items", e);
            return results;
        } finally {
            // Ensure we're back on the original tab
            if (driver != null && originalTab != null) {
                try {
                    webDriverService.switchToTab(driver, originalTab);
                } catch (Exception e) {
                    log.warn("Could not switch back to original tab", e);
                }
            }
        }
    }

    /**
     * Collect item URLs from search results (fast, single page)
     */
    private List<String> collectItemUrls(WebDriver driver, String searchTerm, int maxItems) {
        List<String> urls = new ArrayList<>();
        
        try {
            // Navigate to search results
            String searchUrl = buildSearchUrl(searchTerm);
            webDriverService.navigateToUrl(driver, searchUrl);
            
            // Wait for results to load
            Thread.sleep(5000);
            
            // Scroll to load more items
            webDriverService.humanLikeScroll(driver);
            Thread.sleep(3000);
            
            // Use JavaScript to find marketplace item links more reliably
            String jsScript = """
                return Array.from(document.querySelectorAll('a[href*="/marketplace/item/"]'))
                    .map(a => a.href)
                    .filter(href => href && href.includes('/marketplace/item/'))
                    .slice(0, %d);
                """.formatted(maxItems);
            
            @SuppressWarnings("unchecked")
            List<String> foundUrls = (List<String>) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsScript);
            
            if (foundUrls != null && !foundUrls.isEmpty()) {
                urls.addAll(foundUrls);
                log.info("JavaScript method found {} URLs", urls.size());
            } else {
                // Fallback to Selenium element finding
                log.info("JavaScript method found no URLs, trying Selenium fallback");
                List<WebElement> listingElements = findListingElements(driver);
                
                for (WebElement element : listingElements) {
                    if (urls.size() >= maxItems) break;
                    
                    try {
                        String url = element.getAttribute("href");
                        if (url != null && url.contains("/marketplace/item/") && !urls.contains(url)) {
                            urls.add(url);
                            log.debug("Found URL via Selenium: {}", url);
                        }
                    } catch (Exception e) {
                        log.debug("Could not extract URL from element: {}", e.getMessage());
                    }
                }
            }
            
            // Log the actual URLs found
            for (int i = 0; i < urls.size(); i++) {
                log.info("URL {}: {}", i + 1, urls.get(i));
            }
            
        } catch (Exception e) {
            log.error("Failed to collect item URLs", e);
        }
        
        return urls;
    }

    /**
     * Process items in parallel using multiple tabs
     */
    private List<Map<String, Object>> processItemsInParallel(WebDriver driver, List<String> itemUrls, String originalTab) {
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());
        List<Future<Void>> futures = new ArrayList<>();
        
        // Facebook is blocking tab navigation, so process sequentially
        log.info("Processing items sequentially to avoid Facebook blocking");
        return processSequentially(driver, itemUrls, originalTab, results);
    }

    /**
     * Test if we can create tabs
     */
    private boolean testTabCreation(WebDriver driver, String originalTab) {
        try {
            String testTab = webDriverService.openNewTab(driver);
            if (testTab != null) {
                // Successfully created tab, close it
                webDriverService.switchToTab(driver, testTab);
                driver.close();
                webDriverService.switchToTab(driver, originalTab);
                return true;
            }
        } catch (Exception e) {
            log.debug("Tab creation test failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Process items using tabs (parallel)
     */
    private List<Map<String, Object>> processWithTabs(WebDriver driver, List<String> itemUrls, String originalTab, 
                                                      List<Map<String, Object>> results, List<Future<Void>> futures) {
        // Process in batches of 3 tabs at a time (reduced from 5 for stability)
        int batchSize = 3;
        for (int i = 0; i < itemUrls.size(); i += batchSize) {
            List<String> batch = itemUrls.subList(i, Math.min(i + batchSize, itemUrls.size()));
            
            // Process current batch
            List<String> tabHandles = new ArrayList<>();
            
            // Open tabs for this batch
            for (String url : batch) {
                try {
                    log.info("Opening tab for URL: {}", url);
                    
                    // Use the improved tab opening method
                    boolean success = webDriverService.openNewTabWithUrl(driver, url);
                    
                    if (success) {
                        String currentHandle = webDriverService.getCurrentTabHandle(driver);
                        tabHandles.add(currentHandle);
                        
                        // Verify the URL loaded correctly
                        String currentUrl = driver.getCurrentUrl();
                        log.info("Successfully opened tab. Current URL: {}", currentUrl);
                        
                        if (!currentUrl.contains("/marketplace/item/")) {
                            log.warn("Navigation may have failed. Expected marketplace item URL, got: {}", currentUrl);
                        }
                    } else {
                        log.warn("Failed to open tab for URL: {}, will skip", url);
                    }
                } catch (Exception e) {
                    log.warn("Failed to open tab for URL: {}", url, e);
                }
            }
            
            // Extract data from all tabs in this batch
            for (int j = 0; j < tabHandles.size(); j++) {
                String tabHandle = tabHandles.get(j);
                String url = batch.get(j);
                
                Future<Void> future = executorService.submit(() -> {
                    try {
                        webDriverService.switchToTab(driver, tabHandle);
                        Thread.sleep(2500); // Reduced from 5000ms
                        
                        Map<String, Object> itemData = extractItemDetails(driver, url);
                        if (itemData != null && !itemData.isEmpty()) {
                            results.add(itemData);
                            log.info("Extracted item: {}", itemData.get("title"));
                        }
                    } catch (Exception e) {
                        log.warn("Failed to extract data from URL: {}", url, e);
                    }
                    return null;
                });
                
                futures.add(future);
            }
            
            // Wait for batch to complete (30 seconds per page)
            for (Future<Void> future : futures) {
                try {
                    future.get(150, TimeUnit.SECONDS); // 30 seconds per page * 5 pages
                } catch (Exception e) {
                    log.warn("Batch processing timeout or error", e);
                }
            }
            futures.clear();
            
            // Close batch tabs
            for (String tabHandle : tabHandles) {
                try {
                    webDriverService.switchToTab(driver, tabHandle);
                    driver.close();
                } catch (Exception e) {
                    log.warn("Failed to close tab: {}", tabHandle);
                }
            }
            
            // Brief pause between batches
            try {
                Thread.sleep(1000); // Reduced from 2000ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return new ArrayList<>(results);
    }

    /**
     * Process items sequentially (more reliable than tabs for Facebook)
     */
    private List<Map<String, Object>> processSequentially(WebDriver driver, List<String> itemUrls, String originalTab, 
                                                          List<Map<String, Object>> results) {
        for (int i = 0; i < itemUrls.size(); i++) {
            String url = itemUrls.get(i);
            try {
                log.info("Processing item {}/{}: {}", i + 1, itemUrls.size(), url);
                
                // Try multiple navigation methods
                boolean navigationSuccess = false;
                
                // Method 1: Direct navigation
                try {
                    driver.get(url);
                    Thread.sleep(1500); // Reduced from 3000ms
                    
                    String currentUrl = driver.getCurrentUrl();
                    if (currentUrl.contains("/marketplace/item/")) {
                        navigationSuccess = true;
                        log.info("Direct navigation successful to: {}", currentUrl);
                    } else {
                        log.warn("Direct navigation redirected to: {}", currentUrl);
                    }
                } catch (Exception e) {
                    log.debug("Direct navigation failed: {}", e.getMessage());
                }
                
                // Method 2: JavaScript navigation if direct failed
                if (!navigationSuccess) {
                    try {
                        ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.location.href = arguments[0];", url);
                        Thread.sleep(4000);
                        
                        String currentUrl = driver.getCurrentUrl();
                        if (currentUrl.contains("/marketplace/item/")) {
                            navigationSuccess = true;
                            log.info("JavaScript navigation successful to: {}", currentUrl);
                        } else {
                            log.warn("JavaScript navigation redirected to: {}", currentUrl);
                        }
                    } catch (Exception e) {
                        log.debug("JavaScript navigation failed: {}", e.getMessage());
                    }
                }
                
                // Method 3: Try clicking from search results if navigation fails
                if (!navigationSuccess) {
                    log.warn("Both navigation methods failed for URL: {}", url);
                    log.info("Trying to click from search results instead");
                    
                    // Go back to search results and try to click the item
                    try {
                        driver.navigate().back();
                        Thread.sleep(2000);
                        
                        // Find and click the specific link
                        String jsClick = String.format(
                            "var links = Array.from(document.querySelectorAll('a[href*=\"/marketplace/item/\"]')); " +
                            "var targetLink = links.find(link => link.href === '%s'); " +
                            "if (targetLink) { targetLink.click(); return true; } else return false;", url);
                        
                        Boolean clickSuccess = (Boolean) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsClick);
                        if (Boolean.TRUE.equals(clickSuccess)) {
                            Thread.sleep(4000);
                            String currentUrl = driver.getCurrentUrl();
                            if (currentUrl.contains("/marketplace/item/")) {
                                navigationSuccess = true;
                                log.info("Click navigation successful to: {}", currentUrl);
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Click navigation failed: {}", e.getMessage());
                    }
                }
                
                if (navigationSuccess) {
                    // Wait for page to fully load
                    Thread.sleep(1250); // Reduced from 2500ms
                    
                    // Extract item details
                    Map<String, Object> itemData = extractItemDetails(driver, url);
                    if (itemData != null && !itemData.isEmpty()) {
                        results.add(itemData);
                        String title = itemData.containsKey("listingTitle") ? 
                            (String) itemData.get("listingTitle") : 
                            (String) itemData.getOrDefault("title", "Unknown");
                        log.info("Successfully extracted item {}: {}", i + 1, title);
                    } else {
                        log.warn("Failed to extract data from item {}", i + 1);
                    }
                } else {
                    log.error("All navigation methods failed for URL: {}", url);
                }
                
                // Pause between items to be respectful
                Thread.sleep(750); // Reduced from 1500ms
                
            } catch (Exception e) {
                log.error("Failed to process item {}: {}", i + 1, e.getMessage(), e);
            }
        }
        
        return new ArrayList<>(results);
    }

    /**
     * Extract detailed information from a single item page using screenshot + LM Studio
     */
    private Map<String, Object> extractItemDetails(WebDriver driver, String url) {
        Map<String, Object> item = new HashMap<>();
        
        try {
            // Basic info
            item.put("url", url);
            item.put("scrapedAt", LocalDateTime.now().toString());
            
            // Wait for page to be fully loaded
            Thread.sleep(1500); // Reduced from 3000ms
            
            // Try to expand "See more" sections first
            expandSeeMoreSections(driver);
            
            // Wait a bit more for content to fully expand
            Thread.sleep(1000); // Reduced from 2000ms
            
            // Take screenshot and save to temporary file
            String screenshotPath = capturePageScreenshot(driver, url);
            item.put("screenshotPath", screenshotPath);
            
            // Send screenshot to LM Studio for analysis
            Map<String, Object> aiAnalysis = analyzeScreenshotWithLMStudio(screenshotPath, url);
            
            // Merge AI analysis results into item data
            if (aiAnalysis != null && !aiAnalysis.isEmpty()) {
                // Put ALL fields from the LM Studio analysis into the item data
                item.putAll(aiAnalysis);
                
                // Check if we have items array
                if (aiAnalysis.containsKey("items") && aiAnalysis.get("items") instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) aiAnalysis.get("items");
                    log.info("Successfully extracted {} individual items from listing via LM Studio analysis", items.size());
                    
                    // If there are items, use the first item's data for main listing fields
                    if (!items.isEmpty()) {
                        Map<String, Object> firstItem = items.get(0);
                        item.put("itemName", firstItem.get("itemName"));
                        item.put("set", firstItem.get("set"));
                        
                        // Ensure productType is never empty
                        String productType = (String) firstItem.get("productType");
                        if (productType == null || productType.trim().isEmpty()) {
                            productType = "OTHER";
                        }
                        item.put("productType", productType);
                        
                        item.put("price", firstItem.get("price"));
                        item.put("quantity", firstItem.get("quantity"));
                        item.put("priceUnit", firstItem.get("priceUnit"));
                        item.put("notes", firstItem.get("notes"));
                    }
                } else {
                    log.info("Successfully extracted data via LM Studio analysis (single item format)");
                }
            } else {
                log.warn("LM Studio analysis returned no data");
                // Add fallback basic data (only fields we want to populate)
                item.put("itemName", "Analysis failed");
                item.put("set", "");
                item.put("productType", "OTHER"); // Always set to "OTHER" as fallback
                item.put("price", "");
                item.put("quantity", 1); // Always integer
                item.put("priceUnit", "");
                item.put("mainListingPrice", "Not extracted");
                item.put("location", "");
                item.put("hasMultipleItems", false);
                item.put("notes", "Screenshot analysis failed");
            }
            
        } catch (Exception e) {
            log.warn("Failed to extract item details from: {}", url, e);
        }
        
        return item;
    }

    /**
     * Capture screenshot and save to temporary file
     */
    private String capturePageScreenshot(WebDriver driver, String url) {
        try {
            // Create temp directory if it doesn't exist
            Path tempDir = Paths.get("temp", "screenshots");
            Files.createDirectories(tempDir);
            
            // Generate unique filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
            String urlId = url.replaceAll(".*/marketplace/item/([^/?]+).*", "$1");
            String filename = String.format("marketplace_%s_%s.png", urlId, timestamp);
            Path screenshotPath = tempDir.resolve(filename);
            
            // Take screenshot
            TakesScreenshot takesScreenshot = (TakesScreenshot) driver;
            byte[] screenshotBytes = takesScreenshot.getScreenshotAs(OutputType.BYTES);
            
            // Save to file
            Files.write(screenshotPath, screenshotBytes);
            
            log.info("Screenshot saved to: {}", screenshotPath.toAbsolutePath());
            return screenshotPath.toAbsolutePath().toString();
            
        } catch (Exception e) {
            log.error("Failed to capture screenshot", e);
            return null;
        }
    }

    /**
     * Send screenshot to LM Studio for analysis
     */
    private Map<String, Object> analyzeScreenshotWithLMStudio(String screenshotPath, String url) {
        try {
            if (screenshotPath == null) {
                return new HashMap<>();
            }
            
            // Convert screenshot to base64
            byte[] imageBytes = Files.readAllBytes(Paths.get(screenshotPath));
            String base64Image = "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
            
            // Create description for LM Studio
            String description = String.format(
                "Facebook Marketplace listing from URL: %s. Please analyze this screenshot and extract the following information in JSON format.", 
                url
            );
            
            // Send to LM Studio service
            log.info("Sending screenshot to LM Studio for analysis...");
            // Get model from configuration or use default
            String model = getConfiguredModel();
            Map<String, Object> analysis = lmStudioService.analyzeMarketplaceListing(description, base64Image, model);
            
            if (analysis != null && !analysis.containsKey("error")) {
                log.info("LM Studio analysis completed successfully");
                return analysis;
            } else {
                log.warn("LM Studio analysis failed: {}", analysis != null ? analysis.get("error") : "Unknown error");
                return new HashMap<>();
            }
            
        } catch (Exception e) {
            log.error("Failed to analyze screenshot with LM Studio", e);
            return new HashMap<>();
        }
    }

    /**
     * Expand "See more" sections to reveal full content
     */
    private void expandSeeMoreSections(WebDriver driver) {
        try {
            log.info("Looking for 'See more' buttons to expand content...");
            
            // Method 1: Use JavaScript to find and click all "See more" elements
            String jsScript = """
                var seeMoreElements = Array.from(document.querySelectorAll('*'))
                    .filter(el => el.textContent && 
                            (el.textContent.trim() === 'See more' || 
                             el.textContent.includes('See more')) &&
                            el.offsetWidth > 0 && el.offsetHeight > 0);
                
                var clicked = 0;
                seeMoreElements.forEach(function(element) {
                    try {
                        if (element.click) {
                            element.click();
                            clicked++;
                        }
                    } catch (e) {
                        console.log('Could not click element:', e);
                    }
                });
                
                return clicked;
                """;
            
            Long clickedCount = (Long) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsScript);
            
            if (clickedCount != null && clickedCount > 0) {
                log.info("JavaScript method clicked {} 'See more' elements", clickedCount);
                Thread.sleep(2000); // Wait for content to expand
                return;
            }
            
            // Method 2: Find elements by common Facebook "See more" classes and text
            log.debug("JavaScript method found no elements, trying Selenium approach");
            
            String[] patterns = {
                "See more",
                "see more", 
                "See More",
                "SEE MORE"
            };
            
            for (String pattern : patterns) {
                try {
                    // Look for spans, divs, or buttons containing the text
                    List<WebElement> elements = driver.findElements(By.xpath(
                        String.format("//span[contains(text(), '%s')] | //div[contains(text(), '%s')] | //button[contains(text(), '%s')]", 
                        pattern, pattern, pattern)));
                    
                    for (WebElement element : elements) {
                        try {
                            if (element.isDisplayed() && element.isEnabled()) {
                                String elementText = element.getText().trim();
                                if (elementText.equals(pattern) || elementText.contains(pattern)) {
                                    log.info("Found 'See more' element with text: '{}'", elementText);
                                    
                                    // Try different click methods
                                    boolean clicked = false;
                                    
                                    // Try normal click
                                    try {
                                        element.click();
                                        clicked = true;
                                        log.info("Successfully clicked 'See more' with normal click");
                                    } catch (Exception e) {
                                        log.debug("Normal click failed: {}", e.getMessage());
                                    }
                                    
                                    // Try JavaScript click
                                    if (!clicked) {
                                        try {
                                            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
                                            clicked = true;
                                            log.info("Successfully clicked 'See more' with JavaScript click");
                                        } catch (Exception e) {
                                            log.debug("JavaScript click failed: {}", e.getMessage());
                                        }
                                    }
                                    
                                    // Try clicking parent element
                                    if (!clicked) {
                                        try {
                                            WebElement parent = element.findElement(By.xpath(".."));
                                            parent.click();
                                            clicked = true;
                                            log.info("Successfully clicked 'See more' parent element");
                                        } catch (Exception e) {
                                            log.debug("Parent click failed: {}", e.getMessage());
                                        }
                                    }
                                    
                                    if (clicked) {
                                        Thread.sleep(1500); // Wait for expansion
                                        
                                        // Check if more "See more" buttons appeared
                                        expandSeeMoreSections(driver); // Recursive call for nested expansions
                                        return;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Could not process see more element: {}", e.getMessage());
                        }
                    }
                    
                    if (!elements.isEmpty()) {
                        break; // Found elements with this pattern, don't try other patterns
                    }
                    
                } catch (Exception e) {
                    log.debug("Pattern '{}' search failed: {}", pattern, e.getMessage());
                }
            }
            
            log.debug("No 'See more' elements found or successfully clicked");
            
        } catch (Exception e) {
            log.warn("Failed to expand 'See more' sections: {}", e.getMessage());
        }
    }

    /**
     * Save results to local JSON file with timestamp
     */
    private void saveToLocalJson(List<Map<String, Object>> results, String searchTerm) {
        try {
            // Create output directory
            Path outputDir = Paths.get("output", "marketplace");
            Files.createDirectories(outputDir);
            
            // Generate filename with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s_%s.json", searchTerm.replaceAll("[^a-zA-Z0-9]", "_"), timestamp);
            Path filePath = outputDir.resolve(filename);
            
            // Save to JSON
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), results);
            
            log.info("Saved {} items to: {}", results.size(), filePath.toAbsolutePath());
            
        } catch (IOException e) {
            log.error("Failed to save results to JSON file", e);
        }
    }

    // Helper extraction methods
    private String extractTitle(WebDriver driver) {
        String[] selectors = {
            "h1[data-testid='marketplace-pdp-title']",
            "h1.x1heor9g",
            "span.x1lliihq.x6ikm8r.x10wlt62.x1n2onr6.xlyipyv.xuxw1ft.x1j85h84",
            "div[data-testid='post_message'] span",
            "[data-testid='marketplace-pdp-header'] h1",
            "h1",
            // Generic fallbacks
            "*[data-testid*='title']",
            "div[role='main'] h1"
        };
        
        return extractTextBySelectorPriority(driver, selectors, "Unknown Title");
    }

    private String extractPrice(WebDriver driver) {
        String[] selectors = {
            "span[data-testid='marketplace-pdp-price']",
            "div[data-testid='marketplace-pdp-price-section'] span",
            "span.x193iq5w.xeuugli.x13faqbe.x1vvkbs.xlh3980.xvmahel.x1n0sxbx.x1lliihq.x1s928wv.xhkezso.x1gmr53x.x1cpjm7i.x1fgarty.x1943h6x.x4zkp8e.x3x7a5m.x1nxh6w3.xo1l8bm.x1qb5hxa",
            "span.x1lliihq.x6ikm8r.x10wlt62.x1n2onr6.xlyipyv.xuxw1ft.x1j85h84",
            // Look for currency symbols
            "span:contains('$')",
            "*[data-testid*='price']",
            // Generic price patterns
            "div[role='main'] span[contains(text(), '$')]"
        };
        
        return extractTextBySelectorPriority(driver, selectors, "Price not found");
    }

    private String extractDescription(WebDriver driver) {
        String[] selectors = {
            "div[data-testid='marketplace-pdp-description']",
            "div[data-testid='post_message']",
            "div[data-testid='marketplace-pdp-description'] span",
            "[data-testid='marketplace-pdp-details-description-text']",
            "div.xz9dl7a.x4uap5.xsag5q8.xkhd6sd.x126k92a",
            // After expanding "See more"
            "span.x193iq5w.xeuugli.x13faqbe.x1vvkbs.x1xmvt09.x6prxxf.xvq8zen.x1s688f.xzsf02u",
            "div[role='main'] div[data-testid*='description']",
            "div[role='main'] span.x1lliihq.x6ikm8r.x10wlt62.x1n2onr6",
            // Generic content areas
            "div[role='main'] div[dir='auto'] span"
        };
        
        return extractTextBySelectorPriority(driver, selectors, "Description not found");
    }

    private String extractSeller(WebDriver driver) {
        String[] selectors = {
            "span[data-testid='marketplace-pdp-seller-name']",
            "div[data-testid='marketplace-pdp-seller-section'] a span",
            "a[role='link'] span.x1lliihq.x6ikm8r.x10wlt62.x1n2onr6",
            "[data-testid='marketplace-pdp-seller-link'] span",
            "strong",
            // Profile links
            "a[href*='/profile/'] span",
            "div[role='main'] a[role='link'] span"
        };
        
        return extractTextBySelectorPriority(driver, selectors, "Seller not found");
    }

    private String extractLocation(WebDriver driver) {
        String[] selectors = {
            "span[data-testid='marketplace-pdp-location']",
            "div[data-testid='marketplace-pdp-location-section'] span",
            "div.x1i10hfl span",
            "[data-testid='location']",
            "*[data-testid*='location']",
            // Look for location icons/text patterns
            "span[aria-label*='location']",
            "div[role='main'] span[contains(text(), ',')]"
        };
        
        return extractTextBySelectorPriority(driver, selectors, "Location not found");
    }

    private List<String> extractImages(WebDriver driver) {
        List<String> images = new ArrayList<>();
        
        try {
            List<WebElement> imgElements = driver.findElements(By.cssSelector("img[data-testid*='marketplace'], img[src*='scontent']"));
            for (WebElement img : imgElements) {
                String src = img.getAttribute("src");
                if (src != null && !src.isEmpty() && !images.contains(src)) {
                    images.add(src);
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract images: {}", e.getMessage());
        }
        
        return images;
    }

    private String extractTextBySelectorPriority(WebDriver driver, String[] selectors, String defaultValue) {
        for (String selector : selectors) {
            try {
                WebElement element = null;
                
                // Handle :contains() selectors with JavaScript
                if (selector.contains(":contains(")) {
                    String containsText = selector.substring(selector.indexOf(":contains('") + 11, selector.lastIndexOf("')"));
                    String baseSelector = selector.substring(0, selector.indexOf(":contains("));
                    
                    String jsScript = String.format(
                        "return Array.from(document.querySelectorAll('%s')).find(el => el.textContent && el.textContent.includes('%s'));", 
                        baseSelector, containsText
                    );
                    element = (WebElement) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(jsScript);
                } else {
                    element = driver.findElement(By.cssSelector(selector));
                }
                
                if (element != null) {
                    String text = element.getText().trim();
                    if (!text.isEmpty()) {
                        log.debug("Found text with selector '{}': {}", selector, text.substring(0, Math.min(text.length(), 50)) + "...");
                        return text;
                    }
                }
            } catch (Exception e) {
                log.debug("Selector '{}' failed: {}", selector, e.getMessage());
                // Try next selector
            }
        }
        log.debug("All selectors failed, returning default: {}", defaultValue);
        return defaultValue;
    }

    private String buildSearchUrl(String searchTerm) {
        String encodedTerm = searchTerm.replace(" ", "%20");
        return String.format("https://www.facebook.com/marketplace/search/?query=%s&sortBy=creation_time_descend", encodedTerm);
    }

    private List<WebElement> findListingElements(WebDriver driver) {
        String[] selectors = {
            "a[href*='/marketplace/item/']",
            "div[data-testid='marketplace-item'] a",
            "div[role='main'] a[href*='/marketplace/item/']"
        };

        for (String selector : selectors) {
            try {
                List<WebElement> elements = driver.findElements(By.cssSelector(selector));
                if (!elements.isEmpty()) {
                    return elements;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found", selector);
            }
        }

        return new ArrayList<>();
    }

    /**
     * Save results to Google Sheets
     */
    private void saveToGoogleSheets(List<Map<String, Object>> results) {
        try {
            log.info("Attempting to save {} listings to Google Sheets", results.size());
            int totalItemsSaved = 0;
            
            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> listing = results.get(i);
                
                // Check if this listing has multiple items
                if (listing.containsKey("items") && listing.get("items") instanceof List) {
                    List<Map<String, Object>> items = (List<Map<String, Object>>) listing.get("items");
                    
                    if (!items.isEmpty()) {
                        // Save each item as a separate row, but include listing-level data
                        for (Map<String, Object> item : items) {
                            Map<String, Object> combinedData = new HashMap<>();
                            
                            // First add all listing-level data
                            combinedData.putAll(listing);
                            
                            // Then override with item-specific data
                            combinedData.putAll(item);
                            
                            // Ensure productType is never empty for individual items
                            String itemProductType = (String) combinedData.get("productType");
                            if (itemProductType == null || itemProductType.trim().isEmpty()) {
                                combinedData.put("productType", "OTHER");
                            }
                            
                            // Ensure only wanted fields from LM Studio are included
                            combinedData.put("mainListingPrice", listing.get("mainListingPrice"));
                            combinedData.put("location", listing.get("location"));
                            combinedData.put("hasMultipleItems", listing.get("hasMultipleItems"));
                            combinedData.put("extractedDescription", listing.get("extractedDescription"));
                            
                            // Remove unwanted fields if they exist
                            combinedData.remove("condition");
                            combinedData.remove("sellerTerms");
                            combinedData.remove("ocrQuality");
                            combinedData.remove("extractionConfidence");
                            combinedData.remove("status");
                            
                            log.info("Saving item to Google Sheets: {}", combinedData.get("itemName"));
                            log.debug("Full combined data: {}", combinedData);
                            googleSheetsService.addMarketplaceListing(combinedData);
                            totalItemsSaved++;
                        }
                    } else {
                        // No items array, save the listing as-is
                        log.info("Saving listing {}/{} to Google Sheets: {}", i + 1, results.size(), 
                            listing.getOrDefault("itemName", "Unknown Item"));
                        googleSheetsService.addMarketplaceListing(listing);
                        totalItemsSaved++;
                    }
                } else {
                    // No items array, save the listing as-is
                    log.info("Saving listing {}/{} to Google Sheets: {}", i + 1, results.size(), 
                        listing.getOrDefault("itemName", "Unknown Item"));
                    googleSheetsService.addMarketplaceListing(listing);
                    totalItemsSaved++;
                }
            }
            
            log.info("Successfully saved {} total items to Google Sheets from {} listings", totalItemsSaved, results.size());
        } catch (Exception e) {
            log.error("Failed to save results to Google Sheets", e);
        }
    }
    
    /**
     * Get the configured model name for LM Studio
     */
    private String getConfiguredModel() {
        // Get from configuration service
        return configurationService.getActiveLmStudioModel();
    }
    
    /**
     * Set the model to use for LM Studio analysis
     */
    public void setConfiguredModel(String model) {
        configurationService.updateLmStudioModel(model);
    }
}