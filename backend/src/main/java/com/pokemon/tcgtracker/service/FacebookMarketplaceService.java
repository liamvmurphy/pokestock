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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.openqa.selenium.JavascriptExecutor;
import java.util.stream.Collectors;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

@Slf4j
@Service
@RequiredArgsConstructor
public class FacebookMarketplaceService {

    private final WebDriverService webDriverService;
    private final LMStudioService lmStudioService;
    private final GoogleSheetsService googleSheetsService;
    private final ConfigurationService configurationService;
    
    // Smart caching for processed URLs to avoid repeated Google Sheets calls
    private final Map<String, LocalDateTime> urlCache = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> searchTermUrlCache = new ConcurrentHashMap<>();
    private LocalDateTime cacheLastUpdated = null;
    private static final long CACHE_REFRESH_HOURS = 1; // Refresh cache every hour
    
    // Configurable timing parameters for optimization
    private static final Duration DEFAULT_PAGE_LOAD_TIMEOUT = Duration.ofSeconds(6);  // Reduced by 40% from 10s
    private static final Duration DEFAULT_SCROLL_WAIT_TIMEOUT = Duration.ofSeconds(3);  // Reduced by 40% from 5s
    private static final Duration DEFAULT_LISTING_LOAD_TIMEOUT = Duration.ofSeconds(5);  // Reduced by 37.5% from 8s
    private static final Duration DEFAULT_ELEMENT_WAIT_TIMEOUT = Duration.ofSeconds(1);  // Reduced by 50% from 2s

    private static final String MARKETPLACE_BASE_URL = "https://www.facebook.com/marketplace";
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$([0-9,]+(?:\\.[0-9]{2})?)");
    
