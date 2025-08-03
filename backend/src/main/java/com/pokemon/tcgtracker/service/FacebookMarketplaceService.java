package com.pokemon.tcgtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
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
     */
    public void startMarketplaceMonitoring() {
        WebDriver driver = null;
        try {
            driver = webDriverService.getWebDriver();
            log.info("Starting Facebook Marketplace monitoring");

            for (String searchTerm : SEARCH_TERMS) {
                try {
                    searchMarketplace(driver, searchTerm);
                    webDriverService.humanDelay();
                } catch (Exception e) {
                    log.error("Error searching for term '{}': {}", searchTerm, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to start marketplace monitoring", e);
        } finally {
            webDriverService.closeWebDriver(driver);
        }
    }

    /**
     * Search Facebook Marketplace for specific term
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
            
            // Check if we need to log in or are blocked
            if (isLoginRequired(driver) || webDriverService.isBlocked(driver)) {
                log.warn("Login required or blocked for search: {}", searchTerm);
                // Close the tab if we opened a new one
                if (openedNewTab && originalTab != null) {
                    webDriverService.closeCurrentTab(driver, originalTab);
                }
                return listings;
            }

            // Wait for listings to load
            Thread.sleep(3000);
            
            // Scroll to load more listings
            webDriverService.humanLikeScroll(driver);
            
            // Find listing elements
            List<WebElement> listingElements = findListingElements(driver);
            log.info("Found {} potential listings for '{}'", listingElements.size(), searchTerm);

            for (WebElement listingElement : listingElements) {
                try {
                    Map<String, Object> listing = extractListingData(driver, listingElement, searchTerm);
                    if (listing != null && !listing.isEmpty()) {
                        listings.add(listing);
                        
                        // Use LM Studio to analyze the listing
                        analyzeListingWithAI(listing);
                        
                        // Save to Google Sheets
                        googleSheetsService.addMarketplaceListing(listing);
                        
                        log.info("Processed listing: {}", listing.get("itemName"));
                    }
                } catch (Exception e) {
                    log.warn("Failed to process listing element: {}", e.getMessage());
                }
            }
            
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
        return driver.getCurrentUrl().contains("/login") || 
               driver.getPageSource().contains("Log in to Facebook") ||
               driver.getPageSource().contains("Create new account");
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
                    elements.addAll(found.subList(0, Math.min(found.size(), 10))); // Limit to 10 items
                    break;
                }
            } catch (Exception e) {
                log.debug("Selector '{}' not found", selector);
            }
        }

        return elements;
    }

    /**
     * Extract data from a listing element
     */
    private Map<String, Object> extractListingData(WebDriver driver, WebElement listingElement, String searchTerm) {
        Map<String, Object> listing = new HashMap<>();
        
        try {
            // Get listing URL
            String listingUrl = listingElement.getAttribute("href");
            if (listingUrl == null || !listingUrl.contains("/marketplace/item/")) {
                return null;
            }
            listing.put("url", listingUrl);

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