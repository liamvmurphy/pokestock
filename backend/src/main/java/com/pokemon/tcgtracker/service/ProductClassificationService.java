package com.pokemon.tcgtracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProductClassificationService {

    // Product type mappings
    private static final Map<String, List<String>> PRODUCT_TYPE_MAPPINGS = new HashMap<>();
    private static final Map<String, String> SET_ABBREVIATIONS = new HashMap<>();
    private static final Pattern JAPANESE_PATTERN = Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FAF]");
    
    static {
        // Initialize product type mappings
        PRODUCT_TYPE_MAPPINGS.put("BOOSTER_BOX", Arrays.asList(
            "booster box", "bb", "box", "sealed box", "display box", "case"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("ETB", Arrays.asList(
            "etb", "elite trainer box", "trainer box", "elite trainer", "trainer kit"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("BOOSTER_PACK", Arrays.asList(
            "booster pack", "pack", "booster", "single pack", "loose pack"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("COLLECTION_BOX", Arrays.asList(
            "collection box", "collection", "premium collection", "box collection",
            "special collection", "anniversary collection", "trainer collection"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("TIN", Arrays.asList(
            "tin", "mini tin", "pokeball tin", "collector tin", "storage tin"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("BUNDLE", Arrays.asList(
            "bundle", "lot", "bulk", "set", "combo", "package deal"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("SINGLE_CARD", Arrays.asList(
            "single", "card", "individual card", "chase card", "alt art",
            "secret rare", "full art", "rainbow rare", "gold card", "promo"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("THEME_DECK", Arrays.asList(
            "theme deck", "battle deck", "starter deck", "league battle deck",
            "championship deck", "world championship deck", "challenger deck"
        ));
        
        PRODUCT_TYPE_MAPPINGS.put("ACCESSORIES", Arrays.asList(
            "sleeves", "playmat", "dice", "damage counters", "binder",
            "deck box", "card protectors", "portfolio", "supplies"
        ));
        
        // Initialize set abbreviation mappings
        SET_ABBREVIATIONS.put("sv", "Scarlet & Violet");
        SET_ABBREVIATIONS.put("sv01", "Scarlet & Violet Base");
        SET_ABBREVIATIONS.put("sv02", "Paldea Evolved");
        SET_ABBREVIATIONS.put("sv03", "Obsidian Flames");
        SET_ABBREVIATIONS.put("sv04", "Paradox Rift");
        SET_ABBREVIATIONS.put("sv05", "Temporal Forces");
        SET_ABBREVIATIONS.put("sv06", "Twilight Masquerade");
        SET_ABBREVIATIONS.put("sv07", "Stellar Crown");
        SET_ABBREVIATIONS.put("sv08", "Surging Sparks");
        
        SET_ABBREVIATIONS.put("pal", "Paldea Evolved");
        SET_ABBREVIATIONS.put("obf", "Obsidian Flames");
        SET_ABBREVIATIONS.put("par", "Paradox Rift");
        SET_ABBREVIATIONS.put("tef", "Temporal Forces");
        SET_ABBREVIATIONS.put("twm", "Twilight Masquerade");
        SET_ABBREVIATIONS.put("scr", "Stellar Crown");
        SET_ABBREVIATIONS.put("ssp", "Surging Sparks");
        
        SET_ABBREVIATIONS.put("mew", "Pokemon 151");
        SET_ABBREVIATIONS.put("paf", "Paldean Fates");
        SET_ABBREVIATIONS.put("sfa", "Shrouded Fable");
        
        // Sword & Shield era
        SET_ABBREVIATIONS.put("ssh", "Sword & Shield");
        SET_ABBREVIATIONS.put("rcl", "Rebel Clash");
        SET_ABBREVIATIONS.put("daa", "Darkness Ablaze");
        SET_ABBREVIATIONS.put("viv", "Vivid Voltage");
        SET_ABBREVIATIONS.put("shf", "Shining Fates");
        SET_ABBREVIATIONS.put("bst", "Battle Styles");
        SET_ABBREVIATIONS.put("cre", "Chilling Reign");
        SET_ABBREVIATIONS.put("evs", "Evolving Skies");
        SET_ABBREVIATIONS.put("cel", "Celebrations");
        SET_ABBREVIATIONS.put("fst", "Fusion Strike");
        SET_ABBREVIATIONS.put("brs", "Brilliant Stars");
        SET_ABBREVIATIONS.put("ast", "Astral Radiance");
        SET_ABBREVIATIONS.put("pgo", "Pokemon GO");
        SET_ABBREVIATIONS.put("loz", "Lost Origin");
        SET_ABBREVIATIONS.put("sit", "Silver Tempest");
        SET_ABBREVIATIONS.put("cz", "Crown Zenith");
        
        // Japanese set indicators
        SET_ABBREVIATIONS.put("黒炎の支配者", "Obsidian Flames");
        SET_ABBREVIATIONS.put("ポケモン151", "Pokemon 151");
        SET_ABBREVIATIONS.put("古代の咆哮", "Ancient Roar");
        SET_ABBREVIATIONS.put("未来の一閃", "Future Flash");
    }
    
    /**
     * Classify and normalize a product listing
     */
    public Map<String, Object> classifyProduct(Map<String, Object> rawProduct) {
        Map<String, Object> classified = new HashMap<>(rawProduct);
        
        String itemName = String.valueOf(rawProduct.getOrDefault("itemName", ""));
        String price = String.valueOf(rawProduct.getOrDefault("price", "0"));
        
        // Clean the item name
        String cleanedName = cleanItemName(itemName);
        classified.put("cleanedItemName", cleanedName);
        
        // Detect language
        String language = detectLanguage(itemName);
        classified.put("language", language);
        
        // Classify product type
        String productType = classifyProductType(cleanedName.toLowerCase());
        classified.put("productType", productType);
        
        // Extract and normalize set name
        String setName = extractAndNormalizeSetName(cleanedName);
        classified.put("setName", setName);
        
        // Parse and normalize price
        Double normalizedPrice = parsePrice(price);
        classified.put("normalizedPrice", normalizedPrice);
        
        // Add classification confidence
        classified.put("classificationConfidence", calculateConfidence(cleanedName, productType));
        
        return classified;
    }
    
    /**
     * Clean and normalize item name
     */
    private String cleanItemName(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "Unknown Item";
        }
        
        String cleaned = itemName.trim();
        
        // Remove excessive whitespace
        cleaned = cleaned.replaceAll("\\s+", " ");
        
        // Remove common noise words at the beginning
        cleaned = cleaned.replaceAll("^(WTS|WTB|WTT|FS|FT|SELLING|BUYING)\\s*:?\\s*", "");
        
        // Standardize common terms
        cleaned = cleaned.replaceAll("(?i)pokemon", "Pokemon");
        cleaned = cleaned.replaceAll("(?i)etb", "ETB");
        cleaned = cleaned.replaceAll("(?i)tcg", "TCG");
        
        // Remove emoji and special characters (but keep Japanese characters)
        if (!JAPANESE_PATTERN.matcher(cleaned).find()) {
            cleaned = cleaned.replaceAll("[^a-zA-Z0-9\\s&\\-\\.\\(\\)]", "");
        }
        
        return cleaned.trim();
    }
    
    /**
     * Detect language of the listing
     */
    private String detectLanguage(String text) {
        if (text == null) return "English";
        
        String lowerText = text.toLowerCase();
        
        // Check for explicit Japanese indicators
        if (lowerText.contains("japanese") || lowerText.contains("jp") || 
            lowerText.contains("jpn") || lowerText.contains("日本")) {
            return "Japanese";
        }
        
        // Check for Japanese characters
        if (JAPANESE_PATTERN.matcher(text).find()) {
            return "Japanese";
        }
        
        // Check for other language indicators
        if (lowerText.contains("korean") || lowerText.contains("kr")) {
            return "Korean";
        }
        
        if (lowerText.contains("chinese") || lowerText.contains("cn")) {
            return "Chinese";
        }
        
        if (lowerText.contains("french") || lowerText.contains("fr")) {
            return "French";
        }
        
        if (lowerText.contains("german") || lowerText.contains("de")) {
            return "German";
        }
        
        if (lowerText.contains("spanish") || lowerText.contains("es")) {
            return "Spanish";
        }
        
        // Default to English
        return "English";
    }
    
    /**
     * Classify the product type based on the item name
     */
    private String classifyProductType(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "OTHER";
        }
        
        String lowerName = itemName.toLowerCase();
        
        // Check each product type mapping
        for (Map.Entry<String, List<String>> entry : PRODUCT_TYPE_MAPPINGS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (lowerName.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }
        
        // Special cases for combined products
        if (lowerName.contains("etb") && lowerName.contains("booster")) {
            return "BUNDLE";
        }
        
        if (lowerName.contains("lot") || lowerName.contains("bulk")) {
            return "BUNDLE";
        }
        
        // Default to OTHER if no match
        return "OTHER";
    }
    
    /**
     * Extract and normalize set name from item name
     */
    private String extractAndNormalizeSetName(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return "Unknown Set";
        }
        
        String lowerName = itemName.toLowerCase();
        
        // First check for exact set abbreviations
        for (Map.Entry<String, String> entry : SET_ABBREVIATIONS.entrySet()) {
            String abbreviation = entry.getKey();
            String fullName = entry.getValue();
            
            // Check for abbreviation with word boundaries
            if (lowerName.matches(".*\\b" + abbreviation + "\\b.*")) {
                return fullName;
            }
        }
        
        // Check for full set names
        List<String> commonSets = Arrays.asList(
            "Scarlet & Violet", "Paldea Evolved", "Obsidian Flames",
            "Paradox Rift", "Pokemon 151", "Temporal Forces",
            "Twilight Masquerade", "Stellar Crown", "Surging Sparks",
            "Evolving Skies", "Brilliant Stars", "Astral Radiance",
            "Lost Origin", "Silver Tempest", "Crown Zenith",
            "Shining Fates", "Celebrations", "Champions Path"
        );
        
        for (String setName : commonSets) {
            if (lowerName.contains(setName.toLowerCase())) {
                return setName;
            }
        }
        
        // Special patterns for sets
        if (lowerName.contains("151")) {
            return "Pokemon 151";
        }
        
        if (lowerName.contains("base set")) {
            if (lowerName.contains("scarlet") || lowerName.contains("violet")) {
                return "Scarlet & Violet Base";
            }
            return "Base Set";
        }
        
        // Check for Japanese set names
        if (itemName.contains("黒炎の支配者")) {
            return "Obsidian Flames (Japanese)";
        }
        if (itemName.contains("ポケモン151")) {
            return "Pokemon 151 (Japanese)";
        }
        
        return "Unknown Set";
    }
    
    /**
     * Parse price string to double
     */
    private Double parsePrice(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return 0.0;
        }
        
        String trimmed = priceStr.trim().toLowerCase();
        
        // Skip if it's clearly a unit, not a price
        if (trimmed.equals("each") || trimmed.equals("lot") || trimmed.equals("set") || 
            trimmed.equals("bundle") || trimmed.equals("piece") || trimmed.equals("item") ||
            trimmed.equals("per") || trimmed.equals("unit") || trimmed.equals("pack") ||
            trimmed.length() > 20) { // Very long strings are probably not prices
            return 0.0;
        }
        
        try {
            // Remove currency symbols and keep only numbers, dots, and commas
            String cleaned = priceStr.replaceAll("[^0-9.,]", "");
            
            if (cleaned.isEmpty()) {
                return 0.0;
            }
            
            // Handle different decimal separators
            // If there are multiple commas or dots, assume last one is decimal separator
            if (cleaned.contains(",") && cleaned.contains(".")) {
                int lastComma = cleaned.lastIndexOf(",");
                int lastDot = cleaned.lastIndexOf(".");
                if (lastDot > lastComma) {
                    // Format like 1,234.56 - remove commas
                    cleaned = cleaned.replace(",", "");
                } else {
                    // Format like 1.234,56 - replace comma with dot, remove other dots
                    cleaned = cleaned.substring(0, lastComma).replace(".", "") + 
                             "." + cleaned.substring(lastComma + 1);
                }
            } else if (cleaned.contains(",")) {
                // Only commas - could be thousands separator or decimal
                long commaCount = cleaned.chars().filter(ch -> ch == ',').count();
                if (commaCount == 1) {
                    int commaPos = cleaned.indexOf(",");
                    String afterComma = cleaned.substring(commaPos + 1);
                    if (afterComma.length() <= 2) {
                        // Probably decimal separator
                        cleaned = cleaned.replace(",", ".");
                    } else {
                        // Probably thousands separator
                        cleaned = cleaned.replace(",", "");
                    }
                } else {
                    // Multiple commas - probably thousands separators
                    cleaned = cleaned.replace(",", "");
                }
            }
            
            // Parse the cleaned string
            double price = Double.parseDouble(cleaned);
            
            // Sanity check - reject unreasonable prices
            if (price < 0 || price > 100000) {
                log.debug("Unreasonable price parsed: {} from '{}'", price, priceStr);
                return 0.0;
            }
            
            return price;
            
        } catch (NumberFormatException e) {
            log.debug("Could not parse price: {} ({})", priceStr, e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Calculate classification confidence score
     */
    private double calculateConfidence(String itemName, String productType) {
        if ("OTHER".equals(productType)) {
            return 0.3;
        }
        
        // Higher confidence for exact matches
        String lowerName = itemName.toLowerCase();
        if (lowerName.contains("etb") && "ETB".equals(productType)) {
            return 0.95;
        }
        if (lowerName.contains("booster box") && "BOOSTER_BOX".equals(productType)) {
            return 0.95;
        }
        
        // Medium confidence for partial matches
        return 0.75;
    }
    
    /**
     * Batch classify multiple products
     */
    public List<Map<String, Object>> classifyProducts(List<Map<String, Object>> products) {
        return products.stream()
            .map(this::classifyProduct)
            .collect(Collectors.toList());
    }
}