package com.pokemon.tcgtracker.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class GoogleDriveService {

    private Drive driveService;
    
    @Value("${google.drive.credentials.path:./credentials.json}")
    private String credentialsPath;
    
    @Value("${google.drive.folder.name:Pokemon TCG Market Intelligence}")
    private String folderName;
    
    private String folderId;
    
    private static final String APPLICATION_NAME = "Pokemon TCG Tracker";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    @PostConstruct
    public void initializeDriveService() {
        try {
            log.info("Initializing Google Drive service...");
            
            // Create credentials
            GoogleCredential credential = GoogleCredential.fromStream(
                new FileInputStream(credentialsPath))
                .createScoped(SCOPES);

            // Build Drive service
            driveService = new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                JSON_FACTORY,
                credential)
                .setApplicationName(APPLICATION_NAME)
                .build();

            // Ensure folder exists
            ensureFolderExists();
            
            log.info("Google Drive service initialized successfully");
            log.info("Reports will be saved to folder: {}", folderName);
            
        } catch (Exception e) {
            log.error("Failed to initialize Google Drive service: {}", e.getMessage());
            log.warn("Market intelligence reports will not be saved to Google Drive");
        }
    }

    /**
     * Save market intelligence report to Google Drive
     */
    public String saveMarketIntelligenceReport(String reportContent, int totalListings) {
        if (driveService == null) {
            log.warn("Google Drive service not available - cannot save report");
            return null;
        }

        try {
            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
            String fileName = String.format("Pokemon_TCG_Market_Intelligence_%s_%d_listings.md", 
                                          timestamp, totalListings);

            // Create file metadata
            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setParents(Collections.singletonList(folderId));
            fileMetadata.setDescription(String.format(
                "Pokemon TCG Market Intelligence Report generated on %s analyzing %d marketplace listings", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 
                totalListings));

            // Create file content
            ByteArrayContent mediaContent = new ByteArrayContent("text/markdown", 
                                                                reportContent.getBytes("UTF-8"));

            // Upload file
            File uploadedFile = driveService.files()
                .create(fileMetadata, mediaContent)
                .setFields("id,name,createdTime,size,webViewLink")
                .execute();

            log.info("Successfully saved market intelligence report to Google Drive");
            log.info("File ID: {}", uploadedFile.getId());
            log.info("File Name: {}", uploadedFile.getName());
            log.info("View Link: {}", uploadedFile.getWebViewLink());

            return uploadedFile.getId();
            
        } catch (Exception e) {
            log.error("Failed to save market intelligence report to Google Drive: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get the most recent market intelligence report
     */
    public Map<String, Object> getLatestMarketIntelligenceReport() {
        if (driveService == null) {
            log.warn("Google Drive service not available - cannot retrieve report");
            return null;
        }

        try {
            // Search for the most recent report
            String query = String.format("'%s' in parents and name contains 'Pokemon_TCG_Market_Intelligence' and mimeType='text/markdown'", folderId);
            
            FileList result = driveService.files().list()
                .setQ(query)
                .setOrderBy("createdTime desc")
                .setPageSize(1)
                .setFields("files(id,name,createdTime,size,webViewLink,description)")
                .execute();

            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                log.info("No previous market intelligence reports found in Google Drive");
                return null;
            }

            File latestReport = files.get(0);
            
            // Download the file content
            String content = driveService.files()
                .get(latestReport.getId())
                .executeMediaAsInputStream()
                .readAllBytes()
                .toString();

            // Actually read the content properly
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            driveService.files()
                .get(latestReport.getId())
                .executeMediaAndDownloadTo(out);
            content = out.toString("UTF-8");

            Map<String, Object> reportData = new HashMap<>();
            reportData.put("fileId", latestReport.getId());
            reportData.put("fileName", latestReport.getName());
            reportData.put("content", content);
            reportData.put("createdTime", latestReport.getCreatedTime());
            reportData.put("size", latestReport.getSize());
            reportData.put("webViewLink", latestReport.getWebViewLink());
            reportData.put("description", latestReport.getDescription());

            log.info("Retrieved latest market intelligence report from Google Drive");
            log.info("Report: {} (Created: {})", latestReport.getName(), latestReport.getCreatedTime());

            return reportData;
            
        } catch (Exception e) {
            log.error("Failed to retrieve latest market intelligence report from Google Drive: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get all market intelligence reports (for history/comparison)
     */
    public List<Map<String, Object>> getAllMarketIntelligenceReports(int maxResults) {
        if (driveService == null) {
            return Collections.emptyList();
        }

        try {
            String query = String.format("'%s' in parents and name contains 'Pokemon_TCG_Market_Intelligence'", folderId);
            
            FileList result = driveService.files().list()
                .setQ(query)
                .setOrderBy("createdTime desc")
                .setPageSize(maxResults)
                .setFields("files(id,name,createdTime,size,webViewLink,description)")
                .execute();

            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> reports = new ArrayList<>();
            for (File file : files) {
                Map<String, Object> reportInfo = new HashMap<>();
                reportInfo.put("fileId", file.getId());
                reportInfo.put("fileName", file.getName());
                reportInfo.put("createdTime", file.getCreatedTime());
                reportInfo.put("size", file.getSize());
                reportInfo.put("webViewLink", file.getWebViewLink());
                reportInfo.put("description", file.getDescription());
                reports.add(reportInfo);
            }

            log.info("Retrieved {} market intelligence reports from Google Drive", reports.size());
            return reports;
            
        } catch (Exception e) {
            log.error("Failed to retrieve market intelligence reports from Google Drive: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Ensure the reports folder exists in Google Drive
     */
    private void ensureFolderExists() throws IOException {
        // Search for existing folder
        String query = String.format("name='%s' and mimeType='application/vnd.google-apps.folder'", folderName);
        FileList result = driveService.files().list()
            .setQ(query)
            .setFields("files(id,name)")
            .execute();

        List<File> folders = result.getFiles();
        if (folders != null && !folders.isEmpty()) {
            folderId = folders.get(0).getId();
            log.info("Found existing folder: {} (ID: {})", folderName, folderId);
        } else {
            // Create new folder
            File folderMetadata = new File();
            folderMetadata.setName(folderName);
            folderMetadata.setMimeType("application/vnd.google-apps.folder");
            folderMetadata.setDescription("Automated storage for Pokemon TCG Market Intelligence Reports generated by the Pokemon TCG Tracker application");

            File folder = driveService.files()
                .create(folderMetadata)
                .setFields("id,name")
                .execute();

            folderId = folder.getId();
            log.info("Created new folder: {} (ID: {})", folderName, folderId);
        }
    }

    /**
     * Get folder information and sharing details
     */
    public Map<String, Object> getFolderInfo() {
        if (driveService == null || folderId == null) {
            return null;
        }

        try {
            File folder = driveService.files()
                .get(folderId)
                .setFields("id,name,webViewLink,createdTime,description")
                .execute();

            Map<String, Object> folderInfo = new HashMap<>();
            folderInfo.put("folderId", folder.getId());
            folderInfo.put("folderName", folder.getName());
            folderInfo.put("webViewLink", folder.getWebViewLink());
            folderInfo.put("createdTime", folder.getCreatedTime());
            folderInfo.put("description", folder.getDescription());

            return folderInfo;
            
        } catch (Exception e) {
            log.error("Failed to get folder info: {}", e.getMessage());
            return null;
        }
    }

    public boolean isServiceAvailable() {
        return driveService != null && folderId != null;
    }
}