package com.pokemon.tcgtracker.controller;

import com.opencsv.exceptions.CsvValidationException;
import com.pokemon.tcgtracker.service.CSVAnalysisService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import com.pokemon.tcgtracker.service.GoogleDriveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/csv")
@CrossOrigin(origins = "*")
public class CSVAnalysisController {

    @Autowired
    private CSVAnalysisService csvAnalysisService;
    
    @Autowired
    private GoogleSheetsService googleSheetsService;
    
    @Autowired(required = false)
    private GoogleDriveService googleDriveService;
    
    /**
     * Upload and analyze CSV file
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadAndAnalyzeCSV(
            @RequestParam("file") MultipartFile file
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Please select a CSV file to upload");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                response.put("success", false);
                response.put("error", "Please upload a CSV file");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Processing CSV upload: {} ({} bytes)", 
                    file.getOriginalFilename(), file.getSize());
            
            // Process the CSV file
            Map<String, Object> analysisResult = csvAnalysisService.processCSVFile(file);
            
            // Build response
            response.put("success", true);
            response.put("message", "CSV file processed successfully");
            response.put("fileName", file.getOriginalFilename());
            response.put("fileSize", file.getSize());
            response.put("analysis", analysisResult);
            
            log.info("CSV analysis completed successfully for {}", file.getOriginalFilename());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.error("Invalid CSV file: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Invalid CSV format: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (CsvValidationException e) {
            log.error("CSV validation error: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "CSV validation failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
            
        } catch (IOException e) {
            log.error("Error processing CSV file: {}", e.getMessage());
            response.put("success", false);
            response.put("error", "Error reading CSV file: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            
        } catch (Exception e) {
            log.error("Unexpected error processing CSV: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Unexpected error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Analyze CSV with master prompt - comprehensive market analysis
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeCSVWithMasterPrompt(
            @RequestParam("file") MultipartFile file
    ) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validate file
            if (file.isEmpty()) {
                response.put("success", false);
                response.put("error", "Please select a CSV file to upload");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (!file.getOriginalFilename().toLowerCase().endsWith(".csv")) {
                response.put("success", false);
                response.put("error", "Please upload a CSV file");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Analyzing CSV with master prompt: {} ({} bytes)", 
                    file.getOriginalFilename(), file.getSize());
            
            // Analyze with master prompt
            Map<String, Object> analysisResult = csvAnalysisService.analyzeCSVWithMasterPrompt(file);
            
            return ResponseEntity.ok(analysisResult);
            
        } catch (Exception e) {
            log.error("Error analyzing CSV with master prompt: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to analyze CSV: " + e.getMessage());
            response.put("analysis", "Error occurred during analysis. Please check your API configuration and try again.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Download analysis results as CSV
     */
    @PostMapping("/download")
    public ResponseEntity<byte[]> downloadAnalysisCSV(
            @RequestBody Map<String, Object> request
    ) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> analyzedProducts = 
                (List<Map<String, Object>>) request.get("analyzedProducts");
            