    // Pokemon TCG search terms - includes high-value discontinued sets
    private static final List<String> SEARCH_TERMS = Arrays.asList(
        "Pokemon ETB",
        "Pokemon Elite Trainer Box",
        "Pokemon Booster Box",
        "Pokemon Evolving Skies",
        "Pokemon Lost Origin",
        "Pokemon Brilliant Stars",
        "Pokemon GO Cards",
        "Pokemon Hidden Fates",
        "Pokemon Celebrations"
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
            
            // Note: eBay data is now stored in the same sheet, so no need to clear it separately
            
            // Process all search terms sequentially in a single tab
            for (int i = 0; i < SEARCH_TERMS.size(); i++) {
                String searchTerm = SEARCH_TERMS.get(i);
                try {
                    log.info("📍 Processing search {}/{}: '{}'", i + 1, SEARCH_TERMS.size(), searchTerm);
                    
                    String searchUrl = buildSearchUrl(searchTerm);
                    driver.get(searchUrl);
                    
                    // Wait for page to load dynamically
                    waitForPageLoad(driver);
                    
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
     * Process search results with optimized navigation - collect all URLs first, then process in batches
     * This method reduces back-and-forth navigation for better performance
     */
    private List<Map<String, Object>> processSearchResultsWithNavigation(WebDriver driver, String searchTerm) {
        List<Map<String, Object>> listings = new ArrayList<>();
        
        try {
            // Target 50 items per search
            final int TARGET_ITEMS = 50;
            List<String> allListingUrls = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int scrollAttempts = 0;
            final int MAX_SCROLL_ATTEMPTS = 15; // Prevent infinite scrolling
            
            log.info("🎯 Target: Collect {} items for search term '{}'", TARGET_ITEMS, searchTerm);
            
            // Continue scrolling until we have enough unique URLs or hit max attempts
            while (allListingUrls.size() < TARGET_ITEMS && scrollAttempts < MAX_SCROLL_ATTEMPTS) {
                // Scroll to load more listings
                webDriverService.humanLikeScroll(driver);
                waitForScrollContent(driver);
                
                // Collect new listing URLs
                List<String> newUrls = collectListingUrls(driver);
                
                // Add only unique URLs
                int beforeCount = allListingUrls.size();
                for (String url : newUrls) {
                    if (seenUrls.add(url)) {
                        allListingUrls.add(url);
                    }
                }
                int afterCount = allListingUrls.size();
                
                log.info("📜 Scroll attempt {}: Found {} new unique URLs. Total: {}/{}", 
                        scrollAttempts + 1, afterCount - beforeCount, afterCount, TARGET_ITEMS);
                
                // If we didn't find any new URLs, we might have reached the end
                if (afterCount == beforeCount) {
                    log.info("⚠️ No new listings found on scroll. May have reached end of results.");
                    webDriverService.humanDelay(); // Wait a bit before next attempt
                }
                
                scrollAttempts++;
                
                // Scroll to bottom to trigger lazy loading
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
                Thread.sleep(800); // Wait for content to load
            }
            
            log.info("📋 Collected {} total listing URLs for '{}' after {} scroll attempts", 
                    allListingUrls.size(), searchTerm, scrollAttempts);
            
            // Filter and deduplicate URLs upfront - use all collected URLs up to our target
            List<String> urlsToProcess = filterAndDeduplicateUrls(allListingUrls, TARGET_ITEMS);
            log.info("📝 After filtering: {} URLs to process for '{}'", urlsToProcess.size(), searchTerm);
            
            // Process URLs in batch without returning to search results each time
            int processedCount = 0;
            for (String listingUrl : urlsToProcess) {
                try {
                    String cleanUrl = cleanMarketplaceUrl(listingUrl);
                    log.info("🔗 Processing listing {}/{}: {}", processedCount + 1, urlsToProcess.size(), cleanUrl);
                    
                    // Navigate to the individual listing page
                    driver.get(cleanUrl);
                    waitForListingPageLoad(driver);
                    
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
                    
                    // Extract and analyze listing data
                    Map<String, Object> listing = extractListingDataFromPage(driver, cleanUrl, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        List<Map<String, Object>> itemsFromListing = analyzeListingWithAI(listing);
                        
                        if (!itemsFromListing.isEmpty()) {
                            listings.addAll(itemsFromListing);
                            processedCount++;
                            
                            // Save all items to Google Sheets
                            googleSheetsService.addMarketplaceListings(itemsFromListing);
                            
                            log.info("✅ Successfully processed listing {}/{} with {} items from: {}", 
                                    processedCount, urlsToProcess.size(), itemsFromListing.size(), cleanUrl);
                        } else {
                            log.warn("⚠️ No items extracted from listing: {}", cleanUrl);
                        }
                    }
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to process listing: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Failed to process search results for term '{}': {}", searchTerm, e.getMessage());
        }
        
        return listings;
    }
    
    /**
     * Filter URLs and remove duplicates upfront to avoid unnecessary processing
     */
    private List<String> filterAndDeduplicateUrls(List<String> urls, int maxItems) {
        Set<String> uniqueUrls = new LinkedHashSet<>();
        List<String> filteredUrls = new ArrayList<>();
        
        for (String url : urls) {
            if (filteredUrls.size() >= maxItems) {
                break;
            }
            
            String cleanUrl = cleanMarketplaceUrl(url);
            
            // Skip if already seen (deduplication)
            if (uniqueUrls.contains(cleanUrl)) {
                continue;
            }
            
            // Check if URL should be processed (caching check)
            if (!shouldProcessUrl(cleanUrl)) {
                log.debug("⏭️ Skipping URL (recently updated): {}", cleanUrl);
                continue;
            }
            
            uniqueUrls.add(cleanUrl);
            filteredUrls.add(cleanUrl);
        }
        
        return filteredUrls;
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
     * Check if a URL should be processed based on 7-day rule with smart caching
     */
    private boolean shouldProcessUrl(String url) {
        try {
            // Check cache first
            if (isUrlCachedAndRecent(url)) {
                LocalDateTime lastUpdate = urlCache.get(url);
                long daysSinceUpdate = ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now());
                return daysSinceUpdate >= 7;
            }
            
            // Not in cache, check Google Sheets and cache the result
            boolean shouldProcess = googleSheetsService.shouldRefreshUrl(url);
            
            // Cache the result with current timestamp if URL exists in sheets
            if (!shouldProcess) {
                urlCache.put(url, LocalDateTime.now().minusDays(1)); // Mark as recently processed
            }
            
            return shouldProcess;
        } catch (Exception e) {
            log.warn("Failed to check URL refresh status, will process: {}", e.getMessage());
            return true; // Process if we can't check
        }
    }
    
    /**
     * Check if URL is in cache and cache is still fresh
     */
    private boolean isUrlCachedAndRecent(String url) {
        if (cacheLastUpdated == null || 
            ChronoUnit.HOURS.between(cacheLastUpdated, LocalDateTime.now()) >= CACHE_REFRESH_HOURS) {
            refreshUrlCache();
        }
        return urlCache.containsKey(url);
    }
    
    /**
     * Refresh the URL cache by loading recent URLs from Google Sheets
     */
    private void refreshUrlCache() {
        try {
            log.info("🔄 Refreshing URL cache from Google Sheets");
            urlCache.clear();
            
            // Get all recent listings from sheets to populate cache
            List<Map<String, Object>> recentListings = googleSheetsService.getAllMarketplaceListings();
            
            for (Map<String, Object> listing : recentListings) {
                String url = (String) listing.get("url");
                Object dateObj = listing.get("dateFound");
                
                if (url != null && dateObj != null) {
                    LocalDateTime date = parseDate(dateObj);
                    if (date != null) {
                        urlCache.put(cleanMarketplaceUrl(url), date);
                    }
                }
            }
            
            cacheLastUpdated = LocalDateTime.now();
            log.info("✅ URL cache refreshed with {} entries", urlCache.size());
            
        } catch (Exception e) {
            log.warn("Failed to refresh URL cache: {}", e.getMessage());
            cacheLastUpdated = LocalDateTime.now(); // Prevent constant retries
        }
    }
    
    /**
     * Parse date object from various formats
     */
    private LocalDateTime parseDate(Object dateObj) {
        try {
            if (dateObj instanceof Date) {
                return ((Date) dateObj).toInstant()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime();
            } else if (dateObj instanceof LocalDateTime) {
                return (LocalDateTime) dateObj;
            } else if (dateObj instanceof String) {
                return LocalDateTime.parse((String) dateObj);
            }
        } catch (Exception e) {
            log.debug("Failed to parse date: {}", dateObj);
        }
        return null;
    }
    
    /**
     * Wait for page to load dynamically instead of fixed sleep
     */
    private void waitForPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, DEFAULT_PAGE_LOAD_TIMEOUT);
            wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").equals("complete"));
        } catch (Exception e) {
            log.debug("Page load wait timed out after {}ms, continuing: {}", 
                     DEFAULT_PAGE_LOAD_TIMEOUT.toMillis(), e.getMessage());
        }
    }
    
    /**
     * Wait for content to load after scrolling
     */
    private void waitForScrollContent(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, DEFAULT_SCROLL_WAIT_TIMEOUT);
            // Wait for any marketplace items to be present
            wait.until(ExpectedConditions.presenceOfElementLocated(
                By.cssSelector("a[href*='/marketplace/item/'], div[data-testid='marketplace-item']")));
        } catch (Exception e) {
            log.debug("Scroll content wait timed out after {}ms, continuing: {}", 
                     DEFAULT_SCROLL_WAIT_TIMEOUT.toMillis(), e.getMessage());
        }
    }
    
