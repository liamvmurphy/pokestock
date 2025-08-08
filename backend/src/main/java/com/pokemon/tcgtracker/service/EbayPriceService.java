package com.pokemon.tcgtracker.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Service
public class EbayPriceService {
    
    // Global cache for search results to avoid duplicate searches
    private final Map<String, Map<String, Object>> searchResultsCache = new HashMap<>();
    
    /**
     * Inner class to represent a standardized search query
     */
    private static class SearchQuery {
        private final String originalItemName;
        private final String cleanedItemName;
        private final String productType;
        private final String language;
        private final String effectiveSearchString;
        private final List<Map<String, String>> matchingItems;
        
        public SearchQuery(String originalItemName, String cleanedItemName, String productType, String language) {
            this.originalItemName = originalItemName;
            this.cleanedItemName = cleanedItemName;
            this.productType = productType;
            this.language = language;
            
            // Build the effective search string
            StringBuilder sb = new StringBuilder(cleanedItemName);
            if (productType != null && !productType.trim().isEmpty() && !productType.equals("OTHER")) {
                sb.append(" ").append(productType);
            }
            sb.append(" ").append(language);
            this.effectiveSearchString = sb.toString();
            
            // DEBUG: Log the effective search string to identify grouping issues
            logger.info("DEBUG SearchQuery: '{}' → '{}'", originalItemName, effectiveSearchString);
            
            this.matchingItems = new ArrayList<>();
        }
        
        public String getOriginalItemName() { return originalItemName; }
        public String getCleanedItemName() { return cleanedItemName; }
        public String getProductType() { return productType; }
        public String getLanguage() { return language; }
        public String getEffectiveSearchString() { return effectiveSearchString; }
        public List<Map<String, String>> getMatchingItems() { return matchingItems; }
        
        public void addMatchingItem(Map<String, String> item) {
            matchingItems.add(item);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchQuery that = (SearchQuery) o;
            return effectiveSearchString.equals(that.effectiveSearchString);
        }
        
        @Override
        public int hashCode() {
            return effectiveSearchString.hashCode();
        }
    }
    
    private static final Logger logger = LoggerFactory.getLogger(EbayPriceService.class);
    
    @Autowired
    private WebDriverService webDriverService;
    
    @Autowired(required = false)
    private GoogleSheetsService googleSheetsService;
    
    public Map<String, Object> searchEbayPrices(List<Map<String, String>> csvData) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> allSearchResults = new ArrayList<>();
        
        // Log input data info for validation tracking
        logger.info("🔍 Processing eBay price search for {} items from Facebook Marketplace", csvData.size());
        logger.info("📊 Current cache contains {} unique search results", searchResultsCache.size());
        
        // Process in batches of 500
        final int BATCH_SIZE = 500;
        int totalProcessed = 0;
        int cacheHits = 0;
        
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // First, apply any cached results to ALL matching items in the CSV
            if (!searchResultsCache.isEmpty()) {
                List<Map<String, Object>> cachedResults = applyCachedResultsToAllItems(csvData);
                if (!cachedResults.isEmpty()) {
                    cacheHits = cachedResults.size();
                    logger.info("✨ Applied {} cached results to matching items", cacheHits);
                    if (googleSheetsService != null) {
                        try {
                            googleSheetsService.saveEbayPriceData(cachedResults);
                            logger.info("✅ Saved {} cached results to Google Sheets", cachedResults.size());
                        } catch (Exception e) {
                            logger.error("❌ Failed to save cached results to Google Sheets", e);
                        }
                    }
                    allSearchResults.addAll(cachedResults);
                }
            }
            
            // Process items in batches - filter fresh each time to get updated search dates
            boolean hasMoreItems = true;
            int batchNumber = 1;
            
