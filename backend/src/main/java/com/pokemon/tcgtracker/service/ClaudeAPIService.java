package com.pokemon.tcgtracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokemon.tcgtracker.constants.PromptConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Slf4j
@Service
public class ClaudeAPIService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${anthropic.api.key:}")
    private String apiKey;
    
    @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}")
    private String apiUrl;
    
    @Value("${anthropic.api.model:claude-3-5-sonnet-20241022}")
    private String model;
    
    @Value("${anthropic.api.max-tokens:20000}")
    private int maxTokens;
    
    public ClaudeAPIService() {
        this.webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }
    
    @PostConstruct
    public void init() {
        log.info("Initializing Claude API Service...");
        log.info("API Key configured: {}", apiKey != null && !apiKey.isEmpty());
        if (apiKey != null && !apiKey.isEmpty()) {
            log.info("API Key length: {}", apiKey.length());
            log.info("API Key format valid: {}", apiKey.startsWith("sk-ant"));
            log.info("API Key prefix: {}...", apiKey.substring(0, Math.min(20, apiKey.length())));
        } else {
            log.warn("ANTHROPIC_API_KEY environment variable is not set or empty!");
        }
        log.info("API URL: {}", apiUrl);
        log.info("Model: {}", model);
        log.info("Max Tokens: {}", maxTokens);
    }
    
    /**
     * Test the Claude API connection with a simple message
     */
    public String testClaudeAPI() {
        try {
            String response = callClaudeAPI("Hello, please respond with 'API test successful'");
            return "Success: " + response;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Analyze CSV data using the master CSV analysis prompt
     */
    public String analyzeData(String csvContent) {
        try {
            String fullPrompt = PromptConstants.MASTER_CSV_ANALYZE_PROMPT + "\n\nHere is my CSV:\n" + csvContent;
            String response = callClaudeAPI(fullPrompt);
            return response;
        } catch (Exception e) {
            log.error("Error analyzing CSV data with Claude API: {}", e.getMessage());
            throw new RuntimeException("Failed to analyze CSV data: " + e.getMessage());
        }
    }
    
    /**
     * Classify and analyze a Pokemon TCG product using Claude API
     */
    public Map<String, Object> classifyAndAnalyzeProduct(Map<String, Object> rawProduct) {
        try {
            String prompt = buildProductAnalysisPrompt(rawProduct);
            String response = callClaudeAPI(prompt);
            return parseProductAnalysisResponse(response, rawProduct);
        } catch (Exception e) {
            log.error("Error calling Claude API for product analysis: {}", e.getMessage());
            return createFallbackAnalysis(rawProduct);
        }
    }
    
    /**
     * Analyze multiple products in batch using Claude API
     */
    public List<Map<String, Object>> analyzeProductsBatch(List<Map<String, Object>> products) {
        try {
            String prompt = buildBatchAnalysisPrompt(products);
            String response = callClaudeAPI(prompt);
            return parseBatchAnalysisResponse(response, products);
        } catch (Exception e) {
            log.error("Error calling Claude API for batch analysis: {}", e.getMessage());
            // Fallback to individual analysis
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> product : products) {
                results.add(classifyAndAnalyzeProduct(product));
            }
            return results;
        }
    }
    
    /**
     * Build prompt for single product analysis
     */
    private String buildProductAnalysisPrompt(Map<String, Object> product) {
        return String.format("""
            You are a Pokemon TCG marketplace data analyst. Clean, standardize, and analyze this listing with comprehensive market intelligence.
            
            RAW DATA:
            Item Name: %s
            Price: %s
            Set: %s
            Quantity: %s
            Price Unit: %s
            Location: %s
            Notes: %s
            Date Found: %s
            Marketplace URL: %s
            
            TASK: Provide comprehensive Pokemon TCG analysis and return ONLY a JSON response with these exact fields:
            
            {
              "cleanedItemName": "standardized product name (remove caps, fix typos, standard format)",
              "productType": "ETB|BOOSTER_BOX|BOOSTER_PACK|COLLECTION_BOX|TIN|BUNDLE|SINGLE_CARD|THEME_DECK|ACCESSORIES|PROMO|GRADED_CARD|OTHER",
              "setName": "official Pokemon TCG set name (standardized)",
              "language": "English|Japanese|Korean|Chinese|French|German|Spanish|Other",
              "condition": "Near Mint|Lightly Played|Moderately Played|Heavily Played|Damaged|Sealed|Graded|Unknown",
              "isSealed": true/false,
              "isGraded": true/false,
              "gradingCompany": "PSA|BGS|CGC|SGC|None|Unknown",
              "gradingScore": numeric_grade_or_null,
              "marketValueEstimate": current_market_value_USD,
              "dealScore": 1-10_integer_score,
              "recommendationLevel": "Must Buy (9-10)|Excellent Deal (7-8)|Good Deal (5-6)|Fair Price (4)|Overpriced (1-3)",
              "recommendationNotes": "detailed analysis with specific reasoning, market context, and actionable advice",
              "dealPercentage": percentage_savings_or_markup,
              "pricePerUnit": price_per_individual_item,
              "riskLevel": "Low|Medium|High",
              "riskFactors": ["specific", "risk", "factors"],
              "marketTrends": "current market trend analysis",
              "competitiveComparison": "how this compares to similar listings",
              "urgencyLevel": "Act Fast|Consider Soon|No Rush|Avoid",
              "investmentPotential": "High|Medium|Low|Speculative|Declining",
              "classificationConfidence": 0.0-1.0_confidence_score,
              "dataQuality": "Excellent|Good|Fair|Poor|Incomplete",
              "warningFlags": ["any", "red", "flags", "or", "concerns"]
            }
            
            ANALYSIS GUIDELINES:
            - Use current 2024 Pokemon TCG market prices (TCGPlayer, eBay sold, PWCC)
            - Score 1-10: 1-3=Overpriced, 4=Fair, 5-6=Good Deal, 7-8=Excellent, 9-10=Must Buy
            - Consider set popularity, card meta relevance, print run size
            - Factor in condition, language, and seller reputation
            - Identify potential scams, fakes, or damaged goods
            - Provide specific, actionable investment advice
            - Include current market trends and competitive analysis
            """,
            product.getOrDefault("itemName", ""),
            product.getOrDefault("price", ""),
            product.getOrDefault("set", ""),
            product.getOrDefault("quantity", ""),
            product.getOrDefault("priceUnit", ""),
            product.getOrDefault("location", ""),
            product.getOrDefault("notes", ""),
            product.getOrDefault("dateFound", ""),
            product.getOrDefault("marketplaceUrl", "")
        );
    }
    
    /**
     * Build prompt for batch analysis
     */
    private String buildBatchAnalysisPrompt(List<Map<String, Object>> products) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
            You are a Pokemon TCG marketplace data analyst. Analyze and standardize these listings with comprehensive market intelligence.
            
            BATCH ANALYSIS - Return a JSON array with analysis for each product in the same order:
            
            Products to analyze:
            """);
        
        for (int i = 0; i < products.size() && i < 8; i++) { // Reduced batch size for better quality
            Map<String, Object> product = products.get(i);
            prompt.append(String.format("""
                
                Product %d:
                - Item Name: %s
                - Price: %s
                - Set: %s
                - Quantity: %s
                - Price Unit: %s
                - Location: %s
                - Notes: %s
                - Date Found: %s
                - Marketplace URL: %s
                """, 
                i + 1,
                product.getOrDefault("itemName", ""),
                product.getOrDefault("price", ""),
                product.getOrDefault("set", ""),
                product.getOrDefault("quantity", ""),
                product.getOrDefault("priceUnit", ""),
                product.getOrDefault("location", ""),
                product.getOrDefault("notes", ""),
                product.getOrDefault("dateFound", ""),
                product.getOrDefault("marketplaceUrl", "")
            ));
        }
        
        prompt.append("""
            
            For each product, provide comprehensive analysis JSON with ALL these fields:
            {
              "cleanedItemName": "standardized product name",
              "productType": "ETB|BOOSTER_BOX|BOOSTER_PACK|COLLECTION_BOX|TIN|BUNDLE|SINGLE_CARD|THEME_DECK|ACCESSORIES|PROMO|GRADED_CARD|OTHER",
              "setName": "official Pokemon TCG set name",
              "language": "English|Japanese|Korean|Chinese|French|German|Spanish|Other",
              "condition": "Near Mint|Lightly Played|Moderately Played|Heavily Played|Damaged|Sealed|Graded|Unknown",
              "isSealed": true/false,
              "isGraded": true/false,
              "gradingCompany": "PSA|BGS|CGC|SGC|None|Unknown",
              "gradingScore": numeric_grade_or_null,
              "marketValueEstimate": current_market_value_USD,
              "dealScore": 1-10_integer_score,
              "recommendationLevel": "Must Buy (9-10)|Excellent Deal (7-8)|Good Deal (5-6)|Fair Price (4)|Overpriced (1-3)",
              "recommendationNotes": "detailed analysis with reasoning and advice",
              "dealPercentage": percentage_savings_or_markup,
              "pricePerUnit": price_per_individual_item,
              "riskLevel": "Low|Medium|High",
              "riskFactors": ["specific", "risk", "factors"],
              "marketTrends": "current market trend analysis",
              "competitiveComparison": "comparison to similar listings",
              "urgencyLevel": "Act Fast|Consider Soon|No Rush|Avoid",
              "investmentPotential": "High|Medium|Low|Speculative|Declining",
              "classificationConfidence": 0.0-1.0_confidence_score,
              "dataQuality": "Excellent|Good|Fair|Poor|Incomplete",
              "warningFlags": ["any", "concerns", "or", "red", "flags"]
            }
            
            ANALYSIS GUIDELINES:
            - Use current 2024 Pokemon TCG market data
            - Score 1-10: 1-3=Overpriced, 4=Fair, 5-6=Good Deal, 7-8=Excellent, 9-10=Must Buy
            - Provide actionable investment recommendations
            - Identify scams, fakes, and quality issues
            
            Return as valid JSON array: [analysis1, analysis2, ...]
            """);
        
        return prompt.toString();
    }
    
    /**
     * Call Claude API with the given prompt
     */
    private String callClaudeAPI(String prompt) {
        log.debug("API Key present: {}", apiKey != null && !apiKey.isEmpty());
        log.debug("API Key length: {}", apiKey != null ? apiKey.length() : 0);
        log.debug("API Key starts with sk-ant: {}", apiKey != null && apiKey.startsWith("sk-ant"));
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("Anthropic API key is null or empty. Check ANTHROPIC_API_KEY environment variable.");
            throw new RuntimeException("Anthropic API key not configured");
        }
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", maxTokens);
        
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "user");
        message.put("content", prompt);
        messages.add(message);
        requestBody.put("messages", messages);
        
        try {
            log.info("Making request to Claude API: {}", apiUrl);
            log.info("Request headers: x-api-key={}, anthropic-version=2023-06-01", 
                     apiKey.substring(0, 15) + "...");
            log.info("Request body: {}", objectMapper.writeValueAsString(requestBody));
            
            String response = webClient.post()
                .uri(apiUrl)
                .header("x-api-key", apiKey)  // Changed from Authorization: Bearer
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            log.info("Claude API Response: {}", response);
            
            // Extract content from Claude response
            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode contentArray = responseNode.get("content");
            if (contentArray != null && contentArray.isArray() && contentArray.size() > 0) {
                return contentArray.get(0).get("text").asText();
            }
            
            throw new RuntimeException("Invalid response format from Claude API");
            
        } catch (Exception e) {
            log.error("Error calling Claude API: {}", e.getMessage());
            log.error("Full error details: ", e);
            throw new RuntimeException("Failed to call Claude API: " + e.getMessage());
        }
    }
    
    /**
     * Parse Claude's response for single product
     */
    private Map<String, Object> parseProductAnalysisResponse(String response, Map<String, Object> originalProduct) {
        try {
            // Extract JSON from response (Claude might include explanatory text)
            String jsonStr = extractJsonFromResponse(response);
            JsonNode analysisNode = objectMapper.readTree(jsonStr);
            
            Map<String, Object> result = new HashMap<>(originalProduct);
            
            // Apply Claude's comprehensive analysis
            applyAnalysisToProduct(result, analysisNode);
            
            // Parse price from original data
            String priceStr = String.valueOf(originalProduct.getOrDefault("price", "0"));
            result.put("normalizedPrice", parsePrice(priceStr));
            
            return result;
            
        } catch (Exception e) {
            log.error("Error parsing Claude response: {}", e.getMessage());
            return createFallbackAnalysis(originalProduct);
        }
    }
    
    /**
     * Parse Claude's batch response
     */
    private List<Map<String, Object>> parseBatchAnalysisResponse(String response, List<Map<String, Object>> originalProducts) {
        try {
            String jsonStr = extractJsonFromResponse(response);
            JsonNode analysisArray = objectMapper.readTree(jsonStr);
            
            List<Map<String, Object>> results = new ArrayList<>();
            
            for (int i = 0; i < originalProducts.size(); i++) {
                Map<String, Object> originalProduct = originalProducts.get(i);
                
                if (analysisArray.has(i)) {
                    JsonNode analysisNode = analysisArray.get(i);
                    Map<String, Object> result = new HashMap<>(originalProduct);
                    
                    // Apply Claude's analysis similar to single product parsing
                    applyAnalysisToProduct(result, analysisNode);
                    results.add(result);
                } else {
                    // Fallback if batch analysis is incomplete
                    results.add(createFallbackAnalysis(originalProduct));
                }
            }
            
            return results;
            
        } catch (Exception e) {
            log.error("Error parsing batch Claude response: {}", e.getMessage());
            // Fallback to individual analysis
            List<Map<String, Object>> results = new ArrayList<>();
            for (Map<String, Object> product : originalProducts) {
                results.add(createFallbackAnalysis(product));
            }
            return results;
        }
    }
    
    /**
     * Apply analysis from Claude to product
     */
    private void applyAnalysisToProduct(Map<String, Object> product, JsonNode analysisNode) {
        // Basic fields
        if (analysisNode.has("cleanedItemName")) {
            product.put("cleanedItemName", analysisNode.get("cleanedItemName").asText());
        }
        if (analysisNode.has("productType")) {
            product.put("productType", analysisNode.get("productType").asText());
        }
        if (analysisNode.has("setName")) {
            product.put("setName", analysisNode.get("setName").asText());
        }
        if (analysisNode.has("language")) {
            product.put("language", analysisNode.get("language").asText());
        }
        
        // Enhanced condition and grading fields
        if (analysisNode.has("condition")) {
            product.put("condition", analysisNode.get("condition").asText());
        }
        if (analysisNode.has("isSealed")) {
            product.put("isSealed", analysisNode.get("isSealed").asBoolean());
        }
        if (analysisNode.has("isGraded")) {
            product.put("isGraded", analysisNode.get("isGraded").asBoolean());
        }
        if (analysisNode.has("gradingCompany")) {
            product.put("gradingCompany", analysisNode.get("gradingCompany").asText());
        }
        if (analysisNode.has("gradingScore") && !analysisNode.get("gradingScore").isNull()) {
            product.put("gradingScore", analysisNode.get("gradingScore").asDouble());
        }
        
        // Market analysis fields
        if (analysisNode.has("marketValueEstimate")) {
            product.put("marketValueEstimate", analysisNode.get("marketValueEstimate").asDouble());
        }
        if (analysisNode.has("dealScore")) {
            product.put("dealScore", analysisNode.get("dealScore").asInt());
        }
        if (analysisNode.has("recommendationLevel")) {
            product.put("recommendationLevel", analysisNode.get("recommendationLevel").asText());
        }
        if (analysisNode.has("recommendationNotes")) {
            product.put("recommendationNotes", analysisNode.get("recommendationNotes").asText());
        }
        if (analysisNode.has("dealPercentage")) {
            product.put("dealPercentage", analysisNode.get("dealPercentage").asDouble());
        }
        if (analysisNode.has("pricePerUnit")) {
            product.put("pricePerUnit", analysisNode.get("pricePerUnit").asDouble());
        }
        
        // Risk and quality fields
        if (analysisNode.has("riskLevel")) {
            product.put("riskLevel", analysisNode.get("riskLevel").asText());   
        }
        if (analysisNode.has("riskFactors") && analysisNode.get("riskFactors").isArray()) {
            List<String> riskFactors = new ArrayList<>();
            analysisNode.get("riskFactors").forEach(node -> riskFactors.add(node.asText()));
            product.put("riskFactors", riskFactors);
        }
        
        // Market intelligence fields
        if (analysisNode.has("marketTrends")) {
            product.put("marketTrends", analysisNode.get("marketTrends").asText());
        }
        if (analysisNode.has("competitiveComparison")) {
            product.put("competitiveComparison", analysisNode.get("competitiveComparison").asText());
        }
        if (analysisNode.has("urgencyLevel")) {
            product.put("urgencyLevel", analysisNode.get("urgencyLevel").asText());
        }
        if (analysisNode.has("investmentPotential")) {
            product.put("investmentPotential", analysisNode.get("investmentPotential").asText());
        }
        
        // Quality and confidence fields
        if (analysisNode.has("classificationConfidence")) {
            product.put("classificationConfidence", analysisNode.get("classificationConfidence").asDouble());
        }
        if (analysisNode.has("dataQuality")) {
            product.put("dataQuality", analysisNode.get("dataQuality").asText());
        }
        if (analysisNode.has("warningFlags") && analysisNode.get("warningFlags").isArray()) {
            List<String> warningFlags = new ArrayList<>();
            analysisNode.get("warningFlags").forEach(node -> warningFlags.add(node.asText()));
            product.put("warningFlags", warningFlags);
        }
        
        // Parse price from original data
        String priceStr = String.valueOf(product.getOrDefault("price", "0"));
        product.put("normalizedPrice", parsePrice(priceStr));
    }
    
    /**
     * Extract JSON from Claude's response text
     */
    private String extractJsonFromResponse(String response) {
        // Find JSON block in response
        int jsonStart = response.indexOf("{");
        if (jsonStart == -1) {
            jsonStart = response.indexOf("[");
        }
        
        if (jsonStart == -1) {
            throw new RuntimeException("No JSON found in Claude response");
        }
        
        // Try to find the end of JSON
        String jsonPart = response.substring(jsonStart);
        
        // Simple bracket counting to find JSON end
        int bracketCount = 0;
        int endIndex = -1;
        boolean inString = false;
        boolean escaped = false;
        char startChar = jsonPart.charAt(0);
        char endChar = startChar == '{' ? '}' : ']';
        
        for (int i = 0; i < jsonPart.length(); i++) {
            char c = jsonPart.charAt(i);
            
            if (escaped) {
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == startChar) {
                    bracketCount++;
                } else if (c == endChar) {
                    bracketCount--;
                    if (bracketCount == 0) {
                        endIndex = i + 1;
                        break;
                    }
                }
            }
        }
        
        if (endIndex > 0) {
            return jsonPart.substring(0, endIndex);
        }
        
        return jsonPart; // Return whole part if we can't find the end
    }
    
    /**
     * Create fallback analysis when Claude API fails
     */
    private Map<String, Object> createFallbackAnalysis(Map<String, Object> originalProduct) {
        Map<String, Object> result = new HashMap<>(originalProduct);
        
        String itemName = String.valueOf(originalProduct.getOrDefault("itemName", ""));
        result.put("cleanedItemName", itemName);
        result.put("productType", "OTHER");
        result.put("setName", "Unknown Set");
        result.put("language", "English");
        result.put("marketValueEstimate", 0.0);
        result.put("dealScore", 3);
        result.put("recommendationLevel", "Fair Deal");
        result.put("recommendationNotes", "Unable to analyze - Claude API unavailable");
        result.put("dealPercentage", 0.0);
        result.put("riskLevel", "Medium");
        result.put("classificationConfidence", 0.1);
        
        // Parse price from original data with validation
        String priceStr = String.valueOf(originalProduct.getOrDefault("price", "0"));
        Double normalizedPrice = 0.0;
        
        // Only parse if it looks like a valid price (not a timestamp)
        if (isValidPrice(priceStr)) {
            normalizedPrice = parsePrice(priceStr);
        } else {
            log.warn("Invalid price detected in Claude fallback analysis: '{}' for item: '{}'", 
                    priceStr, itemName);
        }
        
        result.put("normalizedPrice", normalizedPrice);
        
        return result;
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
            
            // Reject values that look like Excel date serial numbers or unreasonably high prices
            if (numericValue > 10000) {
                return false;
            }
            
            // Reject values that are too small to be realistic prices
            if (numericValue < 0.1) {
                return false;
            }
            
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Simple price parsing
     */
    private Double parsePrice(String priceStr) {
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
}