            if (analyzedProducts == null || analyzedProducts.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            byte[] csvData = csvAnalysisService.generateAnalysisCSV(analyzedProducts);
            
            String filename = "pokemon_tcg_analysis_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + 
                ".csv";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(csvData.length);
            
            return ResponseEntity.ok()
                .headers(headers)
                .body(csvData);
                
        } catch (Exception e) {
            log.error("Error generating CSV download: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get sample CSV format
     */
    @GetMapping("/sample")
    public ResponseEntity<Map<String, Object>> getSampleCSVFormat() {
        Map<String, Object> response = new HashMap<>();
        
        String[] sampleHeaders = {
            "Item Name", "Set", "Product Type", "Price", "Quantity", "Price Unit",
            "Main Listing Price", "Location", "Has Multiple Items", "Marketplace URL",
            "Notes", "Date Found", "Source"
        };
        
        String[][] sampleData = {
            {
                "Pokemon Scarlet & Violet ETB",
                "Scarlet & Violet",
                "ETB",
                "45.00",
                "1",
                "each",
                "50.00",
                "Melbourne, VIC",
                "false",
                "https://facebook.com/marketplace/item/123456",
                "Brand new, sealed",
                "2024-01-15 10:30:00",
                "Facebook Marketplace"
            },
            {
                "Obsidian Flames Booster Box",
                "Obsidian Flames",
                "Booster Box",
                "95.00",
                "1",
                "each",
                "110.00",
                "Sydney, NSW",
                "false",
                "https://facebook.com/marketplace/item/789012",
                "Slight box damage but packs intact",
                "2024-01-15 14:20:00",
                "Facebook Marketplace"
            }
        };
        
        response.put("headers", sampleHeaders);
        response.put("sampleData", sampleData);
        response.put("description", "Sample CSV format for Pokemon TCG marketplace data");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Auto-analyze marketplace data from Google Sheets
     */
    @PostMapping("/auto-analyze")
    public ResponseEntity<Map<String, Object>> autoAnalyzeMarketplaceData() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            log.info("Starting auto-analysis of marketplace data from Google Sheets");
            
            // Fetch marketplace data from Google Sheets
            List<Map<String, Object>> marketplaceData = googleSheetsService.getAllMarketplaceListings();
            
            if (marketplaceData.isEmpty()) {
                response.put("success", false);
                response.put("error", "No marketplace data found. Please run the marketplace scanner first.");
                return ResponseEntity.badRequest().body(response);
            }
            
            log.info("Found {} marketplace listings for analysis", marketplaceData.size());
            
            // Convert marketplace data to CSV format
            String csvContent = convertMarketplaceDataToCSV(marketplaceData);
            
            // Use the master prompt analysis method
            Map<String, Object> analysisResult = csvAnalysisService.analyzeCSVWithMasterPrompt(csvContent);
            
            // Add metadata
            analysisResult.put("dataSource", "Google Sheets Marketplace Data");
            analysisResult.put("totalListings", marketplaceData.size());
            analysisResult.put("autoGenerated", true);
            
            return ResponseEntity.ok(analysisResult);
            
        } catch (Exception e) {
            log.error("Error in auto-analysis of marketplace data: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to analyze marketplace data: " + e.getMessage());
            response.put("analysis", "Error occurred during analysis. Please check the server logs.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Convert marketplace data to CSV format for analysis
     */
    private String convertMarketplaceDataToCSV(List<Map<String, Object>> marketplaceData) {
        StringBuilder csvBuilder = new StringBuilder();
        
        // Add CSV headers
        csvBuilder.append("Date Found,Item Name,Set,Product Type,Price,Quantity,Price Unit,")
                 .append("Main Listing Price,Location,Has Multiple Items,Marketplace URL,Notes\n");
        
        // Add data rows
        for (Map<String, Object> listing : marketplaceData) {
            csvBuilder.append(escapeCsvValue(listing.get("dateFound")))
                     .append(",").append(escapeCsvValue(listing.get("itemName")))
                     .append(",").append(escapeCsvValue(listing.get("set")))
                     .append(",").append(escapeCsvValue(listing.get("productType")))
                     .append(",").append(escapeCsvValue(listing.get("price")))
                     .append(",").append(escapeCsvValue(listing.get("quantity")))
                     .append(",").append(escapeCsvValue(listing.get("priceUnit")))
                     .append(",").append(escapeCsvValue(listing.get("mainListingPrice")))
                     .append(",").append(escapeCsvValue(listing.get("location")))
                     .append(",").append(escapeCsvValue(listing.get("hasMultipleItems")))
                     .append(",").append(escapeCsvValue(listing.get("marketplaceUrl")))
                     .append(",").append(escapeCsvValue(listing.get("notes")))
                     .append("\n");
        }
        
        return csvBuilder.toString();
    }
    
    /**
     * Escape CSV values to handle commas, quotes, etc.
     */
    private String escapeCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        String str = value.toString();
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }

    /**
     * Get the last saved market intelligence report from Google Sheets
     */
    @GetMapping("/last-report")
    public ResponseEntity<Map<String, Object>> getLastSavedReport() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (googleSheetsService == null) {
                response.put("success", false);
                response.put("error", "Google Sheets service not available");
                return ResponseEntity.ok(response);
            }
            
            log.info("Retrieving last saved market intelligence report from Google Sheets");
            
            Map<String, Object> reportData = googleSheetsService.getLatestMarketIntelligenceReport();
            
            if (reportData == null) {
                response.put("success", false);
                response.put("error", "No previous reports found in Google Sheets");
                return ResponseEntity.ok(response);
            }
            
            response.put("success", true);
            response.put("report", reportData);
            
            log.info("Successfully retrieved last market intelligence report: {}", 
                    reportData.get("sheetName"));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error retrieving last saved report: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", "Failed to retrieve last report: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Get Google Sheets info and report history
     */
    @GetMapping("/google-sheets/info")
    public ResponseEntity<Map<String, Object>> getGoogleSheetsInfo() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (googleSheetsService == null) {
                response.put("success", false);
                response.put("available", false);
                response.put("error", "Google Sheets service not available");
                return ResponseEntity.ok(response);
            }
            
            response.put("success", true);
            response.put("available", true);
            response.put("spreadsheetUrl", googleSheetsService.getSpreadsheetUrl());
            response.put("description", "Market intelligence reports are saved as new tabs in your Google Sheets file");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error getting Google Sheets info: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("available", false);
            response.put("error", "Failed to get Google Sheets info: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "OK");
        response.put("service", "CSV Analysis Service");
        response.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
}