            while (hasMoreItems) {
                // Refresh CSV data from Google Sheets to get the most up-to-date eBay information
                if (googleSheetsService != null) {
                    try {
                        csvData = googleSheetsService.getMarketplaceDataAsCsv();
                        logger.info("🔄 Refreshed CSV data: {} items loaded from Google Sheets", csvData.size());
                    } catch (Exception e) {
                        logger.warn("⚠️ Failed to refresh data from Google Sheets, using existing data: {}", e.getMessage());
                    }
                }
                
                // Filter items that haven't been searched yet (check for eBay median price instead of just search date)
                List<Map<String, String>> itemsToSearch = csvData.stream()
                    .filter(item -> {
                        String ebayMedian = item.get("eBay Median Price");
                        return ebayMedian == null || ebayMedian.trim().isEmpty();
                    })
                    .limit(BATCH_SIZE)
                    .collect(Collectors.toList());
                
                if (itemsToSearch.isEmpty()) {
                    logger.info("✅ No more items to search - all items processed");
                    hasMoreItems = false;
                    break;
                }
                
                logger.info("🔄 Processing batch {}: {} items need searching", batchNumber, itemsToSearch.size());
                
                List<Map<String, Object>> batchResults = processBatchWithCache(driver, wait, itemsToSearch, csvData);
                allSearchResults.addAll(batchResults);
                totalProcessed += batchResults.size();
                
                // Save batch results to Google Sheets immediately
                if (googleSheetsService != null && !batchResults.isEmpty()) {
                    try {
                        googleSheetsService.saveEbayPriceData(batchResults);
                        logger.info("✅ Saved batch of {} results to Google Sheets using name matching", batchResults.size());
                        
                    } catch (Exception e) {
                        logger.error("❌ Failed to save batch to Google Sheets", e);
                    }
                }
                
                batchNumber++;
                
                // Small delay between batches
                logger.info("⏸️ Batch {} complete. Brief pause before next batch...", batchNumber - 1);
                Thread.sleep(2000);
            }
            
