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
     * Attempts multi-tab processing, falls back to optimized sequential processing
     */
    public void startMarketplaceMonitoring() {
        WebDriver driver = null;
        List<Map<String, Object>> allListings = new ArrayList<>();
        
        try {
            driver = webDriverService.getWebDriver();
            log.info("🚀 Starting Facebook Marketplace monitoring with {} search terms", SEARCH_TERMS.size());
            
            // Check if we can create new tabs by testing one
            log.info("🔍 Checking if multi-tab mode is possible...");
            boolean canUseMultiTab = canCreateNewTabs(driver);
            log.info("📊 Multi-tab capability result: {}", canUseMultiTab);
            
            if (canUseMultiTab) {
                log.info("✅ Multi-tab mode: attempting to create separate tabs for each search");
                allListings = processWithMultipleTabs(driver);
            } else {
                log.info("⚠️ Single-tab mode: processing searches sequentially in current tab");
                allListings = processSequentially(driver);
            }
            
            log.info("🏁 Marketplace monitoring completed. Total listings processed: {}", allListings.size());

        } catch (Exception e) {
            log.error("❌ Failed to start marketplace monitoring", e);
        } finally {
            webDriverService.closeWebDriver(driver);
        }
    }
    
    /**
     * Test if we can use multiple tabs (either create new ones or use existing ones)
     */
    private boolean canCreateNewTabs(WebDriver driver) {
        try {
            Set<String> allTabHandles = webDriverService.getAllTabHandles(driver);
            int initialTabCount = allTabHandles.size();
            
            log.info("🔢 Checking multi-tab capability...");
            log.info("📋 Current tab count: {}", initialTabCount);
            log.info("📋 Required tabs for all searches: {}", SEARCH_TERMS.size());
            log.info("📋 All tab handles: {}", allTabHandles);
            
            // If we already have enough tabs for all searches, we can use multi-tab mode
            if (initialTabCount >= SEARCH_TERMS.size()) {
                log.info("✅ Found {} existing tabs, which is enough for {} search terms. Using multi-tab mode!", 
                         initialTabCount, SEARCH_TERMS.size());
                return true;
            }
            
            log.info("⚠️ Not enough existing tabs ({} < {}). Trying to create a test tab...", 
                     initialTabCount, SEARCH_TERMS.size());
            
            // Try to create a single test tab
            String originalTab = webDriverService.getCurrentTabHandle(driver);
            log.info("📌 Original tab handle: {}", originalTab);
            
            // Try JavaScript approach
            log.info("🧪 Attempting to create test tab with JavaScript...");
            ((JavascriptExecutor) driver).executeScript("window.open('about:blank', '_blank');");
            Thread.sleep(1000);
            
            Set<String> newTabHandles = webDriverService.getAllTabHandles(driver);
            int newTabCount = newTabHandles.size();
            log.info("📊 After tab creation attempt: {} tabs", newTabCount);
            log.info("📋 New tab handles: {}", newTabHandles);
            
            if (newTabCount > initialTabCount) {
                log.info("✅ Test tab created successfully!");
                // Close the test tab
                for (String handle : newTabHandles) {
                    if (!allTabHandles.contains(handle)) {
                        log.info("🗑️ Closing test tab: {}", handle);
                        driver.switchTo().window(handle);
                        driver.close();
                        break;
                    }
                }
                // Return to original tab
                driver.switchTo().window(originalTab);
                log.info("✅ Tab creation test successful - can create new tabs programmatically");
                return true;
            } else {
                log.warn("❌ Cannot create new tabs programmatically. To use multi-tab mode, manually open {} total tabs in Chrome", SEARCH_TERMS.size());
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Tab capability test failed with exception: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Process searches using multiple tabs (if possible)
     */
    private List<Map<String, Object>> processWithMultipleTabs(WebDriver driver) {
        List<Map<String, Object>> allListings = new ArrayList<>();
        
        try {
            Set<String> allTabHandles = webDriverService.getAllTabHandles(driver);
            List<String> tabHandlesList = new ArrayList<>(allTabHandles);
            
            log.info("Found {} existing tabs for multi-tab processing", tabHandlesList.size());
            
            if (tabHandlesList.size() < SEARCH_TERMS.size()) {
                log.warn("Not enough tabs ({}) for all search terms ({}). Need to manually open more tabs or will fall back to sequential.", 
                         tabHandlesList.size(), SEARCH_TERMS.size());
                return processSequentially(driver);
            }
            
            // Step 1: Navigate each tab to its search URL
            Map<String, String> tabToSearchTerm = new HashMap<>();
            log.info("=== TAB DEBUGGING INFO ===");
            log.info("Available tab handles: {}", tabHandlesList);
            log.info("Current tab before starting: {}", webDriverService.getCurrentTabHandle(driver));
            log.info("Total tabs detected: {}", tabHandlesList.size());
            log.info("Search terms to process: {}", SEARCH_TERMS);
            log.info("========================");
            
            for (int i = 0; i < SEARCH_TERMS.size(); i++) {
                String searchTerm = SEARCH_TERMS.get(i);
                String tabHandle = tabHandlesList.get(i);
                String searchUrl = buildSearchUrl(searchTerm);
                
                try {
                    log.info("=== PROCESSING TAB {} ===", i + 1);
                    log.info("Target tab handle: {}", tabHandle);
                    log.info("Search term: '{}'", searchTerm);
                    log.info("Search URL: {}", searchUrl);
                    
                    // Check current tab before switching
                    String currentTabBefore = webDriverService.getCurrentTabHandle(driver);
                    log.info("Current tab before switch: {}", currentTabBefore);
                    
                    boolean switchSuccess = webDriverService.switchToTab(driver, tabHandle);
                    log.info("Switch to tab result: {}", switchSuccess);
                    
                    if (!switchSuccess) {
                        log.error("FAILED to switch to tab {}", tabHandle);
                        continue;
                    }
                    
                    // Verify we actually switched
                    String currentTabAfter = webDriverService.getCurrentTabHandle(driver);
                    log.info("Current tab after switch: {}", currentTabAfter);
                    log.info("Switch successful: {}", tabHandle.equals(currentTabAfter));
                    
                    String urlBeforeNavigation = driver.getCurrentUrl();
                    log.info("URL before navigation: {}", urlBeforeNavigation);
                    
                    // Navigate to search URL
                    driver.get(searchUrl);
                    
                    // Verify navigation
                    String urlAfterNavigation = driver.getCurrentUrl();
                    log.info("URL after navigation: {}", urlAfterNavigation);
                    log.info("Navigation successful: {}", urlAfterNavigation.contains("marketplace"));
                    
                    tabToSearchTerm.put(tabHandle, searchTerm);
                    log.info("=== TAB {} SETUP COMPLETE ===", i + 1);
                    
                    // Small delay between navigations
                    Thread.sleep(1000);
                } catch (Exception e) {
                    log.error("ERROR in tab {} for '{}': {}", i + 1, searchTerm, e.getMessage(), e);
                }
            }
            
            log.info("=== NAVIGATION SUMMARY ===");
            log.info("Tab to search term mapping: {}", tabToSearchTerm);
            log.info("Successfully configured {} tabs", tabToSearchTerm.size());
            log.info("=========================");
            
            log.info("All {} tabs navigated to their search URLs. Waiting for pages to load...", SEARCH_TERMS.size());
            Thread.sleep(3000); // Give all tabs time to load
            
            // Step 2: Process all tabs concurrently by switching between them
            log.info("=== STARTING CONCURRENT RESULT PROCESSING ===");
            
            // Process up to 15 items total, switching between tabs
            int totalItemsProcessed = 0;
            int maxItemsPerSearch = 15;
            int maxTotalItems = maxItemsPerSearch * tabToSearchTerm.size(); // 45 total
            
            // Track how many items we've processed per tab
            Map<String, Integer> itemsPerTab = new HashMap<>();
            for (String tabHandle : tabToSearchTerm.keySet()) {
                itemsPerTab.put(tabHandle, 0);
            }
            
            List<String> availableTabs = new ArrayList<>(tabToSearchTerm.keySet());
            int currentTabIndex = 0;
            
            log.info("🚀 Starting concurrent processing across {} tabs for up to {} total items", 
                     availableTabs.size(), maxTotalItems);
            
            while (totalItemsProcessed < maxTotalItems && !availableTabs.isEmpty()) {
                String currentTabHandle = availableTabs.get(currentTabIndex);
                String searchTerm = tabToSearchTerm.get(currentTabHandle);
                int processedInThisTab = itemsPerTab.get(currentTabHandle);
                
                // Skip this tab if it's already processed its limit
                if (processedInThisTab >= maxItemsPerSearch) {
                    availableTabs.remove(currentTabIndex);
                    if (!availableTabs.isEmpty()) {
                        currentTabIndex = currentTabIndex % availableTabs.size();
                    }
                    continue;
                }
                
                try {
                    log.info("🔄 Processing item {}/{} from tab '{}' (term: '{}')", 
                             processedInThisTab + 1, maxItemsPerSearch, currentTabHandle, searchTerm);
                    
                    // Switch to current tab
                    boolean switchSuccess = webDriverService.switchToTab(driver, currentTabHandle);
                    if (!switchSuccess) {
                        log.error("❌ Failed to switch to tab {}", currentTabHandle);
                        availableTabs.remove(currentTabIndex);
                        if (!availableTabs.isEmpty()) {
                            currentTabIndex = currentTabIndex % availableTabs.size();
                        }
                        continue;
                    }
                    
                    // Process one item from this tab
                    List<Map<String, Object>> singleListing = processSingleListingFromTab(driver, searchTerm, processedInThisTab);
                    
                    if (!singleListing.isEmpty()) {
                        allListings.addAll(singleListing);
                        totalItemsProcessed++;
                        itemsPerTab.put(currentTabHandle, processedInThisTab + 1);
                        
                        log.info("✅ Processed 1 listing from '{}'. Total: {}/{}. Tab progress: {}/{}", 
                                 searchTerm, totalItemsProcessed, maxTotalItems, 
                                 itemsPerTab.get(currentTabHandle), maxItemsPerSearch);
                    } else {
                        log.warn("⚠️ No more listings found in tab '{}' after {} items", searchTerm, processedInThisTab);
                        // Remove this tab from rotation if no more items
                        availableTabs.remove(currentTabIndex);
                        if (!availableTabs.isEmpty()) {
                            currentTabIndex = currentTabIndex % availableTabs.size();
                        }
                        continue;
                    }
                    
                    // Move to next tab
                    currentTabIndex = (currentTabIndex + 1) % availableTabs.size();
                    
                    // Small delay between tab switches
                    Thread.sleep(500);
                    
                } catch (Exception e) {
                    log.error("❌ Error processing from tab '{}': {}", searchTerm, e.getMessage());
                    // Move to next tab on error
                    currentTabIndex = (currentTabIndex + 1) % availableTabs.size();
                }
            }
            
            log.info("🏁 Concurrent processing completed. Processed {} total items across {} tabs", 
                     totalItemsProcessed, tabToSearchTerm.size());
            for (Map.Entry<String, Integer> entry : itemsPerTab.entrySet()) {
                String tabHandle = entry.getKey();
                String searchTerm = tabToSearchTerm.get(tabHandle);
                log.info("📊 Tab '{}': {} items processed", searchTerm, entry.getValue());
            }
            
            log.info("Multi-tab processing completed. Total listings: {}", allListings.size());
            
        } catch (Exception e) {
            log.error("Error in multi-tab processing, falling back to sequential: {}", e.getMessage());
            return processSequentially(driver);
        }
        
        return allListings;
    }
    
    /**
     * Process a single listing from the current tab
     */
    private List<Map<String, Object>> processSingleListingFromTab(WebDriver driver, String searchTerm, int itemIndex) {
        List<Map<String, Object>> listings = new ArrayList<>();
        String originalUrl = null;
        
        try {
            // Store original URL to return to search results
            originalUrl = driver.getCurrentUrl();
            
            // Check if we need to log in
            if (isLoginRequired(driver)) {
                log.warn("Login required for search: {}", searchTerm);
                return listings;
            }

            // Wait for listings to load if this is the first item
            if (itemIndex == 0) {
                Thread.sleep(2000);
                // Scroll to load more listings
                webDriverService.humanLikeScroll(driver);
            }
            
            // Find listing elements on the search results page
            List<WebElement> listingElements = findListingElements(driver);
            log.debug("Found {} potential listings for '{}' on attempt {}", listingElements.size(), searchTerm, itemIndex + 1);

            // Process the specific item we want (by index)
            if (itemIndex < listingElements.size()) {
                WebElement listingElement = listingElements.get(itemIndex);
                
                try {
                    // Extract the listing URL from the element
                    String listingUrl = listingElement.getAttribute("href");
                    if (listingUrl == null || !listingUrl.contains("/marketplace/item/")) {
                        log.warn("Invalid listing URL at index {}: {}", itemIndex, listingUrl);
                        return listings;
                    }
                    
                    // Clean the URL
                    String cleanUrl = cleanMarketplaceUrl(listingUrl);
                    log.info("🔗 Navigating to listing {}: {}", itemIndex + 1, cleanUrl);
                    
                    // Navigate to the individual listing page in this tab
                    driver.get(cleanUrl);
                    Thread.sleep(3000); // Wait for listing page to load
                    
                    // Check if we're on the correct listing page
                    String currentUrl = driver.getCurrentUrl();
                    if (!currentUrl.contains("/marketplace/item/")) {
                        log.warn("Failed to navigate to listing page. Current URL: {}", currentUrl);
                        return listings;
                    }
                    
                    // Check again for login after navigation
                    if (isLoginRequired(driver)) {
                        log.warn("Login required on listing page for: {}", cleanUrl);
                        return listings;
                    }
                    
                    // Extract listing data from the individual listing page
                    Map<String, Object> listing = extractListingDataFromPage(driver, cleanUrl, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        listings.add(listing);
                        
                        // Use LM Studio to analyze the listing
                        analyzeListingWithAI(listing);
                        
                        // Save to Google Sheets
                        googleSheetsService.addMarketplaceListing(listing);
                        
                        log.info("✅ Successfully processed listing {}: {}", itemIndex + 1, listing.get("itemName"));
                    }
                    
                    // Navigate back to search results for next iteration
                    log.debug("🔙 Returning to search results: {}", originalUrl);
                    driver.get(originalUrl);
                    Thread.sleep(2000); // Wait for search page to reload
                    
                } catch (Exception e) {
                    log.warn("Failed to process listing element {}: {}", itemIndex + 1, e.getMessage());
                    // Try to return to search results even if processing failed
                    try {
                        if (originalUrl != null) {
                            driver.get(originalUrl);
                            Thread.sleep(1000);
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to return to search results: {}", ex.getMessage());
                    }
                }
            } else {
                log.debug("No listing found at index {} for search '{}'", itemIndex, searchTerm);
            }
            
        } catch (Exception e) {
            log.error("Failed to process single listing from tab for term '{}': {}", searchTerm, e.getMessage());
            // Try to return to original URL if possible
            try {
                if (originalUrl != null) {
                    driver.get(originalUrl);
                    Thread.sleep(1000);
                }
            } catch (Exception ex) {
                log.warn("Failed to return to original URL: {}", ex.getMessage());
            }
        }
        
        return listings;
    }
    
    /**
     * Process searches sequentially in the same tab with optimizations
     */
    private List<Map<String, Object>> processSequentially(WebDriver driver) {
        List<Map<String, Object>> allListings = new ArrayList<>();
        
        try {
            for (int i = 0; i < SEARCH_TERMS.size(); i++) {
                String searchTerm = SEARCH_TERMS.get(i);
                try {
                    log.info("Processing search {}/{}: '{}'", i + 1, SEARCH_TERMS.size(), searchTerm);
                    
                    String searchUrl = buildSearchUrl(searchTerm);
                    driver.get(searchUrl);
                    
                    // Wait for page to load
                    Thread.sleep(2000);
                    
                    List<Map<String, Object>> listings = processSearchResults(driver, searchTerm);
                    allListings.addAll(listings);
                    
                    log.info("Completed search {}/{}: found {} listings for '{}'", 
                             i + 1, SEARCH_TERMS.size(), listings.size(), searchTerm);
                    
                    // Add delay between searches to be respectful
                    if (i < SEARCH_TERMS.size() - 1) {
                        webDriverService.humanDelay();
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing search term '{}': {}", searchTerm, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in sequential processing: {}", e.getMessage());
        }
        
        return allListings;
    }

    /**
     * Search Facebook Marketplace for specific term (legacy method for single searches)
     */
    public List<Map<String, Object>> searchMarketplace(WebDriver driver, String searchTerm) {
        List<Map<String, Object>> listings = new ArrayList<>();
        String originalTab = null;
        
        try {
            String searchUrl = buildSearchUrl(searchTerm);
            log.info("Searching marketplace for: {}", searchTerm);
            
            // Store the original tab handle
            originalTab = webDriverService.getCurrentTabHandle(driver);
            
            // Open search in new tab
            boolean openedNewTab = webDriverService.openNewTabWithUrl(driver, searchUrl);
            if (!openedNewTab) {
                log.warn("Failed to open new tab, using current tab instead");
                webDriverService.navigateToUrl(driver, searchUrl);
            }
            
            // Process the search results
            listings = processSearchResults(driver, searchTerm);
            
            // Close the tab if we opened a new one
            if (originalTab != null && webDriverService.getAllTabHandles(driver).size() > 1) {
                webDriverService.closeCurrentTab(driver, originalTab);
            }

        } catch (Exception e) {
            log.error("Failed to search marketplace for term: {}", searchTerm, e);
            // Try to return to original tab if possible
            if (originalTab != null) {
                try {
                    webDriverService.switchToTab(driver, originalTab);
                } catch (Exception ex) {
                    log.warn("Failed to switch back to original tab", ex);
                }
            }
        }

        return listings;
    }
    
    /**
     * Process search results from the current tab
     * Extracts listings from the page, analyzes them, and saves to Google Sheets
     */
    private List<Map<String, Object>> processSearchResults(WebDriver driver, String searchTerm) {
        List<Map<String, Object>> listings = new ArrayList<>();
        
        try {
            // Check if we need to log in
            if (isLoginRequired(driver)) {
                log.warn("Login required for search: {}", searchTerm);
                return listings;
            }

            // Wait for listings to load
            Thread.sleep(3000);
            
            // Scroll to load more listings
            webDriverService.humanLikeScroll(driver);
            
            // Find listing elements (limited to 15 per search)
            List<WebElement> listingElements = findListingElements(driver);
            log.info("Found {} potential listings for '{}'", listingElements.size(), searchTerm);

            // Process up to 15 listings per search
            int processedCount = 0;
            for (WebElement listingElement : listingElements) {
                if (processedCount >= 15) {
                    log.info("Reached 15 listing limit for search term '{}'", searchTerm);
                    break;
                }
                
                try {
                    Map<String, Object> listing = extractListingData(driver, listingElement, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        listings.add(listing);
                        processedCount++;
                        
                        // Use LM Studio to analyze the listing
                        analyzeListingWithAI(listing);
                        
                        // Save to Google Sheets
                        googleSheetsService.addMarketplaceListing(listing);
                        
                        log.info("Processed listing {}/15: {}", processedCount, listing.get("itemName"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process listing element: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Failed to process search results for term '{}': {}", searchTerm, e.getMessage());
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
        
        log.debug("Login check - URL: {}", currentUrl);
        log.debug("Login check - URL contains /login: {}", urlLogin);
        log.debug("Login check - Page contains 'Log in to Facebook': {}", sourceLogin);
        log.debug("Login check - Page contains 'Create new account': {}", sourceAccount);
        
        boolean loginRequired = urlLogin || sourceLogin || sourceAccount;
        log.info("🔍 Login required check result: {} (URL: {}, LogText: {}, AccText: {})", 
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
                    elements.addAll(found.subList(0, Math.min(found.size(), 20))); // Limit to 20 items (to ensure we can get 15 valid ones)
                    break;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found", selector);
            }
        }

        return elements;
    }

    /**
     * Extract data from a listing element (used for search results page)
     */
    private Map<String, Object> extractListingData(WebDriver driver, WebElement listingElement, String searchTerm) {
        Map<String, Object> listing = new HashMap<>();
        
        try {
            // Get listing URL
            String listingUrl = listingElement.getAttribute("href");
            if (listingUrl == null || !listingUrl.contains("/marketplace/item/")) {
                return null;
            }
            // Clean URL by removing query parameters
            String cleanUrl = cleanMarketplaceUrl(listingUrl);
            listing.put("url", cleanUrl);

            // Extract text content
            String listingText = listingElement.getText();
            if (listingText.isEmpty()) {
                return null;
            }

            // Extract title/item name
            String itemName = extractItemName(listingElement, listingText);
            listing.put("itemName", itemName);

            // Extract price
            Double price = extractPrice(listingText);
            if (price != null) {
                listing.put("price", price);
            }

            // Extract seller info
            String seller = extractSeller(listingElement);
            listing.put("seller", seller != null ? seller : "Unknown");

            // Set metadata
            listing.put("searchTerm", searchTerm);
            listing.put("dateFound", new Date());
            listing.put("status", "New");
            listing.put("source", "Facebook Marketplace");

            // Take screenshot for AI analysis
            String screenshot = webDriverService.takeScreenshot(driver);
            listing.put("screenshot", screenshot);

            return listing;

        } catch (Exception e) {
            log.warn("Failed to extract listing data: {}", e.getMessage());
            return null;
        }
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
     * Extract item name from listing element
     */
    private String extractItemName(WebElement element, String text) {
        try {
            // Try to find title element first
            List<WebElement> titleElements = element.findElements(By.tagName("span"));
            for (WebElement titleElement : titleElements) {
                String title = titleElement.getText();
                if (title.length() > 10 && isPokemonTcgRelated(title)) {
                    return title;
                }
            }

            // Fallback to first line of text
            String[] lines = text.split("\\n");
            if (lines.length > 0) {
                return lines[0];
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
     * Extract seller information
     */
    private String extractSeller(WebElement element) {
        try {
            // Look for seller name elements
            List<WebElement> sellerElements = element.findElements(By.cssSelector("span, div"));
            for (WebElement sellerElement : sellerElements) {
                String text = sellerElement.getText();
                if (text.length() > 3 && text.length() < 50 && !text.contains("$") && !isPokemonTcgRelated(text)) {
                    return text;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract seller info");
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
     * Use LM Studio to analyze the listing
     */
    private void analyzeListingWithAI(Map<String, Object> listing) {
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
                
                // Merge AI analysis results into listing
                if (analysis != null && !analysis.containsKey("error")) {
                    listing.putAll(analysis);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to analyze listing with AI: {}", e.getMessage());
        }
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
}