# Pokemon TCG Tracker

A comprehensive MonoRepo application for tracking Pokemon Trading Cards, sales opportunities, and inventory monitoring across multiple platforms.

## Project Structure

```
.
├── backend/          # Java Spring Boot application
├── frontend/         # Next.js application with TypeScript
├── shared/           # Common types and utilities
├── docs/             # Documentation
│   └── MASTER_PROMPT.md  # Original project requirements
├── docker-compose.yml
└── README.md
```

## Features

- **Facebook Marketplace Integration** - Automated listing extraction and analysis
- **Multi-Store Inventory Monitoring** - Track stock across multiple retailers
- **LM Studio Integration** - AI-powered item identification and analysis
- **Google Sheets Storage** - All data stored in organized spreadsheets
- **Modern Dashboard** - Next.js frontend with real-time updates

## Prerequisites

- Java 17+
- Node.js 18+
- Docker & Docker Compose
- LM Studio running locally
- Google Sheets API credentials

## Quick Start

1. Clone the repository
2. Set up environment variables (see Configuration)
3. Install dependencies:
   ```bash
   cd backend && ./gradlew build
   cd ../frontend && npm install
   ```
4. Run with Docker Compose:
   ```bash
   docker-compose up
   ```

## Configuration

Create a `.env` file in the root directory:

```env
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

## Development

- Backend: `cd backend && ./gradlew bootRun`
- Frontend: `cd frontend && npm run dev`

### Application Restart

When making code changes that require a restart (configuration changes, new dependencies, etc.), you can use the restart API for a gentle refresh or manually restart for full changes:

**Gentle restart (refreshes application context):**
```bash
curl -X POST http://localhost:8080/api/test/restart
```

**Full shutdown (requires manual restart):**
```bash
curl -X POST http://localhost:8080/api/test/shutdown
```

**Note:** The restart API performs a gentle application context refresh and won't kill your terminal. For major changes (new dependencies, configuration changes), you may need to manually stop (Ctrl+C) and restart the application.

**Important:** After completing any development task that involves:
- Adding new dependencies
- Modifying configuration files
- Adding new Spring components
- Database schema changes

Consider restarting the application to ensure changes take effect properly.

### API Endpoints

- `GET /api/test/health` - Check application health
- `GET /api/test/models` - List available LM Studio models
- `POST /api/test/restart` - Restart the application
- `POST /api/test/shutdown` - Shutdown the application
- `POST /api/test/test-marketplace` - Test marketplace listing creation

## License

Private project - All rights reserved