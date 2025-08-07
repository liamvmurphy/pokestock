package com.pokemon.tcgtracker.controller;

import com.pokemon.tcgtracker.service.ConfigurationService;
import com.pokemon.tcgtracker.service.FacebookMarketplaceService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MarketplaceController {

    private final FacebookMarketplaceService facebookMarketplaceService;
    private final GoogleSheetsService googleSheetsService;
    private final ConfigurationService configurationService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            Map<String, Object> status = facebookMarketplaceService.getMonitoringStatus();
            // Add current configuration to status
            status.putAll(configurationService.getCurrentConfiguration());
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get marketplace status", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startMonitoring() {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    log.info("Starting Facebook Marketplace monitoring");
                    facebookMarketplaceService.startMarketplaceMonitoring();
                    log.info("Completed Facebook Marketplace monitoring");
                } catch (Exception e) {
                    log.error("Facebook Marketplace monitoring failed", e);
                }
            });

            // Get the actual search terms used by the service
            Map<String, Object> serviceStatus = facebookMarketplaceService.getMonitoringStatus();
            List<String> actualSearchTerms = (List<String>) serviceStatus.get("searchTerms");

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Marketplace monitoring started");
            response.put("status", "running");
            response.put("searchTerms", actualSearchTerms);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start marketplace monitoring", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMarketplace(@RequestBody Map<String, String> request) {
        try {
            String searchTerm = request.getOrDefault("searchTerm", "pokemon tcg");
            
            // Run search asynchronously
            CompletableFuture<List<Map<String, Object>>> futureResults = CompletableFuture.supplyAsync(() -> {
                try {
                    return facebookMarketplaceService.searchMarketplace(null, searchTerm);
                } catch (Exception e) {
                    log.error("Search failed for term: " + searchTerm, e);
                    return List.of();
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Search started for: " + searchTerm);
            response.put("searchTerm", searchTerm);
            response.put("status", "searching");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to start marketplace search", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> scrapeMarketplace(@RequestBody Map<String, Object> request) {
        try {
            String searchTerm = (String) request.getOrDefault("searchTerm", "Pokemon ETB");
            Integer maxItems = (Integer) request.getOrDefault("maxItems", 15);
            
            log.info("Starting scalable marketplace scraping for: {} (max {} items)", searchTerm, maxItems);
            
            // Run scraping synchronously for now (can be made async later)
            List<Map<String, Object>> results = facebookMarketplaceService.scrapeMarketplaceItems(searchTerm, maxItems);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Scraping completed");
            response.put("searchTerm", searchTerm);
            response.put("itemsFound", results.size());
            response.put("results", results);
            response.put("status", "completed");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to scrape marketplace", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("status", "failed");
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/search-terms")
    public ResponseEntity<Map<String, Object>> getSearchTerms() {
        Map<String, Object> response = new HashMap<>();
        // Get the actual search terms from the service to ensure consistency
        Map<String, Object> serviceStatus = facebookMarketplaceService.getMonitoringStatus();
        List<String> searchTerms = (List<String>) serviceStatus.get("searchTerms");
        response.put("searchTerms", searchTerms);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/configure")
    public ResponseEntity<Map<String, Object>> configure(@RequestBody Map<String, Object> config) {
        try {
            // Update model configuration if provided
            if (config.containsKey("model")) {
                String model = (String) config.get("model");
                configurationService.updateLmStudioModel(model);
                log.info("Updated LM Studio model to: {}", model);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Configuration updated");
            response.put("config", config);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update marketplace configuration", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopMonitoring() {
        try {
            // TODO: Implement stop functionality
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Marketplace monitoring stopped");
            response.put("status", "stopped");
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to stop marketplace monitoring", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory() {
        try {
            // TODO: Implement history retrieval from Google Sheets
            
            Map<String, Object> response = new HashMap<>();
            response.put("listings", List.of());
            response.put("totalCount", 0);
            response.put("lastUpdate", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get marketplace history", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @GetMapping("/listings")
    public ResponseEntity<Map<String, Object>> getListings() {
        try {
            // Fetch all listings from Google Sheets
            List<Map<String, Object>> allListings = googleSheetsService.getAllMarketplaceListings();
            
            // Separate into available and all listings
            List<Map<String, Object>> availableListings = allListings.stream()
                    .filter(listing -> {
                        // Filter for available items (you can customize this logic)
                        Object quantity = listing.get("quantity");
                        if (quantity instanceof String) {
                            try {
                                return Integer.parseInt((String) quantity) > 0;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        } else if (quantity instanceof Number) {
                            return ((Number) quantity).intValue() > 0;
                        }
                        return false;
                    })
                    .collect(java.util.stream.Collectors.toList());
            
            Map<String, Object> response = new HashMap<>();
            response.put("availableListings", availableListings);
            response.put("allListings", allListings);
            response.put("totalCount", allListings.size());
            response.put("availableCount", availableListings.size());
            response.put("lastUpdate", System.currentTimeMillis());
            response.put("spreadsheetUrl", googleSheetsService.getSpreadsheetUrl());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get marketplace listings", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("message", "Failed to fetch listings from Google Sheets");
            return ResponseEntity.internalServerError().body(error);
        }
    }
}