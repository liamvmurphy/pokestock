package com.pokemon.tcgtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
public class DealAnalysisService {

    // Market value references (in USD) - These would ideally come from a database
    private static final Map<String, Map<String, Double>> MARKET_VALUES = new HashMap<>();
    
    static {
        // Initialize market values for common products
        Map<String, Double> etbValues = new HashMap<>();
        etbValues.put("Scarlet & Violet", 45.0);
        etbValues.put("Paldea Evolved", 42.0);
        etbValues.put("Obsidian Flames", 44.0);
        etbValues.put("Paradox Rift", 43.0);
        etbValues.put("Pokemon 151", 65.0);
        etbValues.put("Temporal Forces", 42.0);
        etbValues.put("Stellar Crown", 45.0);
        etbValues.put("Evolving Skies", 85.0);
        etbValues.put("Crown Zenith", 55.0);
        MARKET_VALUES.put("ETB", etbValues);
        
        Map<String, Double> boosterBoxValues = new HashMap<>();
        boosterBoxValues.put("Scarlet & Violet", 95.0);
        boosterBoxValues.put("Paldea Evolved", 92.0);
        boosterBoxValues.put("Obsidian Flames", 94.0);
        boosterBoxValues.put("Paradox Rift", 93.0);
        boosterBoxValues.put("Pokemon 151", 210.0);
        boosterBoxValues.put("Temporal Forces", 92.0);
        boosterBoxValues.put("Stellar Crown", 95.0);
        boosterBoxValues.put("Evolving Skies", 180.0);
        boosterBoxValues.put("Crown Zenith", 120.0);
        boosterBoxValues.put("Lost Origin", 98.0);
        boosterBoxValues.put("Silver Tempest", 95.0);
        MARKET_VALUES.put("BOOSTER_BOX", boosterBoxValues);
        
        Map<String, Double> packValues = new HashMap<>();
        packValues.put("Default", 4.5); // Average pack price
        packValues.put("Pokemon 151", 7.0);
        packValues.put("Evolving Skies", 6.5);
        MARKET_VALUES.put("BOOSTER_PACK", packValues);
        
        Map<String, Double> collectionBoxValues = new HashMap<>();
        collectionBoxValues.put("Default", 25.0); // Average collection box
        collectionBoxValues.put("Premium", 50.0);
        collectionBoxValues.put("Ultra Premium", 120.0);
        MARKET_VALUES.put("COLLECTION_BOX", collectionBoxValues);
        
        Map<String, Double> tinValues = new HashMap<>();
        tinValues.put("Default", 22.0);
        tinValues.put("Mini", 12.0);
        MARKET_VALUES.put("TIN", tinValues);
    }
    
    /**
     * Analyze a single product and provide deal recommendation
     */
    public Map<String, Object> analyzeProduct(Map<String, Object> product) {
        Map<String, Object> analysis = new HashMap<>(product);
        
        try {
            String productType = String.valueOf(product.getOrDefault("productType", "OTHER"));
            String setName = String.valueOf(product.getOrDefault("setName", "Unknown Set"));
            Double price = getDoubleValue(product.get("normalizedPrice"));
            String language = String.valueOf(product.getOrDefault("language", "English"));
            Integer quantity = getIntegerValue(product.getOrDefault("quantity", 1));
            
            // Estimate market value
            Double marketValue = estimateMarketValue(productType, setName, language, quantity);
            analysis.put("marketValueEstimate", marketValue);
            
            // Calculate deal score
            DealScore dealScore = calculateDealScore(price, marketValue);
            analysis.put("dealScore", dealScore.score);
            analysis.put("recommendationLevel", dealScore.level);
            analysis.put("recommendationNotes", dealScore.notes);
            
            // Add additional analysis
            analysis.put("priceDeviation", calculatePriceDeviation(price, marketValue));
            analysis.put("dealPercentage", calculateDealPercentage(price, marketValue));
            
            // Add temporal analysis
            addTemporalAnalysis(analysis, product);
            
            // Add risk assessment
            analysis.put("riskLevel", assessRisk(product));
            
        } catch (Exception e) {
            log.error("Error analyzing product: {}", e.getMessage());
            analysis.put("dealScore", 3);
            analysis.put("recommendationLevel", "Fair Deal");
            analysis.put("recommendationNotes", "Unable to fully analyze - manual review recommended");
        }
        
        return analysis;
    }
    
