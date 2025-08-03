package com.pokemon.tcgtracker.controller;

import com.pokemon.tcgtracker.service.ConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigurationController {
    
    private final ConfigurationService configurationService;
    
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentConfig() {
        Map<String, Object> response = new HashMap<>();
        response.putAll(configurationService.getCurrentConfiguration());
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/model")
    public ResponseEntity<Map<String, Object>> updateModel(@RequestBody Map<String, String> request) {
        String model = request.get("model");
        if (model != null && !model.trim().isEmpty()) {
            configurationService.updateLmStudioModel(model);
            log.info("Updated LM Studio model via ConfigurationController to: {}", model);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Model updated");
        response.put("currentModel", configurationService.getActiveLmStudioModel());
        return ResponseEntity.ok(response);
    }
}