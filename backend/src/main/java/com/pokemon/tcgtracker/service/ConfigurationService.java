package com.pokemon.tcgtracker.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Getter
@Setter
public class ConfigurationService {
    
    @Value("${lmstudio.model:qwen/qwen2.5-vl-7b}")
    private String defaultLmStudioModel;
    
    // Runtime configuration that can be updated
    private String activeLmStudioModel;
    
    @PostConstruct
    public void init() {
        // Initialize with default
        this.activeLmStudioModel = defaultLmStudioModel;
        log.info("Initialized LM Studio model to: {}", activeLmStudioModel);
    }
    
    /**
     * Get the current active LM Studio model
     */
    public String getActiveLmStudioModel() {
        return activeLmStudioModel;
    }
    
    /**
     * Update the active LM Studio model
     */
    public void updateLmStudioModel(String model) {
        if (model != null && !model.trim().isEmpty()) {
            this.activeLmStudioModel = model.trim();
            log.info("Updated active LM Studio model to: {}", activeLmStudioModel);
        }
    }
    
    /**
     * Get the current configuration as a map
     */
    public Map<String, Object> getCurrentConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("lmStudioModel", activeLmStudioModel);
        config.put("defaultLmStudioModel", defaultLmStudioModel);
        return config;
    }
}