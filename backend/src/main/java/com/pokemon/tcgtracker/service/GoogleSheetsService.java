package com.pokemon.tcgtracker.service;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleSheetsService {

    private final Sheets sheetsService;
    
    @Value("${google.sheets.spreadsheet.id:}")
    private String spreadsheetId;

    private static final String MARKETPLACE_SHEET = "Facebook Marketplace";
    private static final String INVENTORY_SHEET = "Store Inventory";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PostConstruct
    public void initializeSheets() {
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.warn("No spreadsheet ID configured. Please set google.sheets.spreadsheet.id property.");
            return;
        }
        
        try {
            ensureSheetExists(MARKETPLACE_SHEET);
            ensureSheetExists(INVENTORY_SHEET);
            initializeHeaders();
            log.info("Google Sheets initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize Google Sheets", e);
        }
    }

    private void ensureSheetExists(String sheetName) throws IOException {
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        
        boolean sheetExists = spreadsheet.getSheets().stream()
                .anyMatch(sheet -> sheet.getProperties().getTitle().equals(sheetName));
        
        if (!sheetExists) {
            log.info("Creating sheet: {}", sheetName);
            
            Request addSheetRequest = new Request()
                    .setAddSheet(new AddSheetRequest()
                            .setProperties(new SheetProperties()
                                    .setTitle(sheetName)));
            
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(Collections.singletonList(addSheetRequest));
            
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        }
    }

    private void initializeHeaders() throws IOException {
        // Initialize Facebook Marketplace headers - simplified
        List<Object> marketplaceHeaders = Arrays.asList(
                "Date Found", "Item Name", "Set", "Product Type", 
                "Price", "Quantity", "Price Unit", "Language",
                "Main Listing Price", "Location",
                "Has Multiple Items", "Marketplace URL", "Notes"
        );
        updateRow(MARKETPLACE_SHEET, 1, marketplaceHeaders);

        // Initialize Store Inventory headers
        List<Object> inventoryHeaders = Arrays.asList(
                "Last Checked", "Store", "Product Name", "Set",
                "Price", "Stock Status", "Availability", "URL",
                "Price Change", "Notes"
        );
        updateRow(INVENTORY_SHEET, 1, inventoryHeaders);
    }

    /**
     * Add multiple marketplace listings that share the same URL (for multi-item listings)
     * This method will delete all existing rows with the URL and add the new items
     */
    public void addMarketplaceListings(List<Map<String, Object>> listings) throws IOException {
        if (listings == null || listings.isEmpty()) {
            return;
        }
        
        // Get the URL from the first listing (all should have the same URL)
        String url = (String) listings.get(0).getOrDefault("url", "");
        if (url.isEmpty()) {
            log.warn("No URL found in listings, skipping batch add");
            return;
        }
        
        // Delete all existing rows with this URL
        int deletedRows = deleteRowsByUrl(MARKETPLACE_SHEET, url);
        if (deletedRows > 0) {
            log.info("Deleted {} existing rows for URL: {}", deletedRows, url);
        }
        
        // Prepare all rows to add
        List<List<Object>> rows = new ArrayList<>();
        for (Map<String, Object> listing : listings) {
            // Ensure quantity is an integer
            Object quantityObj = listing.getOrDefault("quantity", 1);
            int quantity = 1; // default
            if (quantityObj instanceof Number) {
                quantity = ((Number) quantityObj).intValue();
            } else if (quantityObj instanceof String) {
                try {
                    quantity = Integer.parseInt((String) quantityObj);
                } catch (NumberFormatException e) {
                    quantity = 1; // fallback to 1 if parsing fails
                }
            }
            
            // Ensure productType is never empty - default to "OTHER"
            String productType = (String) listing.getOrDefault("productType", "");
            if (productType == null || productType.trim().isEmpty()) {
                productType = "OTHER";
            }
            
            // Ensure all fields have safe values (never null)
            String itemName = listing.getOrDefault("itemName", "Unknown Item").toString();
            String set = listing.getOrDefault("set", "").toString();
            Object priceObj = listing.getOrDefault("price", "0.00");
            String price = priceObj != null ? priceObj.toString() : "0.00";
            String priceUnit = listing.getOrDefault("priceUnit", "each").toString();
            String mainListingPrice = listing.getOrDefault("mainListingPrice", "").toString();
            String location = listing.getOrDefault("location", "").toString();
            Boolean hasMultipleItems = (Boolean) listing.getOrDefault("hasMultipleItems", false);
            String notes = listing.getOrDefault("notes", "").toString();
            
            List<Object> row = Arrays.asList(
                    LocalDateTime.now().format(DATE_FORMATTER),
                    itemName.isEmpty() ? "Unknown Item" : itemName,
                    set,
                    productType, // Always has a value, defaults to "OTHER"
                    price.isEmpty() ? "0.00" : price,
                    quantity, // Always an integer
                    priceUnit.isEmpty() ? "each" : priceUnit,
                    listing.getOrDefault("language", "English").toString(),
                    mainListingPrice,
                    location,
                    hasMultipleItems != null ? hasMultipleItems : false,
                    url,
                    notes
            );
            rows.add(row);
        }
        
        // Add all rows at once
        if (!rows.isEmpty()) {
            appendRows(MARKETPLACE_SHEET, rows);
            log.info("Added {} new marketplace listings for URL: {}", rows.size(), url);
        }
    }
    
    public void addMarketplaceListing(Map<String, Object> listing) throws IOException {
        log.info("Processing listing for Google Sheets: {}", listing.getOrDefault("itemName", listing.getOrDefault("title", "Unknown")));
        log.debug("Full listing data: {}", listing);
        
        String url = (String) listing.getOrDefault("url", "");
        if (url.isEmpty()) {
            log.warn("Skipping listing without URL: {}. Available keys: {}", 
                listing.getOrDefault("itemName", listing.getOrDefault("title", "Unknown")), 
                listing.keySet());
            return;
        }
        
        // Check if URL already exists and if it needs refreshing (> 7 days old)
        int existingRowIndex = findRowByUrl(MARKETPLACE_SHEET, url);
        if (existingRowIndex > 0) {
            LocalDateTime lastUpdate = getLastUpdateDate(MARKETPLACE_SHEET, existingRowIndex);
            if (lastUpdate != null && ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now()) < 7) {
                log.info("Skipping URL (last updated {} days ago): {}", 
                    ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now()), url);
                return;
            }
            log.info("URL found but is {} days old, refreshing: {}", 
                lastUpdate != null ? ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now()) : "unknown", url);
        }

        // Ensure quantity is an integer
        Object quantityObj = listing.getOrDefault("quantity", 1);
        int quantity = 1; // default
        if (quantityObj instanceof Number) {
            quantity = ((Number) quantityObj).intValue();
        } else if (quantityObj instanceof String) {
            try {
                quantity = Integer.parseInt((String) quantityObj);
            } catch (NumberFormatException e) {
                quantity = 1; // fallback to 1 if parsing fails
            }
        }
        
        // Ensure productType is never empty - default to "OTHER"
        String productType = (String) listing.getOrDefault("productType", "");
        if (productType == null || productType.trim().isEmpty()) {
            productType = "OTHER";
        }
        
        // Ensure all fields have safe values (never null)
        String itemName = listing.getOrDefault("itemName", "Unknown Item").toString();
        String set = listing.getOrDefault("set", "").toString();
        Object priceObj = listing.getOrDefault("price", "0.00");
        String price = priceObj != null ? priceObj.toString() : "0.00";
        String priceUnit = listing.getOrDefault("priceUnit", "each").toString();
        String mainListingPrice = listing.getOrDefault("mainListingPrice", "").toString();
        String location = listing.getOrDefault("location", "").toString();
        Boolean hasMultipleItems = (Boolean) listing.getOrDefault("hasMultipleItems", false);
        String notes = listing.getOrDefault("notes", "").toString();
        
        List<Object> row = Arrays.asList(
                LocalDateTime.now().format(DATE_FORMATTER),
                itemName.isEmpty() ? "Unknown Item" : itemName,
                set,
                productType, // Always has a value, defaults to "OTHER"
                price.isEmpty() ? "0.00" : price,
                quantity, // Always an integer
                priceUnit.isEmpty() ? "each" : priceUnit,
                listing.getOrDefault("language", "English").toString(),
                mainListingPrice,
                location,
                hasMultipleItems != null ? hasMultipleItems : false,
                url,
                notes
        );
        
        // Update existing row or append new row (existingRowIndex already determined above)
        if (existingRowIndex > 0) {
            // Update existing row (but keep original date found, just update the timestamp in notes)
            List<Object> existingRow = getRow(MARKETPLACE_SHEET, existingRowIndex);
            List<Object> updatedRow = Arrays.asList(
                    existingRow.get(0), // Keep original "Date Found"
                    itemName.isEmpty() ? "Unknown Item" : itemName,
                    set,
                    productType, // Use the processed productType (defaults to "OTHER")
                    price.isEmpty() ? "0.00" : price,
                    quantity, // Use the processed integer quantity
                    priceUnit.isEmpty() ? "each" : priceUnit,
                    listing.getOrDefault("language", "English").toString(),
                    mainListingPrice,
                    location,
                    hasMultipleItems != null ? hasMultipleItems : false,
                    url,
                    "Last updated: " + LocalDateTime.now().format(DATE_FORMATTER) + ". " + notes
            );
            updateRow(MARKETPLACE_SHEET, existingRowIndex, updatedRow);
            log.info("Updated existing marketplace listing: {}", listing.get("itemName"));
        } else {
            appendRow(MARKETPLACE_SHEET, row);
            log.info("Added new marketplace listing: {}", listing.get("itemName"));
        }
    }

    public void updateInventory(String storeName, List<Map<String, Object>> products) throws IOException {
        List<List<Object>> rows = new ArrayList<>();
        
        for (Map<String, Object> product : products) {
            List<Object> row = Arrays.asList(
                    LocalDateTime.now().format(DATE_FORMATTER),
                    storeName,
                    product.getOrDefault("name", ""),
                    product.getOrDefault("set", ""),
                    product.getOrDefault("price", ""),
                    product.getOrDefault("stockStatus", ""),
                    product.getOrDefault("availability", ""),
                    product.getOrDefault("url", ""),
                    product.getOrDefault("priceChange", ""),
                    product.getOrDefault("notes", "")
            );
            rows.add(row);
        }
        
        if (!rows.isEmpty()) {
            appendRows(INVENTORY_SHEET, rows);
            log.info("Updated inventory for store: {} ({} products)", storeName, rows.size());
        }
    }

    private void appendRow(String sheetName, List<Object> row) throws IOException {
        appendRows(sheetName, Collections.singletonList(row));
    }

    private void appendRows(String sheetName, List<List<Object>> rows) throws IOException {
        ValueRange body = new ValueRange().setValues(rows);
        
        sheetsService.spreadsheets().values()
                .append(spreadsheetId, sheetName + "!A:A", body)
                .setValueInputOption("USER_ENTERED")
                .setInsertDataOption("INSERT_ROWS")
                .execute();
    }

    private void updateRow(String sheetName, int rowNumber, List<Object> values) throws IOException {
        ValueRange body = new ValueRange()
                .setValues(Collections.singletonList(values));
        
        String range = String.format("%s!A%d:%s%d", 
                sheetName, rowNumber, 
                getColumnLetter(values.size()), rowNumber);
        
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, range, body)
                .setValueInputOption("USER_ENTERED")
                .execute();
    }
    

    private String getColumnLetter(int columnNumber) {
        StringBuilder column = new StringBuilder();
        while (columnNumber > 0) {
            int remainder = (columnNumber - 1) % 26;
            column.insert(0, (char) ('A' + remainder));
            columnNumber = (columnNumber - 1) / 26;
        }
        return column.toString();
    }

    /**
     * Find the row index (1-based) that contains the given URL in the Marketplace URL column
     * Returns -1 if not found
     */
    private int findRowByUrl(String sheetName, String url) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!K:K") // Column K is Marketplace URL (11th column)
                .execute();
        
        List<List<Object>> values = response.getValues();
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (!row.isEmpty() && url.equals(row.get(0).toString())) {
                    return i + 1; // Return 1-based row index
                }
            }
        }
        return -1; // Not found
    }
    
    /**
     * Find all row indices (1-based) that contain the given URL in the Marketplace URL column
     * Returns empty list if none found
     */
    private List<Integer> findAllRowsByUrl(String sheetName, String url) throws IOException {
        List<Integer> rowIndices = new ArrayList<>();
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!K:K") // Column K is Marketplace URL (11th column)
                .execute();
        
        List<List<Object>> values = response.getValues();
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (!row.isEmpty() && url.equals(row.get(0).toString())) {
                    rowIndices.add(i + 1); // Add 1-based row index
                }
            }
        }
        return rowIndices;
    }
    
    /**
     * Delete all rows that have the specified URL
     * Returns the number of rows deleted
     */
    private int deleteRowsByUrl(String sheetName, String url) throws IOException {
        List<Integer> rowsToDelete = findAllRowsByUrl(sheetName, url);
        
        if (rowsToDelete.isEmpty()) {
            return 0;
        }
        
        // Sort in descending order so we delete from bottom to top (to maintain row indices)
        Collections.sort(rowsToDelete, Collections.reverseOrder());
        
        log.info("Deleting {} existing rows for URL: {}", rowsToDelete.size(), url);
        
        // Get the sheet ID for batch delete
        Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        Integer sheetId = null;
        for (Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                sheetId = sheet.getProperties().getSheetId();
                break;
            }
        }
        
        if (sheetId == null) {
            log.error("Could not find sheet ID for: {}", sheetName);
            return 0;
        }
        
        // Create batch delete requests
        List<Request> requests = new ArrayList<>();
        for (Integer rowIndex : rowsToDelete) {
            // Skip header row
            if (rowIndex <= 1) continue;
            
            requests.add(new Request()
                    .setDeleteDimension(new DeleteDimensionRequest()
                            .setRange(new DimensionRange()
                                    .setSheetId(sheetId)
                                    .setDimension("ROWS")
                                    .setStartIndex(rowIndex - 1) // 0-based for API
                                    .setEndIndex(rowIndex)))); // exclusive end
        }
        
        if (!requests.isEmpty()) {
            BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest()
                    .setRequests(requests);
            sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
        }
        
        return rowsToDelete.size();
    }

    /**
     * Get a specific row from the sheet (1-based row index)
     */
    private List<Object> getRow(String sheetName, int rowIndex) throws IOException {
        ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!" + rowIndex + ":" + rowIndex)
                .execute();
        
        List<List<Object>> values = response.getValues();
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return new ArrayList<>(); // Return empty list if row not found
    }
    
    /**
     * Get the last update date from a row (column A - Date Found)
     * Returns null if date cannot be parsed
     */
    private LocalDateTime getLastUpdateDate(String sheetName, int rowIndex) throws IOException {
        List<Object> row = getRow(sheetName, rowIndex);
        if (row.isEmpty()) {
            return null;
        }
        
        try {
            String dateStr = row.get(0).toString();
            return LocalDateTime.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Could not parse date from row {}: {}", rowIndex, row.get(0));
            return null;
        }
    }
    
    /**
     * Check if a URL should be refreshed based on 7-day rule
     * Returns true if URL doesn't exist or was last updated more than 7 days ago
     */
    public boolean shouldRefreshUrl(String url) throws IOException {
        int existingRowIndex = findRowByUrl(MARKETPLACE_SHEET, url);
        if (existingRowIndex <= 0) {
            return true; // URL doesn't exist, should scrape
        }
        
        LocalDateTime lastUpdate = getLastUpdateDate(MARKETPLACE_SHEET, existingRowIndex);
        if (lastUpdate == null) {
            return true; // Can't determine last update, refresh to be safe
        }
        
        long daysSinceUpdate = ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now());
        return daysSinceUpdate >= 7;
    }

    /**
     * Fetch all marketplace listings from Google Sheets
     * Returns a list of maps containing all listing data
     */
    public List<Map<String, Object>> getAllMarketplaceListings() throws IOException {
        List<Map<String, Object>> listings = new ArrayList<>();
        
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.warn("No spreadsheet ID configured, returning empty listings");
            return listings;
        }
        
        try {
            // Fetch all data from the marketplace sheet
            String range = MARKETPLACE_SHEET + "!A:M"; // Columns A through M (all columns including Language)
            log.info("Fetching data from range: {}", range);
            
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            
            List<List<Object>> values = response.getValues();
            log.info("Retrieved {} rows from Google Sheets", values != null ? values.size() : 0);
            
            if (values == null || values.size() <= 1) {
                log.info("No data found or only headers present");
                return listings; // Return empty if no data or only headers
            }
        
            // Skip header row and process data
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                if (row.isEmpty()) continue;
                
                Map<String, Object> listing = new HashMap<>();
                
                // Map columns to fields (matching the header structure)
                listing.put("dateFound", row.size() > 0 ? row.get(0) : "");
                listing.put("itemName", row.size() > 1 ? row.get(1) : "");
                listing.put("set", row.size() > 2 ? row.get(2) : "");
                listing.put("productType", row.size() > 3 ? row.get(3) : "");
                listing.put("price", row.size() > 4 ? row.get(4) : "");
                listing.put("quantity", row.size() > 5 ? row.get(5) : "");
                listing.put("priceUnit", row.size() > 6 ? row.get(6) : "");
                listing.put("language", row.size() > 7 ? row.get(7) : "English");
                listing.put("mainListingPrice", row.size() > 8 ? row.get(8) : "");
                listing.put("location", row.size() > 9 ? row.get(9) : "");
                listing.put("hasMultipleItems", row.size() > 10 ? row.get(10) : false);
                listing.put("marketplaceUrl", row.size() > 11 ? row.get(11) : "");
                listing.put("notes", row.size() > 12 ? row.get(12) : "");
                
                // Add computed fields for UI
                listing.put("id", "item_" + i); // Generate unique ID
                listing.put("source", "Facebook Marketplace");
                listing.put("available", true); // Can be computed based on business logic
                
                listings.add(listing);
            }
        
            log.info("Fetched {} marketplace listings from Google Sheets", listings.size());
            return listings;
            
        } catch (IOException e) {
            log.error("Error fetching marketplace listings from Google Sheets: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    public String getSpreadsheetUrl() {
        return String.format("https://docs.google.com/spreadsheets/d/%s/edit", spreadsheetId);
    }
    
    /**
     * Save market intelligence report as a new sheet tab
     */
    public String saveMarketIntelligenceReport(String reportContent, int totalListings) {
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.warn("No spreadsheet ID configured for saving market intelligence report");
            return null;
        }
        
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String sheetName = String.format("Market_Report_%s_%d_listings", timestamp, totalListings);
            
            log.info("Saving market intelligence report as sheet: {}", sheetName);
            
            // Create new sheet
            createNewSheet(sheetName);
            
            // Convert markdown report to rows for Google Sheets
            List<List<Object>> rows = convertMarkdownToSheetRows(reportContent, totalListings);
            
            // Write the report data to the sheet
            ValueRange body = new ValueRange()
                .setValues(rows);
            
            sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", body)
                .setValueInputOption("RAW")
                .execute();
            
            log.info("Successfully saved market intelligence report to sheet: {}", sheetName);
            return sheetName;
            
        } catch (IOException e) {
            log.error("Failed to save market intelligence report to Google Sheets: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Get the most recent market intelligence report from Google Sheets
     */
    public Map<String, Object> getLatestMarketIntelligenceReport() {
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.warn("No spreadsheet ID configured for retrieving market intelligence report");
            return null;
        }
        
        try {
            // Get all sheets in the spreadsheet
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheets = spreadsheet.getSheets();
            
            // Find all market report sheets and get the most recent one
            Sheet latestReportSheet = null;
            LocalDateTime latestDate = null;
            
            for (Sheet sheet : sheets) {
                String sheetName = sheet.getProperties().getTitle();
                if (sheetName.startsWith("Market_Report_")) {
                    try {
                        // Extract date from sheet name: Market_Report_2025-08-05_14-30-25_15_listings
                        String dateTimeStr = sheetName.substring("Market_Report_".length());
                        dateTimeStr = dateTimeStr.substring(0, dateTimeStr.indexOf("_", dateTimeStr.indexOf("_") + 1));
                        
                        LocalDateTime sheetDate = LocalDateTime.parse(dateTimeStr, DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                        
                        if (latestDate == null || sheetDate.isAfter(latestDate)) {
                            latestDate = sheetDate;
                            latestReportSheet = sheet;
                        }
                    } catch (Exception e) {
                        log.debug("Could not parse date from sheet name: {}", sheetName);
                    }
                }
            }
            
            if (latestReportSheet == null) {
                log.info("No market intelligence report sheets found");
                return null;
            }
            
            String sheetName = latestReportSheet.getProperties().getTitle();
            log.info("Found latest market intelligence report sheet: {}", sheetName);
            
            // Read the content from the sheet
            ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A:B")
                .execute();
            
            List<List<Object>> values = response.getValues();
            if (values == null || values.isEmpty()) {
                log.warn("Market intelligence report sheet is empty: {}", sheetName);
                return null;
            }
            
            // Convert sheet rows back to markdown
            String reportContent = convertSheetRowsToMarkdown(values);
            
            Map<String, Object> reportData = new HashMap<>();
            reportData.put("sheetName", sheetName);
            reportData.put("content", reportContent);
            reportData.put("createdTime", latestDate.format(DATE_FORMATTER));
            reportData.put("spreadsheetUrl", getSpreadsheetUrl() + "#gid=" + latestReportSheet.getProperties().getSheetId());
            
            log.info("Successfully retrieved market intelligence report from sheet: {}", sheetName);
            return reportData;
            
        } catch (IOException e) {
            log.error("Failed to retrieve latest market intelligence report from Google Sheets: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Save eBay price data to a new sheet tab
     */
    public String saveEbayPriceData(List<Map<String, Object>> searchResults) throws IOException {
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.error("No spreadsheet ID configured");
            throw new IllegalStateException("Google Sheets not configured");
        }
        
        try {
            // Delete existing eBay price sheets for fresh search
            deleteEbayPriceSheets();
            
            // Create a timestamp for the sheet name
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            String sheetName = String.format("eBay_Prices_%s", timestamp);
            
            log.info("Creating new eBay price sheet: {}", sheetName);
            
            // Create the new sheet
            createNewSheet(sheetName);
            
            // Prepare the data to write
            List<List<Object>> rows = new ArrayList<>();
            
            // Add headers - includes listing details and price data
            rows.add(Arrays.asList(
                "Search Name",
                "Original Name",
                "Language",
                "Set",
                "Product Type",
                "Facebook Price",
                "eBay Median",
                "Result Count",
                "Listing Details",
                "All Prices",
                "Top 5 Prices",
                "Search Time"
            ));
            
            // Add data rows
            for (Map<String, Object> result : searchResults) {
                List<Object> row = new ArrayList<>();
                row.add(result.getOrDefault("searchName", ""));
                row.add(result.getOrDefault("originalName", ""));
                row.add(result.getOrDefault("language", "English"));
                row.add(result.getOrDefault("set", ""));
                row.add(result.getOrDefault("productType", ""));
                row.add(result.getOrDefault("facebookPrice", ""));
                row.add(result.getOrDefault("medianPrice", ""));
                row.add(result.getOrDefault("resultCount", "0"));
                
                // Convert listing details list to string (markdown links)
                Object listingDetailsObj = result.get("listingDetails");
                String listingDetailsStr = "";
                if (listingDetailsObj instanceof List) {
                    listingDetailsStr = ((List<?>) listingDetailsObj).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(" | "));
                }
                row.add(listingDetailsStr);
                
                // Convert price list to string for graph data
                Object allPricesObj = result.get("allPrices");
                String allPricesStr = "";
                if (allPricesObj instanceof List) {
                    allPricesStr = ((List<?>) allPricesObj).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                }
                row.add(allPricesStr);
                
                // Convert top 5 prices to string for card graph
                Object top5PricesObj = result.get("top5Prices");
                String top5PricesStr = "";
                if (top5PricesObj instanceof List) {
                    top5PricesStr = ((List<?>) top5PricesObj).stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                }
                row.add(top5PricesStr);
                
                row.add(timestamp);
                rows.add(row);
            }
            
            // Write the data
            ValueRange body = new ValueRange().setValues(rows);
            UpdateValuesResponse updateResult = sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A1", body)
                .setValueInputOption("RAW")
                .execute();
            
            log.info("Successfully saved {} eBay price results to sheet: {}", searchResults.size(), sheetName);
            return sheetName;
            
        } catch (IOException e) {
            log.error("Failed to save eBay price data to Google Sheets: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get eBay price data from the most recent sheet
     */
    public List<Map<String, String>> getEbayPriceData() throws IOException {
        List<Map<String, String>> data = new ArrayList<>();
        
        if (spreadsheetId == null || spreadsheetId.isEmpty()) {
            log.warn("No spreadsheet ID configured");
            return data;
        }
        
        try {
            // Get all sheets
            Spreadsheet spreadsheet = sheetsService.spreadsheets()
                .get(spreadsheetId)
                .execute();
            
            // Find eBay price sheets
            List<Sheet> sheets = spreadsheet.getSheets();
            Sheet latestSheet = null;
            LocalDateTime latestDate = null;
            
            for (Sheet sheet : sheets) {
                String sheetName = sheet.getProperties().getTitle();
                if (sheetName.startsWith("eBay_Prices_")) {
                    try {
                        String dateStr = sheetName.substring("eBay_Prices_".length());
                        LocalDateTime sheetDate = LocalDateTime.parse(dateStr, DATE_FORMATTER);
                        
                        if (latestDate == null || sheetDate.isAfter(latestDate)) {
                            latestDate = sheetDate;
                            latestSheet = sheet;
                        }
                    } catch (Exception e) {
                        log.debug("Could not parse date from sheet name: {}", sheetName);
                    }
                }
            }
            
            if (latestSheet == null) {
                log.info("No eBay price sheets found");
                return data;
            }
            
            // Read data from the latest sheet - updated range to include all prices
            String sheetName = latestSheet.getProperties().getTitle();
            ValueRange response = sheetsService.spreadsheets().values()
                .get(spreadsheetId, sheetName + "!A:L")
                .execute();
            
            List<List<Object>> values = response.getValues();
            if (values == null || values.size() <= 1) {
                return data;
            }
            
            // Skip header row
            for (int i = 1; i < values.size(); i++) {
                List<Object> row = values.get(i);
                Map<String, String> item = new HashMap<>();
                
                // Updated to include listing details and all prices
                if (row.size() > 0) item.put("searchName", row.get(0).toString());
                if (row.size() > 1) item.put("originalName", row.get(1).toString());
                if (row.size() > 2) item.put("language", row.get(2).toString());
                if (row.size() > 3) item.put("set", row.get(3).toString());
                if (row.size() > 4) item.put("productType", row.get(4).toString());
                if (row.size() > 5) item.put("facebookPrice", row.get(5).toString());
                if (row.size() > 6) item.put("ebayMedian", row.get(6).toString());
                if (row.size() > 7) item.put("resultCount", row.get(7).toString());
                if (row.size() > 8) item.put("listingDetails", row.get(8).toString());
                if (row.size() > 9) item.put("allPrices", row.get(9).toString());
                if (row.size() > 10) item.put("top5Prices", row.get(10).toString());
                if (row.size() > 11) item.put("searchTime", row.get(11).toString());
                
                data.add(item);
            }
            
            log.info("Retrieved {} eBay price records from sheet: {}", data.size(), sheetName);
            return data;
            
        } catch (IOException e) {
            log.error("Failed to retrieve eBay price data: {}", e.getMessage(), e);
            return data;
        }
    }
    
    /**
     * Delete existing eBay price sheets for fresh search
     */
    private void deleteEbayPriceSheets() throws IOException {
        try {
            Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
            List<Sheet> sheets = spreadsheet.getSheets();
            
            for (Sheet sheet : sheets) {
                String sheetName = sheet.getProperties().getTitle();
                if (sheetName.startsWith("eBay_Prices_")) {
                    int sheetId = sheet.getProperties().getSheetId();
                    
                    // Delete the sheet
                    DeleteSheetRequest deleteRequest = new DeleteSheetRequest()
                        .setSheetId(sheetId);
                    
                    BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
                        .setRequests(Arrays.asList(new Request().setDeleteSheet(deleteRequest)));
                    
                    sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute();
                    log.info("Deleted old eBay price sheet: {}", sheetName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete old eBay price sheets: {}", e.getMessage());
        }
    }
    
    /**
     * Create a new sheet with the given name
     */
    private void createNewSheet(String sheetName) throws IOException {
        AddSheetRequest addSheetRequest = new AddSheetRequest()
            .setProperties(new SheetProperties()
                .setTitle(sheetName)
                .setGridProperties(new GridProperties()
                    .setRowCount(1000)
                    .setColumnCount(2)));
        
        BatchUpdateSpreadsheetRequest batchUpdateRequest = new BatchUpdateSpreadsheetRequest()
            .setRequests(Arrays.asList(new Request().setAddSheet(addSheetRequest)));
        
        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchUpdateRequest).execute();
        log.debug("Created new sheet: {}", sheetName);
    }
    
    /**
     * Convert markdown report to Google Sheets rows - store complete markdown content
     */
    private List<List<Object>> convertMarkdownToSheetRows(String reportContent, int totalListings) {
        List<List<Object>> rows = new ArrayList<>();
        
        // Add header information
        rows.add(Arrays.asList("Report Type", "Pokemon TCG Market Intelligence"));
        rows.add(Arrays.asList("Generated", LocalDateTime.now().format(DATE_FORMATTER)));
        rows.add(Arrays.asList("Total Listings Analyzed", totalListings));
        rows.add(Arrays.asList("", "")); // Empty row
        
        // Store the complete markdown content in a single cell to preserve formatting
        rows.add(Arrays.asList("MARKDOWN_CONTENT", reportContent));
        
        return rows;
    }
    
    /**
     * Convert Google Sheets rows back to markdown
     */
    private String convertSheetRowsToMarkdown(List<List<Object>> values) {
        // Look for the MARKDOWN_CONTENT cell which contains the complete formatted report
        for (List<Object> row : values) {
            if (row.size() >= 2) {
                String type = row.get(0).toString();
                if ("MARKDOWN_CONTENT".equals(type)) {
                    return row.get(1).toString();
                }
            }
        }
        
        // Fallback: if no MARKDOWN_CONTENT found, return empty string
        log.warn("No MARKDOWN_CONTENT found in Google Sheets, report formatting may be lost");
        return "";
    }
}