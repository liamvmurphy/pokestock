package com.pokemon.tcgtracker.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import com.pokemon.tcgtracker.constants.PromptConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CSVAnalysisService {

    @Autowired
    private ClaudeAPIService claudeAPIService;
    
    @Autowired(required = false)
    private ProductClassificationService classificationService;
    
    @Autowired(required = false)
    private DealAnalysisService dealAnalysisService;
    
    @Autowired(required = false)
    private GoogleDriveService googleDriveService;
    
    @Autowired(required = false)
    private GoogleSheetsService googleSheetsService;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Process uploaded CSV file and analyze marketplace data
     */
    public Map<String, Object> processCSVFile(MultipartFile file) throws IOException, CsvValidationException {
        log.info("Processing CSV file: {}", file.getOriginalFilename());
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> rawProducts = new ArrayList<>();
        
        // Parse CSV file
        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {
            
            // Read header row
            String[] headers = csvReader.readNext();
            if (headers == null) {
                throw new IllegalArgumentException("CSV file is empty");
            }
            
            log.info("CSV Headers: {}", Arrays.toString(headers));
            result.put("headers", headers);
            
            // Map headers to indices
            Map<String, Integer> headerMap = createHeaderMap(headers);
            log.info("Header mapping: {}", headerMap);
            
            // Read and parse each row
            String[] row;
            int rowNumber = 2; // Start at 2 (1 is header)
            while ((row = csvReader.readNext()) != null) {
                try {
                    Map<String, Object> product = parseRow(row, headerMap, rowNumber);
                    if (product != null && !isEmptyProduct(product)) {
                        log.debug("Parsed product {}: itemName={}, price={}", 
                                rowNumber, product.get("itemName"), product.get("price"));
                        rawProducts.add(product);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing row {}: {}", rowNumber, e.getMessage());
                }
                rowNumber++;
            }
        }
        
        log.info("Parsed {} products from CSV", rawProducts.size());
        result.put("totalRows", rawProducts.size());
        
        // Analyze products using Claude API with fallback
        log.info("Sending {} products to Claude API for analysis...", rawProducts.size());
        List<Map<String, Object>> analyzedProducts;
        
        try {
            analyzedProducts = claudeAPIService.analyzeProductsBatch(rawProducts);
        } catch (Exception e) {
            log.warn("Claude API failed, falling back to hardcoded analysis: {}", e.getMessage());
            if (classificationService != null && dealAnalysisService != null) {
                List<Map<String, Object>> classifiedProducts = classificationService.classifyProducts(rawProducts);
                analyzedProducts = dealAnalysisService.analyzeProducts(classifiedProducts);
            } else {
                // Create basic fallback analysis
                analyzedProducts = createBasicFallbackAnalysis(rawProducts);
            }
        }
        
        // Generate statistics
        Map<String, Object> statistics = generateStatistics(analyzedProducts);
        result.put("statistics", statistics);
        
        // Find top deals
        List<Map<String, Object>> topDeals = findTopDeals(analyzedProducts, 10);
        result.put("topDeals", topDeals);
        
        // Group by product type
        Map<String, List<Map<String, Object>>> productsByType = groupByProductType(analyzedProducts);
        result.put("productsByType", productsByType);
        
        // All analyzed products
        result.put("analyzedProducts", analyzedProducts);
        
        // Generate timestamp
        result.put("analysisTimestamp", LocalDateTime.now().format(DATE_FORMATTER));
        
        return result;
    }
    
    /**
     * Create basic fallback analysis when both Claude API and hardcoded services fail
     */
    private List<Map<String, Object>> createBasicFallbackAnalysis(List<Map<String, Object>> rawProducts) {
        List<Map<String, Object>> analyzed = new ArrayList<>();
        
        for (Map<String, Object> product : rawProducts) {
            Map<String, Object> analysis = new HashMap<>(product);
            
            String itemName = String.valueOf(product.getOrDefault("itemName", ""));
            analysis.put("cleanedItemName", itemName);
            analysis.put("productType", "OTHER");
            analysis.put("setName", "Unknown Set");
            analysis.put("language", "English");
            analysis.put("marketValueEstimate", 0.0);
            analysis.put("dealScore", 3);
            analysis.put("recommendationLevel", "Fair Deal");
            analysis.put("recommendationNotes", "Unable to analyze - Claude API unavailable");
            analysis.put("dealPercentage", 0.0);
            analysis.put("riskLevel", "Medium");
            analysis.put("classificationConfidence", 0.1);
            
            // Parse price from original data using improved logic
            String priceStr = String.valueOf(product.getOrDefault("price", "0"));
            Double normalizedPrice = 0.0;
            
            // Use the improved price validation to avoid timestamps
            if (isValidPrice(priceStr)) {
                normalizedPrice = parseBasicPrice(priceStr);
            } else {
                // If price is invalid, log it and use 0
                log.warn("Invalid price detected in fallback analysis: '{}' for item: '{}'", 
                        priceStr, itemName);
            }
            
            analysis.put("normalizedPrice", normalizedPrice);
            
            analyzed.add(analysis);
        }
        
        return analyzed;
    }
    
    /**
     * Basic price parsing for fallback
     */
    private Double parseBasicPrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return 0.0;
        }
        
        try {
            String cleaned = priceStr.replaceAll("[^0-9.]", "");
            if (cleaned.isEmpty()) return 0.0;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Generate downloadable CSV with analysis results
     */
    public byte[] generateAnalysisCSV(List<Map<String, Object>> analyzedProducts) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try (Writer writer = new OutputStreamWriter(outputStream);
             CSVWriter csvWriter = new CSVWriter(writer)) {
            
            // Write headers
            String[] headers = {
                "Item Name", "Cleaned Name", "Product Type", "Set Name", "Language",
                "Price", "Market Value", "Deal Score (1-5)", "Recommendation",
                "Deal Percentage", "Risk Level", "Quantity", "Location", 
                "Date Found", "Marketplace URL", "Notes"
            };
            csvWriter.writeNext(headers);
            
            // Write data rows
            for (Map<String, Object> product : analyzedProducts) {
                String[] row = {
                    String.valueOf(product.getOrDefault("itemName", "")),
                    String.valueOf(product.getOrDefault("cleanedItemName", "")),
                    String.valueOf(product.getOrDefault("productType", "")),
                    String.valueOf(product.getOrDefault("setName", "")),
                    String.valueOf(product.getOrDefault("language", "")),
                    formatPrice(product.get("normalizedPrice")),
                    formatPrice(product.get("marketValueEstimate")),
                    String.valueOf(product.getOrDefault("dealScore", "")),
                    String.valueOf(product.getOrDefault("recommendationLevel", "")),
                    formatPercentage(product.get("dealPercentage")),
                    String.valueOf(product.getOrDefault("riskLevel", "")),
                    String.valueOf(product.getOrDefault("quantity", "")),
                    String.valueOf(product.getOrDefault("location", "")),
                    String.valueOf(product.getOrDefault("dateFound", "")),
                    String.valueOf(product.getOrDefault("marketplaceUrl", "")),
                    String.valueOf(product.getOrDefault("recommendationNotes", ""))
                };
                csvWriter.writeNext(row);
            }
        }
        
        return outputStream.toByteArray();
    }
    
    /**
     * Create header mapping for CSV parsing
     */
    private Map<String, Integer> createHeaderMap(String[] headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].toLowerCase().trim();
            
            // Map common variations with better price and date detection
            if (header.contains("item") && header.contains("name")) {
                headerMap.put("itemName", i);
            } else if (header.contains("set")) {
                headerMap.put("set", i);
            } else if (header.contains("product") && header.contains("type")) {
                headerMap.put("productType", i);
            } else if (header.contains("date") || header.contains("found") || header.contains("timestamp")) {
                // Map date fields BEFORE price fields to avoid confusion
                headerMap.put("dateFound", i);
            } else if (header.contains("main") && header.contains("price")) {
                headerMap.put("mainListingPrice", i);
                headerMap.put("price", i); // Also map as primary price
            } else if (header.contains("price") && !header.contains("unit") && !header.contains("date")) {
                headerMap.put("price", i);
            } else if ((header.contains("cost") || header.contains("amount") || header.contains("value")) 
                       && !header.contains("date") && !header.contains("found")) {
                headerMap.put("price", i); // Alternative price fields, but exclude date-related fields
            } else if (header.contains("quantity")) {
                headerMap.put("quantity", i);
            } else if (header.contains("unit") && header.contains("price")) {
                headerMap.put("priceUnit", i);
            } else if (header.contains("location")) {
                headerMap.put("location", i);
            } else if (header.contains("multiple")) {
                headerMap.put("hasMultipleItems", i);
            } else if (header.contains("url") || header.contains("marketplace")) {
                headerMap.put("marketplaceUrl", i);
            } else if (header.contains("notes") || header.contains("description")) {
                headerMap.put("notes", i);
            } else if (header.contains("source")) {
                headerMap.put("source", i);
            }
            
            // Store original header mapping
            headerMap.put(header, i);
        }
        
        return headerMap;
    }
    
    /**
     * Parse a single CSV row into a product map
     */
    private Map<String, Object> parseRow(String[] row, Map<String, Integer> headerMap, int rowNumber) {
        Map<String, Object> product = new HashMap<>();
        
        // Extract known fields
        product.put("itemName", getValueFromRow(row, headerMap, "itemName"));
        product.put("set", getValueFromRow(row, headerMap, "set"));
        product.put("productType", getValueFromRow(row, headerMap, "productType"));
        
        // Smart price extraction - try multiple price fields
        String price = extractBestPrice(row, headerMap);
        product.put("price", price);
        
        product.put("quantity", getValueFromRow(row, headerMap, "quantity"));
        product.put("priceUnit", getValueFromRow(row, headerMap, "priceUnit"));
        product.put("mainListingPrice", getValueFromRow(row, headerMap, "mainListingPrice"));
        product.put("location", getValueFromRow(row, headerMap, "location"));
        product.put("hasMultipleItems", getValueFromRow(row, headerMap, "hasMultipleItems"));
        product.put("marketplaceUrl", getValueFromRow(row, headerMap, "marketplaceUrl"));
        product.put("notes", getValueFromRow(row, headerMap, "notes"));
        product.put("dateFound", getValueFromRow(row, headerMap, "dateFound"));
        product.put("source", getValueFromRow(row, headerMap, "source"));
        
        // Add metadata
        product.put("rowNumber", rowNumber);
        
        return product;
    }
    
    /**
     * Extract the best available price from multiple possible price columns
     */
    private String extractBestPrice(String[] row, Map<String, Integer> headerMap) {
        // Try different price field variations in order of preference
        String[] priceFields = {"price", "mainListingPrice", "mainprice", "cost", "amount", "value"};
        
        for (String field : priceFields) {
            String value = getValueFromRow(row, headerMap, field);
            if (value != null && !value.isEmpty() && !value.equalsIgnoreCase("each") 
                && !value.equalsIgnoreCase("lot") && !value.equalsIgnoreCase("set")
                && !value.equalsIgnoreCase("bundle") && !value.equalsIgnoreCase("piece")) {
                
                // Check if it looks like a valid price (not a timestamp)
                if (isValidPrice(value)) {
                    return value;
                }
            }
        }
        
        return "0"; // Default if no price found
    }
    
    /**
     * Check if a value looks like a valid price (not a timestamp or Excel date serial number)
     */
    private boolean isValidPrice(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        
        // Remove currency symbols and whitespace
        String cleaned = value.replaceAll("[\\s\\$€£¥A]", "");
        
        // Check if it matches price patterns
        if (!cleaned.matches("^[0-9.,]+$")) {
            return false;
        }
        
        try {
            double numericValue = Double.parseDouble(cleaned.replaceAll(",", ""));
            
            // Reject values that look like Excel date serial numbers (typically > 40000 for recent dates)
            // Also reject unreasonably high prices (over $10,000 for TCG products)
            if (numericValue > 10000) {
                return false;
            }
            
            // Reject values that are too small to be realistic prices (under $0.10)
            if (numericValue < 0.1) {
                return false;
            }
            
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Get value from row using header mapping
     */
    private String getValueFromRow(String[] row, Map<String, Integer> headerMap, String field) {
        Integer index = headerMap.get(field);
        if (index != null && index < row.length) {
            String value = row[index];
            return (value != null && !value.trim().isEmpty()) ? value.trim() : "";
        }
        return "";
    }
    
    /**
     * Check if product is empty
     */
    private boolean isEmptyProduct(Map<String, Object> product) {
        String itemName = String.valueOf(product.getOrDefault("itemName", ""));
        String price = String.valueOf(product.getOrDefault("price", ""));
        
        return itemName.isEmpty() || itemName.equals("null") || 
               itemName.equalsIgnoreCase("Item Name");
    }
    
    /**
     * Generate statistics from analyzed products
     */
    private Map<String, Object> generateStatistics(List<Map<String, Object>> products) {
        Map<String, Object> stats = new HashMap<>();
        
        if (products.isEmpty()) {
            return stats;
        }
        
        // Count by deal score
        Map<Integer, Long> dealScoreCounts = products.stream()
            .collect(Collectors.groupingBy(
                p -> getIntValue(p.get("dealScore")),
                Collectors.counting()
            ));
        stats.put("dealScoreDistribution", dealScoreCounts);
        
        // Count by product type
        Map<String, Long> productTypeCounts = products.stream()
            .collect(Collectors.groupingBy(
                p -> String.valueOf(p.getOrDefault("productType", "OTHER")),
                Collectors.counting()
            ));
        stats.put("productTypeDistribution", productTypeCounts);
        
        // Count by language
        Map<String, Long> languageCounts = products.stream()
            .collect(Collectors.groupingBy(
                p -> String.valueOf(p.getOrDefault("language", "English")),
                Collectors.counting()
            ));
        stats.put("languageDistribution", languageCounts);
        
        // Price statistics
        DoubleSummaryStatistics priceStats = products.stream()
            .mapToDouble(p -> getDoubleValue(p.get("normalizedPrice")))
            .filter(price -> price > 0)
            .summaryStatistics();
        
        stats.put("priceMin", priceStats.getMin());
        stats.put("priceMax", priceStats.getMax());
        stats.put("priceAverage", priceStats.getAverage());
        stats.put("totalValue", priceStats.getSum());
        
        // Deal statistics
        long exceptionalDeals = dealScoreCounts.getOrDefault(5, 0L);
        long greatDeals = dealScoreCounts.getOrDefault(4, 0L);
        long goodDeals = exceptionalDeals + greatDeals;
        
        stats.put("exceptionalDeals", exceptionalDeals);
        stats.put("greatDeals", greatDeals);
        stats.put("totalGoodDeals", goodDeals);
        
        // Risk statistics
        Map<String, Long> riskCounts = products.stream()
            .collect(Collectors.groupingBy(
                p -> String.valueOf(p.getOrDefault("riskLevel", "Low")),
                Collectors.counting()
            ));
        stats.put("riskDistribution", riskCounts);
        
        return stats;
    }
    
    /**
     * Find top deals from analyzed products
     */
    private List<Map<String, Object>> findTopDeals(List<Map<String, Object>> products, int limit) {
        return products.stream()
            .filter(p -> getIntValue(p.get("dealScore")) >= 4)
            .sorted((a, b) -> {
                int scoreCompare = Integer.compare(
                    getIntValue(b.get("dealScore")),
                    getIntValue(a.get("dealScore"))
                );
                if (scoreCompare != 0) return scoreCompare;
                
                // Secondary sort by deal percentage
                return Double.compare(
                    getDoubleValue(b.get("dealPercentage")),
                    getDoubleValue(a.get("dealPercentage"))
                );
            })
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Group products by type
     */
    private Map<String, List<Map<String, Object>>> groupByProductType(List<Map<String, Object>> products) {
        return products.stream()
            .collect(Collectors.groupingBy(
                p -> String.valueOf(p.getOrDefault("productType", "OTHER"))
            ));
    }
    
    /**
     * Format price for display
     */
    private String formatPrice(Object price) {
        Double value = getDoubleValue(price);
        if (value == null || value == 0) return "";
        return String.format("%.2f", value);
    }
    
    /**
     * Format percentage for display
     */
    private String formatPercentage(Object percentage) {
        Double value = getDoubleValue(percentage);
        if (value == null) return "";
        return String.format("%.1f%%", value);
    }
    
    /**
     * Get double value safely
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * Get integer value safely
     */
    private Integer getIntValue(Object value) {
        if (value == null) return 0;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * Analyze CSV file with comprehensive market analysis using MASTER_CSV_ANALYZE_PROMPT
     */
    public Map<String, Object> analyzeCSVWithMasterPrompt(MultipartFile file) throws IOException, CsvValidationException {
        log.info("Analyzing CSV file with master prompt: {}", file.getOriginalFilename());
        
        Map<String, Object> result = new HashMap<>();
        
        // Convert CSV to formatted string for Claude
        String csvContent = convertCSVToString(file);
        
        try {
            // Use Claude API with master prompt
            String analysis = claudeAPIService.analyzeData(csvContent);
            
            result.put("success", true);
            result.put("analysis", analysis);
            result.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));
            result.put("filename", file.getOriginalFilename());
            
        } catch (Exception e) {
            log.error("Error analyzing CSV with master prompt: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("analysis", "Failed to analyze CSV data. Please check your API configuration.");
        }
        
        return result;
    }
    
    /**
     * Analyze CSV content string with comprehensive market analysis using MASTER_CSV_ANALYZE_PROMPT
     */
    public Map<String, Object> analyzeCSVWithMasterPrompt(String csvContent) {
        log.info("Analyzing CSV content with master prompt (auto-generated from marketplace data)");
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Use Claude API with master prompt
            String analysis = claudeAPIService.analyzeData(csvContent);
            
            result.put("success", true);
            result.put("analysis", analysis);
            result.put("timestamp", LocalDateTime.now().format(DATE_FORMATTER));
            result.put("filename", "Auto-generated from marketplace data");
            
            // Save report to Google Sheets if service is available
            if (googleSheetsService != null) {
                try {
                    // Count total listings from CSV
                    int totalListings = countCSVRows(csvContent);
                    
                    // Save to Google Sheets as a new tab
                    String sheetName = googleSheetsService.saveMarketIntelligenceReport(analysis, totalListings);
                    if (sheetName != null) {
                        result.put("savedToGoogleSheets", true);
                        result.put("googleSheetsTabName", sheetName);
                        result.put("spreadsheetUrl", googleSheetsService.getSpreadsheetUrl());
                        log.info("Successfully saved market intelligence report to Google Sheets tab: {}", sheetName);
                    } else {
                        result.put("savedToGoogleSheets", false);
                        log.warn("Failed to save market intelligence report to Google Sheets");
                    }
                } catch (Exception e) {
                    log.error("Error saving report to Google Sheets: {}", e.getMessage());
                    result.put("savedToGoogleSheets", false);
                    result.put("googleSheetsError", e.getMessage());
                }
            } else {
                result.put("savedToGoogleSheets", false);
                result.put("googleSheetsError", "Google Sheets service not available");
            }
            
        } catch (Exception e) {
            log.error("Error analyzing CSV content with master prompt: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("analysis", "Failed to analyze CSV data. Please check your API configuration.");
        }
        
        return result;
    }
    
    /**
     * Convert CSV file to formatted string for analysis
     */
    private String convertCSVToString(MultipartFile file) throws IOException, CsvValidationException {
        StringBuilder csvBuilder = new StringBuilder();
        
        try (Reader reader = new InputStreamReader(file.getInputStream());
             CSVReader csvReader = new CSVReader(reader)) {
            
            String[] row;
            while ((row = csvReader.readNext()) != null) {
                csvBuilder.append(String.join(",", row)).append("\n");
            }
        }
        
        return csvBuilder.toString();
    }
    
    /**
     * Count the number of data rows in CSV content (excluding header)
     */
    private int countCSVRows(String csvContent) {
        if (csvContent == null || csvContent.trim().isEmpty()) {
            return 0;
        }
        
        String[] lines = csvContent.trim().split("\n");
        // Subtract 1 to exclude header row
        return Math.max(0, lines.length - 1);
    }
}