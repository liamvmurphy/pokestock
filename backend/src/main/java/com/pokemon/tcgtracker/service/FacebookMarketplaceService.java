package com.pokemon.tcgtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookMarketplaceService {

    private final WebDriverService webDriverService;
    private final LMStudioService lmStudioService;
    private final GoogleSheetsService googleSheetsService;
    private final ConfigurationService configurationService;

    private static final String MARKETPLACE_BASE_URL = "https://www.facebook.com/marketplace";
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([0-9,]+(?:\\.[0-9]{2})?)");
    
    // Pokemon TCG search terms
    private static final List<String> SEARCH_TERMS = Arrays.asList(
        "Pokemon ETB",
        "Pokemon Elite Trainer Box",
        "Pokemon Booster Box"
    );

    /**
     * Start monitoring Facebook Marketplace for Pokemon TCG listings
     * Uses simple sequential processing in a single tab
     */
    public void startMarketplaceMonitoring() {
        WebDriver driver = null;
        List<Map<String, Object>> allListings = new ArrayList<>();
        
        try {
            driver = webDriverService.getWebDriver();
            log.info("🚀 Starting Facebook Marketplace monitoring with {} search terms", SEARCH_TERMS.size());
            
            // Process all search terms sequentially in a single tab
            for (int i = 0; i < SEARCH_TERMS.size(); i++) {
                String searchTerm = SEARCH_TERMS.get(i);
                try {
                    log.info("📍 Processing search {}/{}: '{}'", i + 1, SEARCH_TERMS.size(), searchTerm);
                    
                    String searchUrl = buildSearchUrl(searchTerm);
                    driver.get(searchUrl);
                    
                    // Wait for page to load
                    Thread.sleep(3000);
                    
                    // Check if login is required
                    if (isLoginRequired(driver)) {
                        log.warn("⚠️ Login required for search: {}", searchTerm);
                        continue;
                    }
                    
                    // Process search results with navigation to individual listings
                    List<Map<String, Object>> listings = processSearchResultsWithNavigation(driver, searchTerm);
                    allListings.addAll(listings);
                    
                    log.info("✅ Completed search {}/{}: found {} listings for '{}'", 
                             i + 1, SEARCH_TERMS.size(), listings.size(), searchTerm);
                    
                    // Add delay between searches to be respectful
                    if (i < SEARCH_TERMS.size() - 1) {
                        webDriverService.humanDelay();
                    }
                    
                } catch (Exception e) {
                    log.error("❌ Error processing search term '{}': {}", searchTerm, e.getMessage());
                }
            }
            
            log.info("🏁 Marketplace monitoring completed. Total listings processed: {}", allListings.size());

        } catch (Exception e) {
            log.error("❌ Failed to start marketplace monitoring", e);
        } finally {
            webDriverService.closeWebDriver(driver);
        }
    }
    
    /**
     * Process search results with navigation to individual listing pages
     * This method navigates to each listing page for detailed scraping
     */
    private List<Map<String, Object>> processSearchResultsWithNavigation(WebDriver driver, String searchTerm) {
        List<Map<String, Object>> listings = new ArrayList<>();
        String searchResultsUrl = driver.getCurrentUrl();
        
        try {
            // Scroll to load more listings
            webDriverService.humanLikeScroll(driver);
            Thread.sleep(2000);
            
            // Find all listing URLs on the search results page
            List<String> listingUrls = collectListingUrls(driver);
            log.info("📋 Found {} listing URLs for '{}'", listingUrls.size(), searchTerm);
            
            // Process up to 15 listings per search term
            int maxItems = 15;
            int processedCount = 0;
            
            for (String listingUrl : listingUrls) {
                if (processedCount >= maxItems) {
                    log.info("📊 Reached {} item limit for search term '{}'", maxItems, searchTerm);
                    break;
                }
                
                try {
                    // Clean the URL
                    String cleanUrl = cleanMarketplaceUrl(listingUrl);
                    
                    // Check if this URL should be refreshed (7-day rule)
                    if (!shouldProcessUrl(cleanUrl)) {
                        log.info("⏭️ Skipping URL (recently updated): {}", cleanUrl);
                        continue;
                    }
                    
                    log.info("🔗 Navigating to listing {}/{}: {}", processedCount + 1, maxItems, cleanUrl);
                    
                    // Navigate to the individual listing page
                    driver.get(cleanUrl);
                    Thread.sleep(3000); // Wait for listing page to load
                    
                    // Check if we're on the correct listing page
                    String currentUrl = driver.getCurrentUrl();
                    if (!currentUrl.contains("/marketplace/item/")) {
                        log.warn("⚠️ Failed to navigate to listing page. Current URL: {}", currentUrl);
                        continue;
                    }
                    
                    // Check for login requirement
                    if (isLoginRequired(driver)) {
                        log.warn("⚠️ Login required on listing page");
                        break;
                    }
                    
                    // Extract listing data from the individual listing page
                    Map<String, Object> listing = extractListingDataFromPage(driver, cleanUrl, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        // Use LM Studio to analyze the listing
                        List<Map<String, Object>> itemsFromListing = analyzeListingWithAI(listing);
                        
                        if (!itemsFromListing.isEmpty()) {
                            listings.addAll(itemsFromListing);
                            processedCount++;
                            
                            // Save all items to Google Sheets (will delete old rows and add new ones)
                            googleSheetsService.addMarketplaceListings(itemsFromListing);
                            
                            log.info("✅ Successfully processed listing {}/{} with {} items from: {}", 
                                    processedCount, maxItems, itemsFromListing.size(), cleanUrl);
                        } else {
                            log.warn("⚠️ No items extracted from listing: {}", cleanUrl);
                        }
                    }
                    
                    // Navigate back to search results
                    log.debug("🔙 Returning to search results");
                    driver.get(searchResultsUrl);
                    Thread.sleep(2000); // Wait for search page to reload
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to process listing: {}", e.getMessage());
                    // Try to return to search results
                    try {
                        driver.get(searchResultsUrl);
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        log.warn("Failed to return to search results: {}", ex.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to process search results for term '{}': {}", searchTerm, e.getMessage());
        }
        
        return listings;
    }
    
    /**
     * Collect listing URLs from the current search results page
     */
    private List<String> collectListingUrls(WebDriver driver) {
        List<String> urls = new ArrayList<>();
        
        try {
            // Find listing elements using multiple selectors
            List<WebElement> listingElements = findListingElements(driver);
            
            for (WebElement element : listingElements) {
                try {
                    String href = element.getAttribute("href");
                    if (href != null && href.contains("/marketplace/item/")) {
                        urls.add(href);
                    }
                } catch (Exception e) {
                    log.debug("Failed to extract URL from element: {}", e.getMessage());
                }
            }
            
            log.info("📝 Collected {} valid listing URLs", urls.size());
            
        } catch (Exception e) {
            log.error("❌ Error collecting listing URLs: {}", e.getMessage());
        }
        
        return urls;
    }
    
    /**
     * Check if a URL should be processed based on 7-day rule
     */
    private boolean shouldProcessUrl(String url) {
        try {
            return googleSheetsService.shouldRefreshUrl(url);
        } catch (Exception e) {
            log.warn("Failed to check URL refresh status, will process: {}", e.getMessage());
            return true; // Process if we can't check
        }
    }

    /**
     * Scrape Facebook Marketplace with dynamic search term and item limit
     * This replaces the functionality from MarketplaceScrapingService
     */
    public List<Map<String, Object>> scrapeMarketplaceItems(String searchTerm, int maxItems) {
        WebDriver driver = null;
        List<Map<String, Object>> allListings = new ArrayList<>();
        
        try {
            driver = webDriverService.getWebDriver();
            log.info("🚀 Starting marketplace scraping for '{}' with max {} items", searchTerm, maxItems);
            
            String searchUrl = buildSearchUrl(searchTerm);
            driver.get(searchUrl);
            
            // Wait for page to load
            Thread.sleep(3000);
            
            // Check if login is required
            if (isLoginRequired(driver)) {
                log.warn("Login required for marketplace scraping");
                return allListings;
            }
            
            // Process search results with navigation to individual listings
            allListings = processSearchResultsWithNavigation(driver, searchTerm, maxItems);
            
            log.info("🏁 Marketplace scraping completed. Processed {} listings", allListings.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to scrape marketplace items", e);
        } finally {
            webDriverService.closeWebDriver(driver);
        }
        
        return allListings;
    }
    
    /**
     * Process search results with navigation to individual listing pages (with custom max items)
     */
    private List<Map<String, Object>> processSearchResultsWithNavigation(WebDriver driver, String searchTerm, int maxItems) {
        List<Map<String, Object>> listings = new ArrayList<>();
        String searchResultsUrl = driver.getCurrentUrl();
        
        try {
            // Scroll to load more listings
            webDriverService.humanLikeScroll(driver);
            Thread.sleep(2000);
            
            // Find all listing URLs on the search results page
            List<String> listingUrls = collectListingUrls(driver);
            log.info("📋 Found {} listing URLs for '{}'", listingUrls.size(), searchTerm);
            
            int processedCount = 0;
            
            for (String listingUrl : listingUrls) {
                if (processedCount >= maxItems) {
                    log.info("📊 Reached {} item limit for search term '{}'", maxItems, searchTerm);
                    break;
                }
                
                try {
                    // Clean the URL
                    String cleanUrl = cleanMarketplaceUrl(listingUrl);
                    
                    // Check if this URL should be refreshed (7-day rule)
                    if (!shouldProcessUrl(cleanUrl)) {
                        log.info("⏭️ Skipping URL (recently updated): {}", cleanUrl);
                        continue;
                    }
                    
                    log.info("🔗 Navigating to listing {}/{}: {}", processedCount + 1, maxItems, cleanUrl);
                    
                    // Navigate to the individual listing page
                    driver.get(cleanUrl);
                    Thread.sleep(3000); // Wait for listing page to load
                    
                    // Check if we're on the correct listing page
                    String currentUrl = driver.getCurrentUrl();
                    if (!currentUrl.contains("/marketplace/item/")) {
                        log.warn("⚠️ Failed to navigate to listing page. Current URL: {}", currentUrl);
                        continue;
                    }
                    
                    // Check for login requirement
                    if (isLoginRequired(driver)) {
                        log.warn("⚠️ Login required on listing page");
                        break;
                    }
                    
                    // Extract listing data from the individual listing page
                    Map<String, Object> listing = extractListingDataFromPage(driver, cleanUrl, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        // Use LM Studio to analyze the listing
                        List<Map<String, Object>> itemsFromListing = analyzeListingWithAI(listing);
                        
                        if (!itemsFromListing.isEmpty()) {
                            listings.addAll(itemsFromListing);
                            processedCount++;
                            
                            // Save all items to Google Sheets (will delete old rows and add new ones)
                            googleSheetsService.addMarketplaceListings(itemsFromListing);
                            
                            log.info("✅ Successfully processed listing {}/{} with {} items from: {}", 
                                    processedCount, maxItems, itemsFromListing.size(), cleanUrl);
                        } else {
                            log.warn("⚠️ No items extracted from listing: {}", cleanUrl);
                        }
                    }
                    
                    // Navigate back to search results
                    log.debug("🔙 Returning to search results");
                    driver.get(searchResultsUrl);
                    Thread.sleep(2000); // Wait for search page to reload
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to process listing: {}", e.getMessage());
                    // Try to return to search results
                    try {
                        driver.get(searchResultsUrl);
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        log.warn("Failed to return to search results: {}", ex.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to process search results for term '{}': {}", searchTerm, e.getMessage());
        }
        
        return listings;
    }
    
    /**
     * Search Facebook Marketplace for specific term (legacy method for single searches)
     */
    public List<Map<String, Object>> searchMarketplace(WebDriver driver, String searchTerm) {
        List<Map<String, Object>> listings = new ArrayList<>();
        
        try {
            String searchUrl = buildSearchUrl(searchTerm);
            log.info("Searching marketplace for: {}", searchTerm);
            
            // Navigate to search URL
            webDriverService.navigateToUrl(driver, searchUrl);
            
            // Process the search results with default 15 items limit
            listings = processSearchResultsWithNavigation(driver, searchTerm, 15);

        } catch (Exception e) {
            log.error("Failed to search marketplace for term: {}", searchTerm, e);
        }

        return listings;
    }

    /**
     * Build Facebook Marketplace search URL
     */
    private String buildSearchUrl(String searchTerm) {
        String encodedTerm = searchTerm.replace(" ", "%20");
        return String.format("%s/search/?query=%s&sortBy=creation_time_descend&exact=false", 
                MARKETPLACE_BASE_URL, encodedTerm);
    }

    /**
     * Check if login is required
     */
    private boolean isLoginRequired(WebDriver driver) {
        String currentUrl = driver.getCurrentUrl();
        String pageSource = driver.getPageSource();
        
        boolean urlLogin = currentUrl.contains("/login");
        boolean sourceLogin = pageSource.contains("Log in to Facebook");
        boolean sourceAccount = pageSource.contains("Create new account");
        
        boolean loginRequired = urlLogin || sourceLogin || sourceAccount;
        log.debug("🔍 Login required check: {} (URL: {}, LogText: {}, AccText: {})", 
                loginRequired, urlLogin, sourceLogin, sourceAccount);
        
        return loginRequired;
    }

    /**
     * Find listing elements on the page
     */
    private List<WebElement> findListingElements(WebDriver driver) {
        List<WebElement> elements = new ArrayList<>();
        
        // Try different selectors for listing elements
        String[] selectors = {
            "[data-testid='marketplace-item']",
            "div[role='main'] a[href*='/marketplace/item/']",
            "div[data-testid='marketplace_feed'] a",
            "a[href*='/marketplace/item/']"
        };

        for (String selector : selectors) {
            try {
                List<WebElement> found = driver.findElements(By.cssSelector(selector));
                if (!found.isEmpty()) {
                    elements = found;
                    log.debug("Found {} elements with selector: {}", found.size(), selector);
                    break;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found", selector);
            }
        }

        return elements;
    }

    /**
     * Extract data from an individual listing page (used when navigated to specific listing)
     * Simplified to rely on screenshot + LMStudio analysis rather than DOM parsing
     */
    private Map<String, Object> extractListingDataFromPage(WebDriver driver, String listingUrl, String searchTerm) {
        Map<String, Object> listing = new HashMap<>();
        
        try {
            listing.put("url", listingUrl);

            // Set basic metadata - let LMStudio extract the detailed info from screenshot
            listing.put("itemName", "Analyzing with AI..."); // Placeholder - LMStudio will fill this
            listing.put("seller", "Unknown");
            listing.put("searchTerm", searchTerm);
            listing.put("dateFound", new Date());
            listing.put("status", "New");
            listing.put("source", "Facebook Marketplace");

            // Try to expand the listing by clicking "See more" before taking screenshot
            expandListingDescription(driver);
            
            // Take screenshot from the actual listing page for AI analysis - this is the key part
            log.info("📸 Taking screenshot of listing page for AI analysis");
            String screenshot = webDriverService.takeScreenshot(driver);
            listing.put("screenshot", screenshot);

            log.info("✅ Captured listing page screenshot for AI analysis: {}", listingUrl);

            return listing;

        } catch (Exception e) {
            log.warn("Failed to extract listing data from page: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract item name from listing element or text
     */
    private String extractItemName(String text) {
        try {
            // Try to extract first line or meaningful text
            String[] lines = text.split("\\n");
            if (lines.length > 0) {
                // Look for lines that might be the title
                for (String line : lines) {
                    if (line.length() > 10 && isPokemonTcgRelated(line)) {
                        return line.trim();
                    }
                }
                // Default to first non-empty line
                for (String line : lines) {
                    if (line.trim().length() > 5) {
                        return line.trim();
                    }
                }
            }
            return "Unknown Item";
        } catch (Exception e) {
            return "Unknown Item";
        }
    }

    /**
     * Extract price from text
     */
    private Double extractPrice(String text) {
        try {
            Matcher matcher = PRICE_PATTERN.matcher(text);
            if (matcher.find()) {
                String priceStr = matcher.group(1).replace(",", "");
                return Double.parseDouble(priceStr);
            }
        } catch (Exception e) {
            log.debug("Could not extract price from: {}", text);
        }
        return null;
    }

    /**
     * Check if text is Pokemon TCG related
     */
    private boolean isPokemonTcgRelated(String text) {
        if (text == null) return false;
        
        String lowerText = text.toLowerCase();
        return lowerText.contains("pokemon") || 
               lowerText.contains("tcg") || 
               lowerText.contains("booster") ||
               lowerText.contains("trainer") ||
               lowerText.contains("cards");
    }

    /**
     * Clean marketplace URL by removing query parameters and fragments
     * This ensures clean URLs are used as primary keys, avoiding issues with 
     * UUID query parameters and other tracking parameters
     */
    private String cleanMarketplaceUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        
        try {
            // Find the position of query parameters (?) or fragments (#)
            int queryIndex = url.indexOf('?');
            int fragmentIndex = url.indexOf('#');
            
            // Find the earliest position to cut
            int cutIndex = -1;
            if (queryIndex != -1 && fragmentIndex != -1) {
                cutIndex = Math.min(queryIndex, fragmentIndex);
            } else if (queryIndex != -1) {
                cutIndex = queryIndex;
            } else if (fragmentIndex != -1) {
                cutIndex = fragmentIndex;
            }
            
            // Return cleaned URL or original if no params/fragments found
            return cutIndex != -1 ? url.substring(0, cutIndex) : url;
            
        } catch (Exception e) {
            log.warn("Failed to clean URL '{}', using original: {}", url, e.getMessage());
            return url;
        }
    }

    /**
     * Use LM Studio to analyze the listing and return all items found
     * Returns a list of items, each with its own data but sharing the same URL
     */
    private List<Map<String, Object>> analyzeListingWithAI(Map<String, Object> listing) {
        List<Map<String, Object>> allItems = new ArrayList<>();
        
        try {
            String description = String.format("Item: %s, Price: %s, Seller: %s", 
                    listing.get("itemName"), 
                    listing.get("price"), 
                    listing.get("seller"));
            
            String screenshot = (String) listing.get("screenshot");
            if (screenshot != null) {
                // Get model from configuration or use default
                String model = getConfiguredModel();
                Map<String, Object> analysis = lmStudioService.analyzeMarketplaceListing(description, screenshot, model);
                
                // Extract AI analysis results
                if (analysis != null && !analysis.containsKey("error")) {
                    // Extract shared top-level fields
                    String mainListingPrice = (String) analysis.get("mainListingPrice");
                    String extractedDescription = (String) analysis.get("extractedDescription");
                    String location = (String) analysis.get("location");
                    Boolean hasMultipleItems = (Boolean) analysis.get("hasMultipleItems");
                    
                    // Process each item from the analysis
                    if (analysis.containsKey("items") && analysis.get("items") instanceof List) {
                        List<Map<String, Object>> items = (List<Map<String, Object>>) analysis.get("items");
                        
                        for (Map<String, Object> item : items) {
                            // Create a new listing entry for each item
                            Map<String, Object> itemListing = new HashMap<>(listing);
                            
                            // Add item-specific data
                            itemListing.put("itemName", item.get("itemName"));
                            itemListing.put("set", item.get("set"));
                            itemListing.put("productType", item.get("productType"));
                            itemListing.put("price", item.get("price"));
                            itemListing.put("quantity", item.get("quantity"));
                            itemListing.put("priceUnit", item.get("priceUnit"));
                            itemListing.put("notes", item.get("notes"));
                            
                            // Add shared data
                            itemListing.put("mainListingPrice", mainListingPrice);
                            itemListing.put("extractedDescription", extractedDescription);
                            itemListing.put("location", location);
                            itemListing.put("hasMultipleItems", hasMultipleItems);
                            
                            allItems.add(itemListing);
                        }
                        
                        log.info("📦 Extracted {} items from listing with URL: {}", 
                                items.size(), listing.get("url"));
                    }
                    
                    // If no items were found, add the original listing as a single item
                    if (allItems.isEmpty()) {
                        listing.put("mainListingPrice", mainListingPrice);
                        listing.put("extractedDescription", extractedDescription);
                        listing.put("location", location);
                        listing.put("hasMultipleItems", false);
                        allItems.add(listing);
                    }
                }
            }
            
            // If AI analysis failed or no screenshot, return the original listing
            if (allItems.isEmpty()) {
                allItems.add(listing);
            }
            
        } catch (Exception e) {
            log.warn("Failed to analyze listing with AI: {}", e.getMessage());
            // Return the original listing on error
            allItems.add(listing);
        }
        
        return allItems;
    }

    /**
     * Get marketplace monitoring status
     */
    public Map<String, Object> getMonitoringStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isRunning", false); // TODO: Implement actual status tracking
        status.put("lastRun", new Date());
        status.put("searchTerms", SEARCH_TERMS);
        status.put("status", "Ready");
        return status;
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
    
    /**
     * Try to expand the listing description by clicking "See more" button
     */
    private void expandListingDescription(WebDriver driver) {
        try {
            log.debug("🔍 Looking for 'See more' button to expand description");
            
            WebElement seeMoreButton = null;
            
            // Use the specific selector that works for Facebook Marketplace
            try {
                // Using XPath to find span containing 'See more' text
                seeMoreButton = driver.findElement(By.xpath("//span[contains(text(), 'See more')]"));
                
                if (seeMoreButton != null && seeMoreButton.isDisplayed()) {
                    log.debug("✅ Found 'See more' button");
                }
            } catch (Exception e) {
                log.debug("'See more' button not found: {}", e.getMessage());
            }
            
            // If we found a "See more" button, click it
            if (seeMoreButton != null && seeMoreButton.isDisplayed()) {
                try {
                    log.info("🖱️ Clicking 'See more' to expand description");
                    
                    // Scroll to the element first to ensure it's in view
                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", seeMoreButton);
                    Thread.sleep(500);
                    
                    // Try regular click first
                    seeMoreButton.click();
                    Thread.sleep(1000); // Wait for expansion to complete
                    
                    log.info("✅ Successfully clicked 'See more' button");
                    
                } catch (Exception e) {
                    log.debug("Regular click failed, trying JavaScript click: {}", e.getMessage());
                    try {
                        // Fallback to JavaScript click
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", seeMoreButton);
                        Thread.sleep(1000);
                        log.info("✅ Successfully clicked 'See more' button with JavaScript");
                    } catch (Exception jsException) {
                        log.warn("Failed to click 'See more' button: {}", jsException.getMessage());
                    }
                }
            } else {
                log.debug("📝 No 'See more' button found - description may already be fully expanded");
            }
            
        } catch (Exception e) {
            log.warn("Error while trying to expand listing description: {}", e.getMessage());
        }
    }
}