    /**
     * Wait for listing page to load with specific elements
     */
    private void waitForListingPageLoad(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, DEFAULT_LISTING_LOAD_TIMEOUT);
            // Wait for either the listing content or login requirement
            wait.until(driver1 -> 
                driver1.getCurrentUrl().contains("/marketplace/item/") ||
                isLoginRequired(driver1) ||
                driver1.getPageSource().contains("marketplace"));
        } catch (Exception e) {
            log.debug("Listing page load wait timed out after {}ms, continuing: {}", 
                     DEFAULT_LISTING_LOAD_TIMEOUT.toMillis(), e.getMessage());
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
            
            // Wait for page to load dynamically
            waitForPageLoad(driver);
            
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
     * Optimized version that processes URLs in batch without returning to search results
     */
    private List<Map<String, Object>> processSearchResultsWithNavigation(WebDriver driver, String searchTerm, int maxItems) {
        List<Map<String, Object>> listings = new ArrayList<>();
        
        try {
            // Scroll to load more listings
            webDriverService.humanLikeScroll(driver);
            waitForScrollContent(driver);
            
            // Find all listing URLs on the search results page
            List<String> listingUrls = collectListingUrls(driver);
            log.info("📋 Found {} listing URLs for '{}'", listingUrls.size(), searchTerm);
            
            // Filter and deduplicate URLs upfront with custom maxItems
            List<String> urlsToProcess = filterAndDeduplicateUrls(listingUrls, maxItems);
            log.info("📝 After filtering: {} URLs to process for '{}'", urlsToProcess.size(), searchTerm);
            
            // Process URLs in batch without returning to search results each time
            int processedCount = 0;
            for (String listingUrl : urlsToProcess) {
                try {
                    String cleanUrl = cleanMarketplaceUrl(listingUrl);
                    log.info("🔗 Processing listing {}/{}: {}", processedCount + 1, urlsToProcess.size(), cleanUrl);
                    
                    // Navigate to the individual listing page
                    driver.get(cleanUrl);
                    waitForListingPageLoad(driver);
                    
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
                    
                    // Extract and analyze listing data
                    Map<String, Object> listing = extractListingDataFromPage(driver, cleanUrl, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        List<Map<String, Object>> itemsFromListing = analyzeListingWithAI(listing);
                        
                        if (!itemsFromListing.isEmpty()) {
                            listings.addAll(itemsFromListing);
                            processedCount++;
                            
                            // Save all items to Google Sheets
                            googleSheetsService.addMarketplaceListings(itemsFromListing);
                            
                            log.info("✅ Successfully processed listing {}/{} with {} items from: {}", 
                                    processedCount, urlsToProcess.size(), itemsFromListing.size(), cleanUrl);
                        } else {
                            log.warn("⚠️ No items extracted from listing: {}", cleanUrl);
                        }
                    }
                    
                } catch (Exception e) {
                    log.warn("⚠️ Failed to process listing: {}", e.getMessage());
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
                            itemListing.put("language", item.getOrDefault("language", "English"));
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
                    
                    // Wait for element to be clickable
                    WebDriverWait shortWait = new WebDriverWait(driver, DEFAULT_ELEMENT_WAIT_TIMEOUT);
                    shortWait.until(ExpectedConditions.elementToBeClickable(seeMoreButton));
                    
                    // Try regular click first
                    seeMoreButton.click();
                    
                    // Wait for expansion to complete by checking for content change
                    shortWait.until(driver1 -> driver1.getPageSource().length() > driver.getPageSource().length() * 0.95);
                    
                    log.info("✅ Successfully clicked 'See more' button");
                    
                } catch (Exception e) {
                    log.debug("Regular click failed, trying JavaScript click: {}", e.getMessage());
                    try {
                        // Fallback to JavaScript click
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", seeMoreButton);
                        
                        // Brief wait for JavaScript click to take effect
                        WebDriverWait jsWait = new WebDriverWait(driver, DEFAULT_ELEMENT_WAIT_TIMEOUT);
                        final WebElement finalSeeMoreButton = seeMoreButton; // Make it final for lambda
                        jsWait.until(driver1 -> !finalSeeMoreButton.isDisplayed() || 
                                    driver1.getPageSource().contains("See less"));
                        
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