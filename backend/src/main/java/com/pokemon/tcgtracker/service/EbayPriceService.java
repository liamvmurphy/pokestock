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
    private static final Logger logger = LoggerFactory.getLogger(EbayPriceService.class);
    
    @Autowired
    private WebDriverService webDriverService;
    
    @Autowired(required = false)
    private GoogleSheetsService googleSheetsService;
    
    public Map<String, Object> searchEbayPrices(List<Map<String, String>> csvData) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> searchResults = new ArrayList<>();
        
        // Log input data info for validation tracking
        logger.info("🔍 Processing eBay price search for {} items from Facebook Marketplace", csvData.size());
        
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            // Fresh search - don't check existing searches, always perform new search
            
            // Remove search limit - process all items
            int searchCount = 0;
            
            for (Map<String, String> item : csvData) {
                String itemName = item.get("Item Name");
                if (itemName == null || itemName.trim().isEmpty()) continue;
                
                // Clean up item name for search
                String searchName = cleanSearchName(itemName);
                
                // Add product type if present
                String productType = item.get("Product Type");
                if (productType != null && !productType.trim().isEmpty() && !productType.equals("OTHER")) {
                    searchName = searchName + " " + productType;
                }
                
                // Detect language and add to search
                String language = detectLanguage(itemName, item.get("Notes"));
                String searchNameWithLanguage = searchName + " " + language;
                
                // No search limit - process all items - fresh search every time
                
                // Skip if not English or Japanese
                if (!isEnglishOrJapanese(itemName, item.get("Notes"))) {
                    logger.info("Skipping non-English/Japanese item: {}", itemName);
                    continue;
                }
                
                Map<String, Object> itemResult = new HashMap<>();
                itemResult.put("originalName", itemName);
                itemResult.put("searchName", searchNameWithLanguage);
                itemResult.put("cleanSearchName", searchName);
                itemResult.put("language", language);
                itemResult.put("set", item.get("Set"));
                itemResult.put("productType", item.get("Product Type"));
                itemResult.put("facebookPrice", item.get("Price"));
                
                try {
                    // Search eBay sold listings with language included
                    String encodedSearch = URLEncoder.encode(searchNameWithLanguage, StandardCharsets.UTF_8.toString());
                    String ebayUrl = String.format("https://www.ebay.com.au/sch/i.html?_nkw=%s&_sacat=&LH_Complete=1&LH_Sold=1", encodedSearch);
                    
                    logger.info("Searching eBay for: {}", searchNameWithLanguage);
                    driver.get(ebayUrl);
                    
                    // Wait for results to load
                    Thread.sleep(1200);  // Reduced by 40% from 2000ms
                    
                    // Extract prices and listing details from the specified div
                    Map<String, Object> ebayData = extractEbayData(driver, wait, searchNameWithLanguage);
                    List<Double> prices = (List<Double>) ebayData.get("prices");
                    List<String> listingDetails = (List<String>) ebayData.get("listingDetails");
                    
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
                                logger.debug("Filtering out price ${} which is >50% above median ${}", 
                                    prices.get(i), initialMedian);
                            }
                        }
                        
                        // Recalculate median with filtered prices
                        double finalMedian = filteredPrices.isEmpty() ? initialMedian : calculateMedian(filteredPrices);
                        
                        // Keep all listing details for viewing, but store first 5 prices separately for top graph
                        List<Double> top5Prices = filteredPrices.size() > 5 
                            ? filteredPrices.subList(0, 5) 
                            : filteredPrices;
                        
                        // Keep all filtered data
                        itemResult.put("medianPrice", finalMedian);
                        itemResult.put("resultCount", filteredPrices.size());
                        itemResult.put("listingDetails", filteredListingDetails); // All listings
                        itemResult.put("allPrices", filteredPrices); // All prices for main graph
                        itemResult.put("top5Prices", top5Prices); // First 5 prices for card graph
                        itemResult.put("originalCount", prices.size());
                        itemResult.put("filteredCount", prices.size() - filteredPrices.size());
                    } else {
                        itemResult.put("listingDetails", new ArrayList<>());
                        itemResult.put("allPrices", new ArrayList<>());
                        itemResult.put("error", "No sold listings found");
                    }
                    
                    searchResults.add(itemResult);
                    searchCount++; // Increment counter after successful search
                    
                    // Small delay between searches
                    Thread.sleep(600);  // Reduced by 40% from 1000ms
                    
                } catch (Exception e) {
                    logger.error("Error searching for item: " + itemName, e);
                    itemResult.put("error", e.getMessage());
                    searchResults.add(itemResult);
                    searchCount++; // Increment counter even for errors to avoid infinite loop
                }
            }
            
            // Save results to Google Sheets
            if (googleSheetsService != null && !searchResults.isEmpty()) {
                String sheetName = googleSheetsService.saveEbayPriceData(searchResults);
                result.put("savedToGoogleSheets", true);
                result.put("sheetName", sheetName);
            }
            
            result.put("searchResults", searchResults);
            result.put("totalSearched", searchResults.size());
            
        } catch (Exception e) {
            logger.error("Error in eBay price search", e);
            result.put("error", e.getMessage());
        } finally {
            // WebDriver is managed by Spring context, no manual cleanup needed
            logger.info("eBay price search completed");
        }
        
        return result;
    }
    
    private Map<String, Object> extractEbayData(WebDriver driver, WebDriverWait wait, String searchTerm) {
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
                    logger.info("Using new eBay HTML structure");
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
            
            // Limit to 30 results per item
            int resultCount = 0;
            final int MAX_RESULTS = 30;
            
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
                        logger.debug("Skipping PSA listing since search doesn't include PSA: {}", title);
                        continue;
                    }
                    
                    // Skip multiple quantity listings (sets, bundles, lots, etc)
                    if (isMultipleQuantityListing(title)) {
                        logger.debug("Skipping multiple quantity listing: {}", title);
                        continue;
                    }
                    
                    // Skip Pokemon Center/Centre exclusive items (premium versions)
                    if (isPokemonCenterListing(title)) {
                        logger.debug("Skipping Pokemon Center exclusive listing: {}", title);
                        continue;
                    }
                    
                    // Skip listings with multiple different products
                    if (hasMultipleProducts(title)) {
                        logger.debug("Skipping multiple products listing: {}", title);
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
            
            logger.debug("Parsing price text: '{}'", priceText);
            
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
            
            logger.debug("Successfully parsed price: {} from '{}'", price, priceText);
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
    
    private String detectLanguage(String itemName, String notes) {
        String combined = (itemName + " " + (notes != null ? notes : "")).toLowerCase();
        
        // Check for explicit language indicators
        if (combined.contains("japanese") || 
            combined.contains("japan") || 
            combined.contains("jp") ||
            combined.contains("jpn")) {
            return "Japanese";
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
}