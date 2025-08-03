# Google Sheets API Setup Guide

## Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" → "New Project"
3. Name it "Pokemon TCG Tracker" and create

## Step 2: Enable Google Sheets API

1. In your project, go to "APIs & Services" → "Library"
2. Search for "Google Sheets API"
3. Click on it and press "Enable"

## Step 3: Create Service Account Credentials

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "Service Account"
3. Fill in:
   - Service account name: `tcg-tracker-service`
   - Service account ID: (auto-generated)
   - Description: "Service account for Pokemon TCG Tracker"
4. Click "Create and Continue"
5. Skip the optional steps and click "Done"

## Step 4: Generate JSON Key

1. In the Credentials page, find your service account
2. Click on the service account email
3. Go to "Keys" tab
4. Click "Add Key" → "Create new key"
5. Choose "JSON" and click "Create"
6. Save the downloaded file as `credentials.json` in the `backend/` directory

## Step 5: Create and Share Google Sheets

1. Create a new Google Sheet for your data
2. Name it "Pokemon TCG Tracker Data"
3. Click "Share" button
4. Add the service account email (found in credentials.json as "client_email")
5. Give it "Editor" permissions
6. Copy the Sheet ID from the URL:
   - URL: `https://docs.google.com/spreadsheets/d/[SHEET_ID]/edit`

## Step 6: Configure Application

1. Create `backend/application-local.properties`:
```properties
# Google Sheets Configuration
google.sheets.spreadsheet.id=YOUR_SHEET_ID_HERE
```

2. Update `.gitignore` to exclude sensitive files:
```
backend/credentials.json
backend/application-local.properties
```

## Security Notes

- **NEVER** commit `credentials.json` to version control
- Keep your service account key secure
- Regularly rotate keys if needed
- Use environment variables in production

## Testing the Connection

Once configured, the application will automatically:
- Connect to Google Sheets on startup
- Create necessary sheets and headers
- Begin writing data as it's collected