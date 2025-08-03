package com.pokemon.tcgtracker.controller;

import com.pokemon.tcgtracker.service.ConfigurationService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.LMStudioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final LMStudioService lmStudioService;
    private final GoogleSheetsService googleSheetsService;
    private final ApplicationContext applicationContext;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "OK");
        status.put("googleSheets", googleSheetsService.getSpreadsheetUrl());
        status.put("lmStudio", lmStudioService.testConnection());
        return ResponseEntity.ok(status);
    }

    @GetMapping("/models")
    public ResponseEntity<?> getAvailableModels() {
        try {
            List<Map<String, Object>> models = lmStudioService.getAvailableModels();
            return ResponseEntity.ok(models);
        } catch (Exception e) {
            log.error("Failed to fetch models", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/analyze-image")
    public ResponseEntity<Map<String, Object>> analyzeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "type", defaultValue = "marketplace") String analysisType,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "model", required = false) String model) {
        
        try {
            // Convert image to base64
            String base64Image = "data:" + image.getContentType() + ";base64," + 
                    Base64.getEncoder().encodeToString(image.getBytes());
            
            Map<String, Object> result;
            if ("marketplace".equals(analysisType)) {
                result = lmStudioService.analyzeMarketplaceListing(
                        description != null ? description : "Analyze this listing", 
                        base64Image,
                        model
                );
            } else {
                result = lmStudioService.analyzeStoreInventory("Test Store", base64Image);
            }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to analyze image", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @GetMapping("/current-model")
    public ResponseEntity<Map<String, Object>> getCurrentModel() {
        Map<String, Object> response = new HashMap<>();
        // Get the model from different sources to debug
        response.put("defaultModel", lmStudioService.getModel());
        // Add configuration service if available
        try {
            ConfigurationService configService = applicationContext.getBean(ConfigurationService.class);
            response.put("activeModel", configService.getActiveLmStudioModel());
        } catch (Exception e) {
            response.put("configServiceError", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/test-marketplace")
    public ResponseEntity<Map<String, Object>> testMarketplace(@RequestBody Map<String, String> request) {
        try {
            // Simulate a marketplace listing
            Map<String, Object> listing = new HashMap<>();
            listing.put("itemName", request.getOrDefault("itemName", "Pokemon TCG Paradox Rift Booster Box"));
            listing.put("set", request.getOrDefault("set", "Paradox Rift"));
            listing.put("productType", request.getOrDefault("productType", "Booster Box"));
            listing.put("condition", request.getOrDefault("condition", "New"));
            listing.put("price", request.getOrDefault("price", "150.00"));
            listing.put("pricePerPack", request.getOrDefault("pricePerPack", "4.17"));
            listing.put("seller", request.getOrDefault("seller", "Test Seller"));
            listing.put("url", request.getOrDefault("url", "https://facebook.com/marketplace/item/123"));
            
            // Save to Google Sheets
            googleSheetsService.addMarketplaceListing(listing);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Test listing added to Google Sheets");
            response.put("data", listing);
            response.put("spreadsheetUrl", googleSheetsService.getSpreadsheetUrl());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to test marketplace", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    @PostMapping("/test-browser")
    public ResponseEntity<Map<String, Object>> testBrowser() {
        try {
            // Test browser automation
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Browser test not implemented yet - requires Chrome setup");
            response.put("status", "pending");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to test browser", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}