    /**
     * Estimate market value for a product
     */
    private Double estimateMarketValue(String productType, String setName, String language, Integer quantity) {
        Double baseValue = 0.0;
        
        // Get base value from market data
        Map<String, Double> typeValues = MARKET_VALUES.get(productType);
        if (typeValues != null) {
            baseValue = typeValues.getOrDefault(setName, typeValues.getOrDefault("Default", 30.0));
        } else {
            // Default values for unknown product types
            switch (productType) {
                case "BUNDLE":
                    baseValue = 80.0;
                    break;
                case "THEME_DECK":
                    baseValue = 15.0;
                    break;
                case "ACCESSORIES":
                    baseValue = 10.0;
                    break;
                case "SINGLE_CARD":
                    baseValue = 5.0;
                    break;
                default:
                    baseValue = 20.0;
            }
        }
        
        // Apply language modifier
        if ("Japanese".equals(language)) {
            baseValue *= 1.15; // Japanese products typically 15% premium
        } else if (!"English".equals(language)) {
            baseValue *= 0.95; // Non-English (except Japanese) typically slight discount
        }
        
        // Apply quantity discount (bulk pricing)
        if (quantity > 1) {
            double bulkDiscount = 1.0 - (Math.min(quantity - 1, 10) * 0.02); // 2% discount per item, max 20%
            baseValue = baseValue * quantity * bulkDiscount;
        } else {
            baseValue = baseValue * quantity;
        }
        
        return baseValue;
    }
    
    /**
     * Calculate deal score and recommendation
     */
    private DealScore calculateDealScore(Double price, Double marketValue) {
        if (price == null || price <= 0 || marketValue == null || marketValue <= 0) {
            return new DealScore(3, "Fair Deal", "Unable to determine market value");
        }
        
        double priceRatio = price / marketValue;
        double savingsPercentage = ((marketValue - price) / marketValue) * 100;
        
        DealScore score = new DealScore();
        
        if (priceRatio <= 0.6) {
            // 40% or more below market
            score.score = 5;
            score.level = "Exceptional Deal";
            score.notes = String.format("%.0f%% below market value - Excellent opportunity!", Math.abs(savingsPercentage));
        } else if (priceRatio <= 0.8) {
            // 20-40% below market
            score.score = 4;
            score.level = "Great Deal";
            score.notes = String.format("%.0f%% below market value - Strong buy recommendation", Math.abs(savingsPercentage));
        } else if (priceRatio <= 1.1) {
            // Within 10% of market
            score.score = 3;
            score.level = "Fair Deal";
            score.notes = String.format("Within %.0f%% of typical market price", Math.abs(savingsPercentage));
        } else if (priceRatio <= 1.3) {
            // 10-30% above market
            score.score = 2;
            score.level = "Above Market";
            score.notes = String.format("%.0f%% above market value - Consider negotiating", Math.abs(savingsPercentage));
        } else {
            // More than 30% above market
            score.score = 1;
            score.level = "Poor Deal";
            score.notes = String.format("%.0f%% above market value - Not recommended unless rare/special", Math.abs(savingsPercentage));
        }
        
        return score;
    }
    
    /**
     * Calculate price deviation from market
     */
    private Double calculatePriceDeviation(Double price, Double marketValue) {
        if (price == null || marketValue == null || marketValue == 0) {
            return 0.0;
        }
        return price - marketValue;
    }
    
    /**
     * Calculate deal percentage
     */
    private Double calculateDealPercentage(Double price, Double marketValue) {
        if (price == null || marketValue == null || marketValue == 0) {
            return 0.0;
        }
        return ((marketValue - price) / marketValue) * 100;
    }
    
