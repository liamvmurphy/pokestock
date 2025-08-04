# Facebook Marketplace Monitoring Setup

## Prerequisites

1. **Chrome Browser** - Must be installed and accessible
2. **ChromeDriver** - Will be automatically managed by Selenium
3. **Facebook Account** - For accessing Marketplace (optional but recommended)

## Setup Steps

### 1. Chrome Profile Setup (Recommended)

For best results, use an existing Chrome profile that's already logged into Facebook:

1. **Find your Chrome profile directory:**
   - Windows: `C:\Users\[USERNAME]\AppData\Local\Google\Chrome\User Data`
   - Mac: `~/Library/Application Support/Google/Chrome`
   - Linux: `~/.config/google-chrome`

2. **Update configuration:**
   ```properties
   # In backend/src/main/resources/application-local.properties
   chrome.user.data.dir=C:/Users/[USERNAME]/AppData/Local/Google/Chrome/User Data
   chrome.profile.name=Default
   selenium.headless=false
   ```

### 2. Enable Marketplace Monitoring

```properties
# Enable scheduled monitoring (optional)
marketplace.monitoring.enabled=true
marketplace.monitoring.interval=1800000  # 30 minutes
```

### 3. Chrome Debug Port Setup

If you want to connect to an existing Chrome instance:

1. **Start Chrome with remote debugging:**
   ```bash
   chrome.exe --remote-debugging-port=9222 --user-data-dir="C:/path/to/profile"
   ```

2. **Configure debug port:**
   ```properties
   chrome.debugger.port=9222
   ```

## API Endpoints

### Start Monitoring
```bash
POST http://localhost:8081/api/marketplace/start
```

### Check Status
```bash
GET http://localhost:8081/api/marketplace/status
```

### Search for Specific Term
```bash
POST http://localhost:8081/api/marketplace/search
Content-Type: application/json

{
  "searchTerm": "pokemon stellar crown"
}
```

### Get Search Terms
```bash
GET http://localhost:8081/api/marketplace/search-terms
```

## Search Terms

The system automatically searches for:
- pokemon tcg
- pokemon cards
- pokemon booster box
- pokemon elite trainer box
- pokemon stellar crown
- pokemon paradox rift
- pokemon 151

## Data Flow

1. **Web Scraping** - Selenium navigates Facebook Marketplace
2. **Screenshot Analysis** - LM Studio analyzes listing images
3. **Data Extraction** - Extract prices, titles, seller info
4. **AI Enhancement** - LM Studio identifies Pokemon products and details
5. **Storage** - Save results to Google Sheets

## Monitoring Features

### Automatic Analysis
- **Product Identification** - Detects Pokemon TCG products
- **Price Extraction** - Finds and validates pricing
- **Condition Assessment** - Identifies item condition
- **Set Recognition** - Determines Pokemon card sets

### Anti-Detection
- **Human-like Behavior** - Random delays and scrolling
- **Browser Profile Usage** - Uses existing logged-in sessions
- **Rate Limiting** - Respects Facebook's request limits
- **Error Handling** - Graceful recovery from blocks

## Troubleshooting

### Common Issues

1. **Chrome not found**
   ```
   Solution: Install Chrome or update ChromeDriver path
   ```

2. **Profile access denied**
   ```
   Solution: Close all Chrome instances before running
   ```

3. **Facebook login required**
   ```
   Solution: Use authenticated Chrome profile or manual login
   ```

4. **Rate limiting detected**
   ```
   Solution: Increase delays or reduce monitoring frequency
   ```

### Debug Mode

Enable detailed logging:
```properties
logging.level.com.pokemon.tcgtracker=TRACE
logging.level.org.openqa.selenium=DEBUG
```

## Security Considerations

- **Never commit browser profiles** containing personal data
- **Use separate Facebook account** for automated activities
- **Respect Facebook's Terms of Service**
- **Monitor for rate limiting** and adjust accordingly
- **Keep ChromeDriver updated** for security patches

## Performance Optimization

1. **Use headless mode** for production:
   ```properties
   selenium.headless=true
   ```

2. **Adjust monitoring intervals** based on needs:
   ```properties
   marketplace.monitoring.interval=3600000  # 1 hour
   ```

3. **Limit concurrent searches** to avoid overwhelming the system

## Testing

1. **Test browser connection:**
   ```bash
   POST http://localhost:8081/api/test/test-browser
   ```

2. **Test marketplace integration:**
   ```bash
   POST http://localhost:8081/api/marketplace/start
   ```

3. **Monitor logs** for successful connections and data extraction

The system will automatically handle login sessions, extract relevant Pokemon TCG listings, and save them to your Google Sheets for analysis.