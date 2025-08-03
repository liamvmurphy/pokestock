package com.pokemon.tcgtracker.controller;

import com.pokemon.tcgtracker.service.ConfigurationService;
import com.pokemon.tcgtracker.service.FacebookMarketplaceService;
import com.pokemon.tcgtracker.service.MarketplaceScrapingService;
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
    private final MarketplaceScrapingService marketplaceScrapingService;
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
            // Run monitoring for 2 search terms only
            List<String> searchTerms = List.of(
                "Pokemon ETB",
                "Pokemon Elite Trainer Box"
            );
            
            CompletableFuture.runAsync(() -> {
                try {
                    for (String searchTerm : searchTerms) {
                        log.info("Starting scraping for: {}", searchTerm);
                        marketplaceScrapingService.scrapeMarketplaceItems(searchTerm, 15); // 15 items per search
                        
                        // Add delay between searches
                        Thread.sleep(2500); // Reduced from 5000ms
                    }
                    log.info("Completed all marketplace scraping");
                } catch (Exception e) {
                    log.error("Marketplace monitoring failed", e);
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Marketplace monitoring started");
            response.put("status", "running");
            response.put("searchTerms", searchTerms);
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
            List<Map<String, Object>> results = marketplaceScrapingService.scrapeMarketplaceItems(searchTerm, maxItems);
            
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
        response.put("searchTerms", List.of(
            "pokemon etb", 
            "pokemon elite trainer box",
            "pokemon booster box"
        ));
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
}