            result.put("searchResults", allSearchResults);
            result.put("totalSearched", totalProcessed);
            result.put("batchesProcessed", batchNumber - 1);
            result.put("cacheHits", cacheHits);
            result.put("cacheSize", searchResultsCache.size());
            
        } catch (Exception e) {
            logger.error("Error in eBay price search", e);
            result.put("error", e.getMessage());
        } finally {
            // WebDriver is managed by Spring context, no manual cleanup needed
            logger.info("🏁 eBay price search completed. Processed {} items in batches (Cache hits: {}, Cache size: {})", 
                totalProcessed, cacheHits, searchResultsCache.size());
        }
        
        return result;
    }
    
    /**
     * Apply cached results to ALL items in the CSV that match
     */
    private List<Map<String, Object>> applyCachedResultsToAllItems(List<Map<String, String>> csvData) {
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Map<String, String> item : csvData) {
            // Skip if already has eBay data
            String ebayMedian = item.get("eBay Median Price");
            if (ebayMedian != null && !ebayMedian.trim().isEmpty()) {
                continue;
            }
            
            String itemName = item.get("Item Name");
            if (itemName == null || itemName.trim().isEmpty()) continue;
            
            // Skip if not English or Japanese
            if (!isEnglishOrJapanese(itemName, item.get("Notes"))) {
                continue;
            }
            
            // Generate the cache key for this item
            String searchName = cleanSearchName(itemName);
            String productType = item.get("Product Type");
            String language = detectLanguage(itemName, item.get("Notes"), item.get("Language"));
            
            SearchQuery query = new SearchQuery(itemName, searchName, productType, language);
            String cacheKey = query.getEffectiveSearchString();
            
            // Check if we have cached results for this search
            if (searchResultsCache.containsKey(cacheKey)) {
                Map<String, Object> cachedResult = searchResultsCache.get(cacheKey);
                
                // Create result for this item using cached data
                Map<String, Object> itemResult = new HashMap<>();
                itemResult.put("originalName", item.get("Item Name"));
                itemResult.put("searchName", cacheKey);
                itemResult.put("cleanSearchName", searchName);
                itemResult.put("language", language);
                itemResult.put("set", item.get("Set"));
                itemResult.put("productType", item.get("Product Type"));
                itemResult.put("facebookPrice", item.get("Price"));
                itemResult.put("marketplaceUrl", item.get("Marketplace URL"));
                itemResult.putAll(cachedResult);
                
                results.add(itemResult);
            }
        }
        
        return results;
    }
    
    /**
     * Process a single batch of eBay searches with caching
     */
    private List<Map<String, Object>> processBatchWithCache(WebDriver driver, WebDriverWait wait, List<Map<String, String>> batch, List<Map<String, String>> allCsvData) {
        List<Map<String, Object>> batchResults = new ArrayList<>();
        
        // Group items by their effective search string to avoid duplicate searches
        Map<String, SearchQuery> searchGroups = new LinkedHashMap<>();
        
        // First pass: Create search queries and group items from the batch
        for (Map<String, String> item : batch) {
            String itemName = item.get("Item Name");
            if (itemName == null || itemName.trim().isEmpty()) continue;
            
            // Skip if not English or Japanese
            if (!isEnglishOrJapanese(itemName, item.get("Notes"))) {
                logger.info("Skipping non-English/Japanese item: {}", itemName);
                continue;
            }
            
            // Clean up item name for search
            String searchName = cleanSearchName(itemName);
            
            // Get product type
            String productType = item.get("Product Type");
            
            // Detect language
            String language = detectLanguage(itemName, item.get("Notes"), item.get("Language"));
            
            // Create search query
            SearchQuery query = new SearchQuery(itemName, searchName, productType, language);
            String effectiveSearch = query.getEffectiveSearchString();
            
            // Check if we already have this in cache
            if (searchResultsCache.containsKey(effectiveSearch)) {
                logger.info("🎯 Cache hit for '{}' - skipping eBay search", effectiveSearch);
                continue; // Skip this item as we already have the results
            }
            
            // Group items with identical search strings
            if (searchGroups.containsKey(effectiveSearch)) {
                searchGroups.get(effectiveSearch).addMatchingItem(item);
            } else {
                query.addMatchingItem(item);
                searchGroups.put(effectiveSearch, query);
            }
        }
        
        // Now also add ALL matching items from the entire CSV to each search group
        for (SearchQuery searchQuery : searchGroups.values()) {
            String cacheKey = searchQuery.getEffectiveSearchString();
            
            // Find ALL items in the entire CSV that match this search
            for (Map<String, String> csvItem : allCsvData) {
                // Skip if already has eBay data
                String ebayMedian = csvItem.get("eBay Median Price");
                if (ebayMedian != null && !ebayMedian.trim().isEmpty()) {
                    continue;
                }
                
                // Skip if already in the group
                if (searchQuery.getMatchingItems().contains(csvItem)) {
                    continue;
                }
                
                String itemName = csvItem.get("Item Name");
                if (itemName == null || itemName.trim().isEmpty()) continue;
                
                // Check if this item would generate the same search
                if (!isEnglishOrJapanese(itemName, csvItem.get("Notes"))) {
                    continue;
                }
                
                String searchName = cleanSearchName(itemName);
                String productType = csvItem.get("Product Type");
                String language = detectLanguage(itemName, csvItem.get("Notes"), csvItem.get("Language"));
                
                SearchQuery tempQuery = new SearchQuery(itemName, searchName, productType, language);
                if (tempQuery.getEffectiveSearchString().equals(cacheKey)) {
                    searchQuery.addMatchingItem(csvItem);
                    logger.debug("Added additional matching item from CSV: {}", itemName);
                }
            }
        }
        
        logger.info("📊 Grouped {} items into {} unique NEW searches (excluding cached)", batch.size(), searchGroups.size());
        
        // Second pass: Execute unique searches and apply results to all matching items
        for (SearchQuery searchQuery : searchGroups.values()) {
            List<Map<String, String>> matchingItems = searchQuery.getMatchingItems();
            String searchNameWithLanguage = searchQuery.getEffectiveSearchString();
            
            try {
                // Search eBay sold listings
                String encodedSearch = URLEncoder.encode(searchNameWithLanguage, StandardCharsets.UTF_8.toString());
                String ebayUrl = String.format("https://www.ebay.com.au/sch/i.html?_nkw=%s&_sacat=&LH_Complete=1&LH_Sold=1", encodedSearch);
                
                driver.get(ebayUrl);
                
                // Wait for results to load (reduced for speed)
                Thread.sleep(2500);
                
                // Use the first item's Facebook price for filtering (they should all be similar if grouped correctly)
                String facebookPriceStr = matchingItems.get(0).get("Price");
                double facebookPrice = parseFacebookPrice(facebookPriceStr);
                
                // Extract prices and listing details from eBay
                Map<String, Object> ebayData = extractEbayData(driver, wait, searchNameWithLanguage, facebookPrice);
                List<Double> prices = (List<Double>) ebayData.get("prices");
                List<String> listingDetails = (List<String>) ebayData.get("listingDetails");
                
                // Process eBay results
                Map<String, Object> searchResults = new HashMap<>();
                
                if (!prices.isEmpty()) {
                    // Calculate initial median
                    double initialMedian = calculateMedian(prices);
                    
                    // Filter out prices that are more than 50% above the median
                    double upperLimit = initialMedian * 1.5;
                    List<Double> filteredPrices = new ArrayList<>();
                    List<String> filteredListingDetails = new ArrayList<>();
                    
                    for (int i = 0; i < prices.size(); i++) {
                        if (prices.get(i) <= upperLimit) {
                            filteredPrices.add(prices.get(i));
                            filteredListingDetails.add(listingDetails.get(i));
                        } else {
//                            logger.debug("Filtering out price ${} which is >50% above median ${}",
//                                prices.get(i), initialMedian);
                        }
                    }
                    
                    // Recalculate median with filtered prices
                    double finalMedian = filteredPrices.isEmpty() ? initialMedian : calculateMedian(filteredPrices);
                    
                    // Keep all listing details for viewing, but store first 5 prices separately for top graph
                    List<Double> top5Prices = filteredPrices.size() > 5 
                        ? filteredPrices.subList(0, 5) 
                        : filteredPrices;
                    
                    searchResults.put("medianPrice", finalMedian);
                    searchResults.put("resultCount", filteredPrices.size());
                    searchResults.put("listingDetails", filteredListingDetails);
                    searchResults.put("allPrices", filteredPrices);
                    searchResults.put("top5Prices", top5Prices);
                    searchResults.put("originalCount", prices.size());
                    searchResults.put("filteredCount", prices.size() - filteredPrices.size());
                    
                    // Store in cache for future use
                    searchResultsCache.put(searchNameWithLanguage, new HashMap<>(searchResults));
                } else {
                    searchResults.put("listingDetails", new ArrayList<>());
                    searchResults.put("allPrices", new ArrayList<>());
                    searchResults.put("error", "No sold listings found");
                }
                
                // Apply results to ALL matching items
                for (Map<String, String> item : matchingItems) {
                    Map<String, Object> itemResult = new HashMap<>();
                    
                    // Item-specific data
                    itemResult.put("originalName", item.get("Item Name"));
                    itemResult.put("searchName", searchNameWithLanguage);
                    itemResult.put("cleanSearchName", searchQuery.getCleanedItemName());
                    itemResult.put("language", searchQuery.getLanguage());
                    itemResult.put("set", item.get("Set"));
                    itemResult.put("productType", item.get("Product Type"));
                    itemResult.put("facebookPrice", item.get("Price"));
                    itemResult.put("marketplaceUrl", item.get("Marketplace URL"));
                    
                    // Copy all eBay search results
                    itemResult.putAll(searchResults);
                    
                    batchResults.add(itemResult);
                }
                
                logger.info("✅ Applied eBay results to {} items with search '{}' (cached for future use)", matchingItems.size(), searchNameWithLanguage);
                
                // Small delay between searches (reduced for speed)
                Thread.sleep(2000);
                
            } catch (Exception e) {
                logger.error("Error searching for: {}", searchNameWithLanguage, e);
                
                // Apply error to all matching items
                for (Map<String, String> item : matchingItems) {
                    Map<String, Object> itemResult = new HashMap<>();
                    itemResult.put("originalName", item.get("Item Name"));
                    itemResult.put("searchName", searchNameWithLanguage);
                    itemResult.put("error", e.getMessage());
                    batchResults.add(itemResult);
                }
            }
        }
        
        return batchResults;
    }
    
    private Map<String, Object> extractEbayData(WebDriver driver, WebDriverWait wait, String searchTerm, double facebookPrice) {
        List<Double> prices = new ArrayList<>();
        List<String> listingDetails = new ArrayList<>();
        
        try {
            // Try both old and new eBay HTML structures
            List<WebElement> listingElements = new ArrayList<>();
            
            // Try new structure first
            try {
                List<WebElement> newStructureElements = driver.findElements(By.cssSelector("li[data-listingid]"));
                if (!newStructureElements.isEmpty()) {
                    listingElements = newStructureElements;
//                    logger.info("Using new eBay HTML structure");
                }
            } catch (Exception e) {
                logger.debug("New structure not found, trying old structure");
            }
            
            // Fallback to old structure
            if (listingElements.isEmpty()) {
                try {
                    WebElement resultsDiv = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.className("srp-river-results")));
                    listingElements = resultsDiv.findElements(By.cssSelector(".s-item"));
                    logger.info("Using old eBay HTML structure");
                } catch (Exception e) {
                    logger.error("Could not find listings with either structure");
                    return createEmptyResult();
                }
            }
            
            // Limit to 15 results per item (sufficient for median calculation)
            int resultCount = 0;
            final int MAX_RESULTS = 15;
            
            for (WebElement listingElement : listingElements) {
                if (resultCount >= MAX_RESULTS) {
                    logger.info("Reached maximum of {} results per item", MAX_RESULTS);
                    break;
                }
                
                try {
                    // Extract price - try multiple selectors
                    WebElement priceElement = null;
                    String priceText = "";
                    
                    // Try new structure price selector
                    try {
                        priceElement = listingElement.findElement(By.cssSelector(".s-card__price"));
                        priceText = priceElement.getText();
                    } catch (Exception e) {
                        // Try old structure price selector
                        try {
                            priceElement = listingElement.findElement(By.cssSelector(".s-item__price"));
                            priceText = priceElement.getText();
                        } catch (Exception e2) {
                            logger.debug("Could not find price element in listing");
                            continue;
                        }
                    }
                    
                    // Skip entries that contain location indicators
                    if (priceText.toLowerCase().contains("from united states") ||
                        priceText.toLowerCase().contains("from ") ||
                        priceText.toLowerCase().contains("shipping") ||
                        priceText.toLowerCase().contains("delivery")) {
                        logger.debug("Skipping location/shipping entry: {}", priceText);
                        continue;
                    }
                    
                    Double price = parsePrice(priceText);
                    if (price == null || price <= 0) {
                        continue; // Skip invalid prices
                    }
                    
                    // Extract title - try multiple selectors
                    WebElement titleElement = null;
                    String title = "";
                    
                    // Try new structure title selector
                    try {
                        titleElement = listingElement.findElement(By.cssSelector(".s-card__title .su-styled-text"));
                        title = titleElement.getText();
                    } catch (Exception e) {
                        // Try old structure title selector
                        try {
                            titleElement = listingElement.findElement(By.cssSelector(".s-item__title"));
                            title = titleElement.getText();
                        } catch (Exception e2) {
                            logger.debug("Could not find title element in listing");
                            continue;
                        }
                    }
                    
                    // Skip if title contains unwanted text
                    if (title.toLowerCase().contains("shop on ebay") || 
                        title.toLowerCase().contains("new listing") ||
                        title.toLowerCase().contains("empty")) {
                        continue;
                    }
                    
                    // PSA filtering: If search doesn't include "PSA", exclude PSA listings
                    boolean searchIncludesPSA = searchTerm.toLowerCase().contains("psa");
                    boolean titleIncludesPSA = title.toLowerCase().contains("psa");
                    
                    if (!searchIncludesPSA && titleIncludesPSA) {
//                        logger.debug("Skipping PSA listing since search doesn't include PSA: {}", title);
                        continue;
                    }
                    
                    // Skip multiple quantity listings (sets, bundles, lots, etc)
                    if (isMultipleQuantityListing(title)) {
//                        logger.debug("Skipping multiple quantity listing: {}", title);
                        continue;
                    }
                    
                    // Skip Pokemon Center/Centre exclusive items (premium versions)
                    if (isPokemonCenterListing(title)) {
//                        logger.debug("Skipping Pokemon Center exclusive listing: {}", title);
                        continue;
                    }
                    
                    // Skip listings with multiple different products
                    if (hasMultipleProducts(title)) {
//                        logger.debug("Skipping multiple products listing: {}", title);
                        continue;
                    }
                    
                    // Skip listings with more than 70% price discrepancy from Facebook price
                    if (facebookPrice > 0 && isPriceDiscrepancyTooHigh(price, facebookPrice)) {
//                        logger.debug("Skipping listing with high price discrepancy: eBay ${}, Facebook ${} ({}% diff): {}",
//                                   price, facebookPrice, calculatePriceDiscrepancyPercent(price, facebookPrice), title);
                        continue;
                    }
                    
                    // Extract URL - try multiple selectors
                    WebElement linkElement = null;
                    String url = "";
                    
                    // Try new structure link selector
                    try {
                        linkElement = listingElement.findElement(By.cssSelector("a.su-link[href*='/itm/']"));
                        url = linkElement.getAttribute("href");
                    } catch (Exception e) {
                        // Try old structure link selector
                        try {
                            linkElement = listingElement.findElement(By.cssSelector(".s-item__link"));
                            url = linkElement.getAttribute("href");
                        } catch (Exception e2) {
                            logger.debug("Could not find URL element in listing");
                            continue;
                        }
                    }
                    
                    // Clean URL (remove tracking parameters)
                    url = cleanEbayUrl(url);
                    
                    // Format as requested: {EbayListingTitle}_{EbaySoldPrice} with embedded URL
                    String listingDetail = String.format("[%s_$%.2f](%s)", 
                        title.trim(), price, url);
                    
                    prices.add(price);
                    listingDetails.add(listingDetail);
                    resultCount++; // Increment counter for successful extractions
                    
                } catch (Exception e) {
                    logger.debug("Could not extract data from listing element: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Error extracting eBay data", e);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("prices", prices);
        result.put("listingDetails", listingDetails);
        return result;
    }
    
    private Map<String, Object> createEmptyResult() {
        Map<String, Object> result = new HashMap<>();
        result.put("prices", new ArrayList<>());
        result.put("listingDetails", new ArrayList<>());
        return result;
    }
    
    private String cleanEbayUrl(String url) {
        try {
            // Remove common tracking parameters
            if (url.contains("?")) {
                String baseUrl = url.split("\\?")[0];
                // Keep essential parameters if any, but remove tracking
                return baseUrl;
            }
            return url;
        } catch (Exception e) {
            return url; // Return original if cleaning fails
        }
    }
    
    private Double parsePrice(String priceText) {
        try {
            // Skip if contains location indicators
            if (priceText.toLowerCase().contains("from ") || 
                priceText.toLowerCase().contains("shipping") ||
                priceText.toLowerCase().contains("postage") ||
                priceText.toLowerCase().contains("delivery")) {
                return null;
            }
            
            // Remove currency symbols, "AU", "$", and other non-numeric characters, but keep numbers, dots, and commas
            String cleanedPrice = priceText.replaceAll("(?i)(AU\\s*\\$?|\\$|USD|AUD|CAD|GBP|EUR)", "");
            cleanedPrice = cleanedPrice.replaceAll("[^0-9.,]", "");
            
            // Handle price ranges (take the first price)
            if (cleanedPrice.contains("to")) {
                cleanedPrice = cleanedPrice.split("to")[0].trim();
            }
            
            // Skip if empty after cleaning
            if (cleanedPrice.trim().isEmpty()) {
                logger.debug("Price text empty after cleaning: '{}'", priceText);
                return null;
            }
            
            // Remove commas and parse
            cleanedPrice = cleanedPrice.replace(",", "");
            double price = Double.parseDouble(cleanedPrice);
            
            // Sanity check - prices should be reasonable (between $1 and $10000)
            if (price < 1.0 || price > 10000.0) {
                logger.debug("Price outside reasonable range: {}", price);
                return null;
            }
            
//            logger.debug("Successfully parsed price: {} from '{}'", price, priceText);
            return price;
        } catch (Exception e) {
            logger.debug("Failed to parse price '{}': {}", priceText, e.getMessage());
            return null;
        }
    }
    
    private String cleanSearchName(String itemName) {
        // Remove common FB marketplace noise
        itemName = itemName.replaceAll("(?i)\\bmake\\s*an\\s*offer\\b", "");
        itemName = itemName.replaceAll("(?i)\\bfirm\\b", "");
        itemName = itemName.replaceAll("(?i)\\bono\\b", "");
        itemName = itemName.replaceAll("(?i)\\bnegotiable\\b", "");
        itemName = itemName.replaceAll("(?i)\\bmelb\\w*\\b", "");
        itemName = itemName.replaceAll("(?i)\\bpick\\s*up\\b", "");
        itemName = itemName.replaceAll("(?i)\\bpostage\\b", "");
        
        // Clean up extra spaces
        itemName = itemName.replaceAll("\\s+", " ").trim();
        
        return itemName;
    }
    
    private String detectLanguage(String itemName, String notes, String languageColumn) {
        // First priority: Check notes and item name for explicit language indicators
        String combined = (itemName + " " + (notes != null ? notes : "")).toLowerCase();
        
        // Check for explicit language indicators in notes/item name
        if (combined.contains("japanese") || 
            combined.contains("japan") || 
            combined.contains("jp") ||
            combined.contains("jpn")) {
            return "Japanese";
        }
        
        // Second priority: Use Language column if available and valid
        if (languageColumn != null && !languageColumn.trim().isEmpty()) {
            String langLower = languageColumn.trim().toLowerCase();
            if (langLower.contains("japanese") || langLower.contains("japan") || 
                langLower.equals("jp") || langLower.equals("jpn")) {
                return "Japanese";
            } else if (langLower.contains("english") || langLower.equals("en") || 
                       langLower.equals("eng")) {
                return "English";
            }
        }
        
        // Default to English
        return "English";
    }
    
    private boolean isEnglishOrJapanese(String itemName, String notes) {
        String combined = (itemName + " " + (notes != null ? notes : "")).toLowerCase();
        
        // Check for language indicators
        boolean isJapanese = combined.contains("japanese") || 
                           combined.contains("japan") || 
                           combined.contains("jp") ||
                           combined.contains("jpn");
                           
        boolean isOtherLanguage = combined.contains("korean") || 
                                combined.contains("chinese") || 
                                combined.contains("spanish") ||
                                combined.contains("french") ||
                                combined.contains("german") ||
                                combined.contains("italian");
        
        // If explicitly marked as non-English/Japanese, exclude
        if (isOtherLanguage && !isJapanese) {
            return false;
        }
        
        // Otherwise assume English or Japanese
        return true;
    }
    
    
    private double calculateMedian(List<Double> prices) {
        List<Double> sorted = new ArrayList<>(prices);
        Collections.sort(sorted);
        int size = sorted.size();
        
        if (size % 2 == 0) {
            return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
        } else {
            return sorted.get(size / 2);
        }
    }
    
    /**
     * Check if listing title indicates multiple quantities (sets, bundles, lots, etc)
     */
    private boolean isMultipleQuantityListing(String title) {
        if (title == null) return false;
        
        String lowerTitle = title.toLowerCase();
        
        // Pattern matching for multiple quantities
        // Check for "set of X", "X pack", "lot of X", etc.
        if (lowerTitle.matches(".*\\bset of \\d+.*") ||
            lowerTitle.matches(".*\\b\\d+\\s*pack\\b.*") ||
            lowerTitle.matches(".*\\b\\d+-pack\\b.*") ||
            lowerTitle.matches(".*\\blot of \\d+.*") ||
            lowerTitle.matches(".*\\b\\d+x\\b.*") ||
            lowerTitle.matches(".*\\bx\\d+\\b.*") ||
            lowerTitle.matches("^\\d+x\\s+.*")) {
            return true;
        }
        
        // Check for common multiple quantity keywords
        String[] multipleKeywords = {
            "bundle", "set of", "pack of", "lot of", "display",
            "case of", "box of", "collection", "bulk",
            "multiple", "combo", "package deal", "sealed case",
            // Specific patterns from your examples
            "display box", "factory sealed display", "sealed display"
        };
        
        for (String keyword : multipleKeywords) {
            if (lowerTitle.contains(keyword)) {
                return true;
            }
        }
        
        // Check for quantity indicators at the beginning (e.g., "2x Pokemon", "5x Elite")
        if (lowerTitle.matches("^\\d+\\s*x\\s+.*")) {
            return true;
        }
        
        // Check for parenthetical quantities (e.g., "(Set of 10)", "(5 Pack)")
        if (lowerTitle.matches(".*\\(.*\\d+.*\\).*") && 
            (lowerTitle.contains("set") || lowerTitle.contains("pack") || 
             lowerTitle.contains("lot") || lowerTitle.contains("bundle"))) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if listing is a Pokemon Center/Centre exclusive (premium versions)
     */
    private boolean isPokemonCenterListing(String title) {
        if (title == null) return false;
        
        String lowerTitle = title.toLowerCase();
        
        // Check for Pokemon Center/Centre variations
        return lowerTitle.contains("pokemon center") ||
               lowerTitle.contains("pokemon centre") ||
               lowerTitle.contains("pokemon-center") ||
               lowerTitle.contains("pokemon-centre") ||
               lowerTitle.contains("pokémon center") ||
               lowerTitle.contains("pokémon centre") ||
               lowerTitle.contains(" pc elite") || // PC often means Pokemon Center
               lowerTitle.contains(" pc etb") ||
               lowerTitle.contains("center edition") ||
               lowerTitle.contains("centre edition") ||
               lowerTitle.contains("center exclusive") ||
               lowerTitle.contains("centre exclusive");
    }
    
    /**
     * Check if listing contains multiple different products (e.g., "Black Bolt and White Flare")
     */
    private boolean hasMultipleProducts(String title) {
        if (title == null) return false;
        
        String lowerTitle = title.toLowerCase();
        
        // Common patterns that indicate multiple different products
        // Check for "and" between product names
        if (lowerTitle.matches(".*\\b\\w+\\s+(and|&)\\s+\\w+\\s+(etb|elite|trainer|box|booster|tin).*")) {
            // But allow single products with "and" in their name (e.g., "Black and White Base Set")
            // Check if it mentions "both" or "set" which confirms multiple items
            if (lowerTitle.contains("both") || 
                lowerTitle.contains("set sealed") || 
                lowerTitle.contains("set of")) {
                return true;
            }
            
            // Count distinct product indicators before and after "and/&"
            String[] parts = lowerTitle.split("\\s+(and|&)\\s+");
            if (parts.length >= 2) {
                // If both parts contain product-specific words, it's likely multiple products
                boolean firstHasProduct = containsProductName(parts[0]);
                boolean secondHasProduct = containsProductName(parts[1]);
                if (firstHasProduct && secondHasProduct) {
                    return true;
                }
            }
        }
        
        // Check for comma-separated lists of products
        if (lowerTitle.matches(".*,.*,.*") && 
            (lowerTitle.contains("etb") || lowerTitle.contains("elite") || 
             lowerTitle.contains("trainer") || lowerTitle.contains("booster"))) {
            return true;
        }
        
        // Specific indicators of multiple products
        String[] multiProductIndicators = {
            "both etb", "both elite", "all three", "all 3",
            "complete set", "full set"
        };
        
        for (String indicator : multiProductIndicators) {
            if (lowerTitle.contains(indicator)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to check if a string contains product-specific names
     */
    private boolean containsProductName(String text) {
        String[] productIndicators = {
            "bolt", "flare", "forces", "fates", "flames", "rift", "crown",
            "zenith", "fusion", "voltage", "silver", "gold", "base",
            "origins", "guardians", "bonds", "clash", "siege",
            "ultra", "shining", "hidden", "vivid", "battle", "roaring"
        };
        
        for (String indicator : productIndicators) {
            if (text.contains(indicator)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Parse Facebook price string to double
     */
    private double parseFacebookPrice(String priceStr) {
        if (priceStr == null || priceStr.trim().isEmpty()) {
            return 0.0;
        }
        
        try {
            // Remove currency symbols, spaces, and other non-numeric characters except dots and commas
            String cleanedPrice = priceStr.replaceAll("[^\\d.,]", "");
            
            // Handle comma as thousands separator (e.g., "1,500.00")
            if (cleanedPrice.contains(",") && cleanedPrice.contains(".")) {
                cleanedPrice = cleanedPrice.replace(",", "");
            }
            // Handle comma as decimal separator (e.g., "15,50")
            else if (cleanedPrice.contains(",") && !cleanedPrice.contains(".")) {
                cleanedPrice = cleanedPrice.replace(",", ".");
            }
            
            return Double.parseDouble(cleanedPrice);
        } catch (NumberFormatException e) {
            logger.debug("Failed to parse Facebook price '{}': {}", priceStr, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Check if price discrepancy is too high (>70%)
     * Uses the lower price as the base for percentage calculation to be more lenient
     */
    private boolean isPriceDiscrepancyTooHigh(double ebayPrice, double facebookPrice) {
        if (ebayPrice <= 0 || facebookPrice <= 0) {
            return false; // Don't filter if either price is invalid
        }
        
        double discrepancyPercent = calculatePriceDiscrepancyPercent(ebayPrice, facebookPrice);
        return discrepancyPercent > 70.0;
    }
    
    /**
     * Calculate price discrepancy percentage
     * Uses the lower price as the base to be more lenient with matches
     */
    private double calculatePriceDiscrepancyPercent(double ebayPrice, double facebookPrice) {
        if (ebayPrice <= 0 || facebookPrice <= 0) {
            return 0.0;
        }
        
        // Use the lower price as the base for percentage calculation
        double basePrice = Math.min(ebayPrice, facebookPrice);
        double higherPrice = Math.max(ebayPrice, facebookPrice);
        
        return ((higherPrice - basePrice) / basePrice) * 100.0;
    }
}