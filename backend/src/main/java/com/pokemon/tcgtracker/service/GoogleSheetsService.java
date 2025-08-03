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
import java.util.*;

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
                "Price", "Quantity", "Price Unit",
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
                mainListingPrice,
                location,
                hasMultipleItems != null ? hasMultipleItems : false,
                url,
                notes
        );
        
        // Check if URL already exists and update that row, otherwise append new row
        int existingRowIndex = findRowByUrl(MARKETPLACE_SHEET, url);
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
                .get(spreadsheetId, sheetName + "!I:I") // Column I is Marketplace URL (9th column)
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

    public String getSpreadsheetUrl() {
        return String.format("https://docs.google.com/spreadsheets/d/%s/edit", spreadsheetId);
    }
}