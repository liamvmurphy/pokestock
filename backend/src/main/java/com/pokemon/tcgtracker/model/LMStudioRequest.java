package com.pokemon.tcgtracker.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class LMStudioRequest {
    private String model;
    private List<Message> messages;
    private Double temperature = 0.7;
    @JsonProperty("max_tokens")
    private Integer maxTokens = 1000;

    @Data
    public static class Message {
        private String role;
        private Object content; // Can be String or List<ContentPart>
    }

    @Data
    public static class ContentPart {
        private String type; // "text" or "image_url"
        private String text;
        @JsonProperty("image_url")
        private ImageUrl imageUrl;
    }

    @Data
    public static class ImageUrl {
        private String url; // Base64 encoded image with data:image/jpeg;base64, prefix
    }
}