    /**
     * Add temporal analysis (how fresh is the listing)
     */
    private void addTemporalAnalysis(Map<String, Object> analysis, Map<String, Object> product) {
        try {
            String dateFound = String.valueOf(product.getOrDefault("dateFound", ""));
            if (!dateFound.isEmpty() && !"null".equals(dateFound)) {
                LocalDateTime listingDate = parseDateTime(dateFound);
                if (listingDate != null) {
                    long daysOld = ChronoUnit.DAYS.between(listingDate, LocalDateTime.now());
                    
                    String freshness;
                    if (daysOld <= 1) {
                        freshness = "Very Fresh";
                    } else if (daysOld <= 3) {
                        freshness = "Fresh";
                    } else if (daysOld <= 7) {
                        freshness = "Recent";
                    } else if (daysOld <= 14) {
                        freshness = "Aging";
                    } else {
                        freshness = "Stale";
                    }
                    
                    analysis.put("listingFreshness", freshness);
                    analysis.put("daysOld", daysOld);
                    
                    // Adjust recommendation based on freshness
                    if (daysOld > 7 && analysis.containsKey("recommendationNotes")) {
                        String notes = String.valueOf(analysis.get("recommendationNotes"));
                        notes += String.format(" (Note: Listing is %d days old)", daysOld);
                        analysis.put("recommendationNotes", notes);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse temporal data: {}", e.getMessage());
        }
    }
    
    /**
     * Parse various date formats including Excel serial numbers
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        String trimmed = dateStr.trim();
        
        try {
            // Try parsing as Excel serial number (days since 1900-01-01)
            if (trimmed.matches("\\d+(\\.\\d+)?")) {
                double excelDate = Double.parseDouble(trimmed);
                // Excel considers 1900-01-01 as day 1, but it's actually day 2 due to Excel bug
                // Subtract 2 to get correct date, then convert to LocalDate
                LocalDate baseDate = LocalDate.of(1899, 12, 30); // Excel epoch adjusted for bug
                LocalDate parsedDate = baseDate.plusDays((long) excelDate);
                
                // Extract time portion if present
                double timeFraction = excelDate - Math.floor(excelDate);
                int hours = (int) (timeFraction * 24);
                int minutes = (int) ((timeFraction * 24 * 60) % 60);
                
                return parsedDate.atTime(hours, minutes);
            }
            
            // Try standard ISO format
            return LocalDateTime.parse(trimmed);
            
        } catch (Exception e1) {
            try {
                // Try other common formats
                DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")
                };
                
                for (DateTimeFormatter formatter : formatters) {
                    try {
                        if (trimmed.contains(":")) {
                            return LocalDateTime.parse(trimmed, formatter);
                        } else {
                            return LocalDate.parse(trimmed, formatter).atStartOfDay();
                        }
                    } catch (Exception ignored) {
                        // Try next format
                    }
                }
                
            } catch (Exception e2) {
                log.debug("Could not parse date: {} - {}", trimmed, e2.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * Assess risk level of the deal
     */
    private String assessRisk(Map<String, Object> product) {
        String riskLevel = "Low";
        List<String> riskFactors = new ArrayList<>();
        
        // Check for suspiciously low prices
        Double price = getDoubleValue(product.get("normalizedPrice"));
        Double marketValue = getDoubleValue(product.get("marketValueEstimate"));
        
        if (marketValue != null && price != null && price < marketValue * 0.5) {
            riskFactors.add("Price unusually low");
            riskLevel = "High";
        }
        
        // Check for unknown products
        String productType = String.valueOf(product.getOrDefault("productType", ""));
        if ("OTHER".equals(productType) || "Unknown Set".equals(product.get("setName"))) {
            riskFactors.add("Product classification uncertain");
            if ("Low".equals(riskLevel)) {
                riskLevel = "Medium";
            }
        }
        
        // Check for bulk/lot items
        Integer quantity = getIntegerValue(product.get("quantity"));
        if (quantity != null && quantity > 10) {
            riskFactors.add("Large quantity");
            if ("Low".equals(riskLevel)) {
                riskLevel = "Medium";
            }
        }
        
        if (!riskFactors.isEmpty()) {
            String existingNotes = String.valueOf(product.getOrDefault("recommendationNotes", ""));
            product.put("recommendationNotes", existingNotes + " | Risk: " + String.join(", ", riskFactors));
        }
        
        return riskLevel;
    }
    
    /**
     * Batch analyze multiple products
     */
    public List<Map<String, Object>> analyzeProducts(List<Map<String, Object>> products) {
        List<Map<String, Object>> analyzed = new ArrayList<>();
        
        for (Map<String, Object> product : products) {
            analyzed.add(analyzeProduct(product));
        }
        
        // Sort by deal score (best deals first)
        analyzed.sort((a, b) -> {
            Integer scoreA = getIntegerValue(a.get("dealScore"));
            Integer scoreB = getIntegerValue(b.get("dealScore"));
            return scoreB.compareTo(scoreA);
        });
        
        return analyzed;
    }
    
    /**
     * Helper to safely get Double value
     */
    private Double getDoubleValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Helper to safely get Integer value
     */
    private Integer getIntegerValue(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
    
    /**
     * Inner class for deal score results
     */
    private static class DealScore {
        int score;
        String level;
        String notes;
        
        DealScore() {}
        
        DealScore(int score, String level, String notes) {
            this.score = score;
            this.level = level;
            this.notes = notes;
        }
    }
}