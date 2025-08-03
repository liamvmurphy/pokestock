package com.pokemon.tcgtracker.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "marketplace.monitoring.enabled", havingValue = "true", matchIfMissing = false)
public class ScheduledMarketplaceService {

    private final FacebookMarketplaceService facebookMarketplaceService;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicReference<LocalDateTime> lastRun = new AtomicReference<>();
    private final AtomicReference<String> lastStatus = new AtomicReference<>("Not started");

    /**
     * Run marketplace monitoring every 30 minutes
     * Can be configured with: marketplace.monitoring.cron
     */
    @Scheduled(fixedRateString = "${marketplace.monitoring.interval:1800000}") // 30 minutes default
    public void runScheduledMonitoring() {
        if (isRunning.compareAndSet(false, true)) {
            try {
                log.info("Starting scheduled marketplace monitoring");
                lastStatus.set("Running");
                
                facebookMarketplaceService.startMarketplaceMonitoring();
                
                lastRun.set(LocalDateTime.now());
                lastStatus.set("Completed successfully");
                log.info("Scheduled marketplace monitoring completed");
                
            } catch (Exception e) {
                log.error("Scheduled marketplace monitoring failed", e);
                lastStatus.set("Failed: " + e.getMessage());
            } finally {
                isRunning.set(false);
            }
        } else {
            log.info("Marketplace monitoring already running, skipping scheduled execution");
        }
    }

    /**
     * Health check - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void healthCheck() {
        try {
            log.debug("Marketplace monitoring health check - Running: {}, Last run: {}, Status: {}", 
                    isRunning.get(), lastRun.get(), lastStatus.get());
        } catch (Exception e) {
            log.warn("Health check failed", e);
        }
    }

    public boolean isCurrentlyRunning() {
        return isRunning.get();
    }

    public LocalDateTime getLastRun() {
        return lastRun.get();
    }

    public String getLastStatus() {
        return lastStatus.get();
    }

    public void forceStop() {
        isRunning.set(false);
        lastStatus.set("Manually stopped");
        log.info("Marketplace monitoring force stopped");
    }
}