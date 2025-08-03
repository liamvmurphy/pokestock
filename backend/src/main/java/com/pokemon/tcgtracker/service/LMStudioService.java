package com.pokemon.tcgtracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokemon.tcgtracker.model.LMStudioRequest;
import com.pokemon.tcgtracker.model.LMStudioResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class LMStudioService {

    @Qualifier("lmStudioRestTemplate")
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    @Value("${lmstudio.model}")
    private String model;
    
    public String getModel() {
        return model;
    }

    private static final String MARKETPLACE_SYSTEM_PROMPT = """
            You are analyzing OCR-extracted text from a Facebook Marketplace Pokemon card listing screenshot.
            
                CONTEXT:
                - The input is ALL text detected in the image via OCR
                - This includes UI elements, buttons, prices, descriptions, and any visible text
                - Facebook Marketplace shows the main price at the top, but sellers often list multiple items with individual prices in the description
            
                YOUR TASK:
                1. First, identify and extract the actual LISTING DESCRIPTION from all the OCR text
                2. The description is usually the longer paragraph/list that details what's being sold
                3. Ignore UI elements like "Message", "Send", "Share", navigation text, etc.
                4. Focus on text that describes Pokemon products and prices
            
                WHAT TO LOOK FOR IN THE DESCRIPTION:
                - Pokemon card names (Charizard, Pikachu, etc.)
                - Set names (Base Set, Vivid Voltage, Paldea Evolved, etc.)
                - Product types: Singles, Booster Box, ETB (Elite Trainer Box), Bundle, Collection Box, Tin
                - Prices next to items (common formats: "$25", "- $25", ": $25", "$25 each")
                - Conditions: NM (Near Mint), LP (Lightly Played), MP, HP, Sealed, New, Used
                - Lists of multiple items with individual pricing
            
                COMMON SELLER PATTERNS:
                - "Charizard VMAX - $45"
                - "Base Set Booster Box $5000"
                - "ETBs: Vivid Voltage $65, Darkness Ablaze $55"
                - Numbered lists with items and prices
                - "All cards NM condition"
                - "Prices firm" or "OBO" (or best offer)
            
                OUTPUT JSON:
                {
                  "mainListingPrice": "Price shown at top of listing (e.g., A$25) or empty string if not found",
                  "extractedDescription": "The actual product description text found or empty string",
                  "items": [
                    {
                      "itemName": "Specific card or product name (REQUIRED - never empty)",
                      "set": "Pokemon set if identifiable or empty string",
                      "productType": "Single|Booster Pack|Booster Box|ETB|Collection Box|Bundle|Tin|OTHER",
                      "price": 0.00,
                      "quantity": 1,
                      "priceUnit": "each|lot|obo",
                      "notes": "Any specific notes about this item or empty string"
                    }
                  ],
                  "location": "Location if mentioned or empty string",
                  "hasMultipleItems": true/false,
                  "rawOcrText": "Complete OCR output for debugging"
                }
            
                PARSING RULES:
                1. If you see a header price (like A$25) but items listed with different prices, the header is just the cheapest item or a placeholder
                2. Extract EACH item mentioned with its individual price
                3. If no individual prices are given, items might be priced as shown in header
                4. Common abbreviations: ETB = Elite Trainer Box, BB = Booster Box, FA = Full Art, AA = Alternate Art
                5. If text seems jumbled or repeated, try to extract the most logical interpretation
                
                CRITICAL FIELD REQUIREMENTS - NEVER RETURN NULL OR UNDEFINED:
                - itemName: MUST have a value, at minimum "Pokemon Product" if unclear
                - set: Empty string "" if unknown, never null
                - productType: MUST be one of the listed values, use "OTHER" if uncertain
                - price: Use 0.00 if no price found, never null or empty
                - quantity: MUST be an integer (default to 1), never decimal or string
                - priceUnit: Use "each" as default if unclear
                - notes: Empty string "" if no notes, never null
                - mainListingPrice: Empty string "" if not found
                - location: Empty string "" if not mentioned
                - hasMultipleItems: Must be boolean true or false, never null
                - extractedDescription: Empty string "" if nothing found
            
                IMPORTANT: Focus on extracting actionable sales information. Ignore Facebook UI elements and focus on what the seller is actually selling and for how much.
        """;

    private static final String INVENTORY_SYSTEM_PROMPT = """
        You are analyzing a store website screenshot for Pokemon TCG products.
        Identify all visible Pokemon TCG products and extract:
        {
          "products": [
            {
              "name": "Product name",
              "set": "Set name if visible",
              "price": 0.00,
              "stockStatus": "In Stock|Out of Stock|Limited Stock|Preorder",
              "availability": "Online|In-Store|Both"
            }
          ]
        }
        Focus only on Pokemon TCG products. Be accurate with stock status.
        """;

    public Map<String, Object> analyzeMarketplaceListing(String description, String base64Image) {
        return analyzeMarketplaceListing(description, base64Image, null);
    }

    public Map<String, Object> analyzeMarketplaceListing(String description, String base64Image, String modelOverride) {
        try {
            LMStudioRequest request = buildVisionRequest(MARKETPLACE_SYSTEM_PROMPT, description, base64Image);
            if (modelOverride != null && !modelOverride.isEmpty()) {
                log.info("Using model override: {} (replacing default: {})", modelOverride, model);
                request.setModel(modelOverride);
            } else {
                log.info("Using default model: {}", model);
            }

            String jsonResponse = callLMStudio(request);
            return parseJsonResponse(jsonResponse);

        } catch (Exception e) {
            log.error("Failed to analyze marketplace listing", e);
            return createErrorResponse("Failed to analyze listing: " + e.getMessage());
        }
    }

    public Map<String, Object> analyzeStoreInventory(String storeName, String base64Image) {
        try {
            String prompt = "Analyze this screenshot from " + storeName + " for Pokemon TCG products:";
            LMStudioRequest request = buildVisionRequest(INVENTORY_SYSTEM_PROMPT, prompt, base64Image);

            String jsonResponse = callLMStudio(request);
            return parseJsonResponse(jsonResponse);

        } catch (Exception e) {
            log.error("Failed to analyze store inventory for {}", storeName, e);
            return createErrorResponse("Failed to analyze inventory: " + e.getMessage());
        }
    }

    public String testConnection() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity("/v1/models", String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                return "LM Studio connected successfully. Available models: " + response.getBody();
            }
            return "LM Studio connection failed: " + response.getStatusCode();
        } catch (Exception e) {
            return "LM Studio connection error: " + e.getMessage();
        }
    }

    public List<Map<String, Object>> getAvailableModels() {
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity("/v1/models", Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                if (body.containsKey("data") && body.get("data") instanceof List) {
                    return (List<Map<String, Object>>) body.get("data");
                }
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to fetch available models from LM Studio", e);
            return new ArrayList<>();
        }
    }

    private LMStudioRequest buildVisionRequest(String systemPrompt, String userText, String base64Image) {
        LMStudioRequest request = new LMStudioRequest();
        request.setModel(model);
        log.debug("Building request with default model: {}", model);

        List<LMStudioRequest.Message> messages = new ArrayList<>();

        // System message
        LMStudioRequest.Message systemMessage = new LMStudioRequest.Message();
        systemMessage.setRole("system");
        systemMessage.setContent(systemPrompt);
        messages.add(systemMessage);

        // User message with text and image
        LMStudioRequest.Message userMessage = new LMStudioRequest.Message();
        userMessage.setRole("user");

        List<LMStudioRequest.ContentPart> content = new ArrayList<>();

        // Text part
        LMStudioRequest.ContentPart textPart = new LMStudioRequest.ContentPart();
        textPart.setType("text");
        textPart.setText(userText);
        content.add(textPart);

        // Image part
        if (base64Image != null && !base64Image.isEmpty()) {
            LMStudioRequest.ContentPart imagePart = new LMStudioRequest.ContentPart();
            imagePart.setType("image_url");
            LMStudioRequest.ImageUrl imageUrl = new LMStudioRequest.ImageUrl();
            imageUrl.setUrl(base64Image.startsWith("data:") ? base64Image : "data:image/jpeg;base64," + base64Image);
            imagePart.setImageUrl(imageUrl);
            content.add(imagePart);
        }

        userMessage.setContent(content);
        messages.add(userMessage);

        request.setMessages(messages);
        return request;
    }

    private String callLMStudio(LMStudioRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<LMStudioRequest> entity = new HttpEntity<>(request, headers);

        log.info("Calling LM Studio with model: {}", request.getModel());
        
        try {
            // Log only model and message count (not full base64 content)
            log.info("Sending request to LM Studio with {} messages", request.getMessages().size());
            
            // First try to get raw response to debug
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    "/v1/chat/completions",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            log.debug("Raw LM Studio response: {}", rawResponse.getBody());

            // Now parse it
            LMStudioResponse response = objectMapper.readValue(rawResponse.getBody(), LMStudioResponse.class);

            if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
                return response.getChoices().get(0).getMessage().getContent();
            }

            throw new RuntimeException("No valid response from LM Studio");

        } catch (Exception e) {
            log.error("Failed to call LM Studio", e);
            throw new RuntimeException("Failed to call LM Studio: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseJsonResponse(String response) {
        try {
            // Extract JSON from the response (LLMs sometimes add extra text)
            int startIndex = response.indexOf("{");
            int endIndex = response.lastIndexOf("}") + 1;

            if (startIndex >= 0 && endIndex > startIndex) {
                String jsonStr = response.substring(startIndex, endIndex);
                return objectMapper.readValue(jsonStr, Map.class);
            }

            log.warn("No JSON found in response: {}", response);
            return createErrorResponse("Invalid response format");

        } catch (Exception e) {
            log.error("Failed to parse JSON response: {}", response, e);
            return createErrorResponse("Failed to parse response");
        }
    }

    private Map<String, Object> createErrorResponse(String error) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", error);
        errorMap.put("timestamp", new Date());
        return errorMap;
    }
}