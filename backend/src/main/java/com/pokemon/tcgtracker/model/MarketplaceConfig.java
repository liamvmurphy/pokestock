package com.pokemon.tcgtracker.model;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "marketplace")
public class MarketplaceConfig {
    
    private Monitoring monitoring = new Monitoring();
    private List<String> searchTerms = List.of(
        "pokemon etb",
        "pokemon elite trainer box",
        "pokemon booster box"
    );
    
    @Data
    public static class Monitoring {
        private boolean enabled = false;
        private long interval = 1800000; // 30 minutes
        private int maxListingsPerSearch = 15;
        private int delayBetweenSearches = 5000; // 5 seconds
        private boolean useExistingBrowser = true;
    }
}