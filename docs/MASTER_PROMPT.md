# Pokemon TCG Tracker - Master Prompt for Claude Code

Create a comprehensive MonoRepo application for tracking Pokemon Trading Cards, sales opportunities, and inventory monitoring across multiple platforms.

## Project Structure Requirements

**MonoRepo Architecture:**
- `/backend` - Java Spring Boot application
- `/frontend` - Next.js application with TypeScript
- `/shared` - Common types and utilities
- Root-level configuration files (Docker, CI/CD, etc.)

## Core Features

### 1. Facebook Marketplace Integration
**Browser Automation Module:**
- Use Selenium WebDriver to connect to existing browser session (Chrome DevTools Protocol)
- Extract listing descriptions, prices, images, and seller information from current logged-in session
- Implement rate limiting and human-like browsing patterns to avoid detection
- Leverage existing authentication and cookies from user's active browser

**LM Studio Integration:**
- Send extracted listing data to local LM Studio API
- Use specialized prompt for Pokemon TCG item identification and standardization
- Parse responses to extract: Item name, condition, price per pack, set information
- Handle various listing formats and seller descriptions

**Google Sheets Integration:**
- Write consolidated data to organized spreadsheets
- Columns: Item Name, Set, Product Type, Condition, Price, Price per Pack, Seller, Date Found, Marketplace URL
- Implement smart categorization (Booster Boxes, Bundles, Single Packs, Singles)
- Add formula calculations for price comparisons and profit margins

### 2. Multi-Store Inventory Monitoring
**Website Scraping System:**
- Selenium-based screenshot capture for target stores
- Configurable store list with custom selectors and patterns
- Send screenshots to LM Studio with specialized stock-checking prompt
- Support for both online and in-store availability detection

**LM Studio Vision Prompt:**
```
You are a Pokemon TCG inventory specialist. Analyze this screenshot of an online store and determine:
1. Which Pokemon TCG products are visible
2. Their current stock status (In Stock, Out of Stock, Limited Stock, Preorder)
3. Current pricing
4. Any special offers or bundles
5. Availability type (Online, In-Store, Both)

Return your analysis in JSON format with product names, stock status, prices, and availability details.
```

**Automated Monitoring:**
- Configurable scheduling system (hourly, daily, custom intervals)
- Store results in separate Google Sheets per store
- Historical tracking of price changes and stock availability
- Alert system for newly available items or price drops

### 3. Next.js Frontend Dashboard
**Modern UI Components:**
- Code Academy-style clean, educational interface
- Dark/light theme support with Pokemon TCG color schemes
- Responsive design for desktop and mobile

**Configuration Management:**
- Store management (add/edit/remove stores to monitor)
- Monitoring schedule configuration
- Facebook Marketplace search parameters
- LM Studio API endpoint configuration
- Google Sheets integration settings

**Data Visualization:**
- Price trend charts for tracked items
- Stock availability heatmaps
- Profit opportunity identification
- Marketplace vs retail price comparisons

**Real-time Updates:**
- WebSocket integration for live monitoring status
- Progress indicators for active scraping sessions
- Live notifications for new opportunities

### 4. Java Backend Services
**Core Services:**
- RESTful API for frontend communication
- Selenium WebDriver management and pooling
- Scheduled job execution (Spring Boot @Scheduled)
- Database layer for caching and historical data

**Integration Services:**
- Google Sheets API integration with authentication
- LM Studio API client with retry logic and error handling
- Chrome browser session connection via DevTools Protocol
- Image processing and screenshot optimization

**Data Storage:**
- All data stored exclusively in Google Sheets
- Multiple sheets for different data types and sources
- No local database - Google Sheets as the single source of truth
- Historical data maintained through sheet versioning and archiving

## Technical Implementation Details

### Backend Stack:
- Java 17+ with Spring Boot 3.x
- Google Sheets API v4 as primary data layer
- Spring Security for API protection
- Selenium WebDriver 4.x with Chrome DevTools Protocol
- Jackson for JSON processing
- Scheduled tasks with @EnableScheduling
- In-memory caching for performance optimization

### Frontend Stack:
- Next.js 14+ with App Router
- TypeScript for type safety
- Tailwind CSS for styling
- shadcn/ui component library
- React Hook Form for form management
- Recharts for data visualization
- Socket.io for real-time updates

### Infrastructure:
- Docker containerization for both services
- Docker Compose for local development
- Environment-based configuration
- Logging with structured JSON format
- Health checks and monitoring endpoints

## Configuration Requirements

### Environment Variables:
```
# LM Studio Configuration
LMSTUDIO_API_URL=http://localhost:1234
LMSTUDIO_MODEL=qwen/qwen2.5-vl-7b

# Google Sheets
GOOGLE_SHEETS_CREDENTIALS_PATH=./credentials.json
GOOGLE_SHEETS_SCOPE=https://www.googleapis.com/auth/spreadsheets

# Browser Integration
CHROME_USER_DATA_DIR=/path/to/chrome/profile
CHROME_PROFILE_NAME=Default
SELENIUM_HEADLESS=false
CHROME_DEBUGGER_PORT=9222
```

### LM Studio API Integration:
Use the provided curl example as a template for the HTTP client implementation:
- Model: qwen/qwen2.5-vl-7b for vision capabilities
- Include specialized system prompts for different tasks
- Handle image encoding for screenshot analysis
- Implement proper error handling and retries

## Security Considerations:
- Secure browser session connection without credential exposure
- Rate limiting to prevent IP blocking
- User agent consistency with existing browser session
- CAPTCHA detection and handling strategies
- Secure API endpoints with authentication
- Google Sheets access controls and sharing permissions

## Deployment Strategy:
- Local development with Docker Compose
- Production deployment guides for cloud platforms
- Backup strategies for Google Sheets data
- Monitoring and alerting setup

Create this as a professional, maintainable codebase with comprehensive documentation, testing, and error handling. Focus on reliability and user experience while respecting website terms of service and implementing ethical scraping practices.