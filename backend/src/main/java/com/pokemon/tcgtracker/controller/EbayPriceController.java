package com.pokemon.tcgtracker.controller;

import com.pokemon.tcgtracker.service.EbayPriceService;
import com.pokemon.tcgtracker.service.GoogleSheetsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import java.io.InputStreamReader;
import java.util.*;

@RestController
@RequestMapping("/api/ebay-price")
@CrossOrigin(origins = "*")
public class EbayPriceController {
    
    @Autowired
    private EbayPriceService ebayPriceService;
    
    @Autowired(required = false)
    private GoogleSheetsService googleSheetsService;
    
    @PostMapping("/search")
    public ResponseEntity<Map<String, Object>> searchEbayPrices(@RequestParam("file") MultipartFile file) {
        try {
            // Parse CSV file
            List<Map<String, String>> csvData = parseCSV(file);
            
            // Search eBay prices
            Map<String, Object> result = ebayPriceService.searchEbayPrices(csvData);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @PostMapping("/search-from-marketplace")
    public ResponseEntity<Map<String, Object>> searchEbayPricesFromMarketplace() {
        try {
            if (googleSheetsService == null) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Google Sheets service not available");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Get marketplace data from Google Sheets
            List<Map<String, Object>> marketplaceData = googleSheetsService.getAllMarketplaceListings();
            
            if (marketplaceData.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "No marketplace data found. Please run Facebook marketplace scanning first.");
                return ResponseEntity.badRequest().body(error);
            }
            
            // Convert marketplace data to the format expected by eBay service
            List<Map<String, String>> csvData = convertMarketplaceDataToCsvFormat(marketplaceData);
            
            // Search eBay prices
            Map<String, Object> result = ebayPriceService.searchEbayPrices(csvData);
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
    
    @GetMapping("/results")
    public ResponseEntity<Map<String, Object>> getEbayPriceResults() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (googleSheetsService != null) {
                List<Map<String, String>> data = googleSheetsService.getEbayPriceData();
                response.put("data", data);
                response.put("hasData", !data.isEmpty());
            } else {
                response.put("error", "Google Sheets service not available");
                response.put("hasData", false);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("hasData", false);
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    private List<Map<String, String>> parseCSV(MultipartFile file) throws Exception {
        List<Map<String, String>> data = new ArrayList<>();
        
        try (CSVReader reader = new CSVReaderBuilder(new InputStreamReader(file.getInputStream()))
                .build()) {
            
            String[] headers = reader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            
            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length && i < line.length; i++) {
                    row.put(headers[i], line[i]);
                }
                data.add(row);
            }
        }
        
        return data;
    }
    
    private List<Map<String, String>> convertMarketplaceDataToCsvFormat(List<Map<String, Object>> marketplaceData) {
        List<Map<String, String>> csvData = new ArrayList<>();
        
        for (Map<String, Object> marketplaceItem : marketplaceData) {
            Map<String, String> csvItem = new HashMap<>();
            
            // Map marketplace data to CSV format expected by eBay service
            csvItem.put("Item Name", getString(marketplaceItem, "itemName"));
            csvItem.put("Set", getString(marketplaceItem, "set"));
            csvItem.put("Product Type", getString(marketplaceItem, "productType"));
            csvItem.put("Price", getString(marketplaceItem, "price"));
            csvItem.put("Quantity", getString(marketplaceItem, "quantity"));
            csvItem.put("Notes", getString(marketplaceItem, "notes"));
            csvItem.put("Language", getString(marketplaceItem, "language"));
            
            csvData.add(csvItem);
        }
        
        return csvData;
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : "";
    }
}