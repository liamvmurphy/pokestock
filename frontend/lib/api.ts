// API configuration
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080'

// API endpoints
export const apiEndpoints = {
  marketplace: {
    listings: `${API_BASE_URL}/api/marketplace/listings`,
    start: `${API_BASE_URL}/api/marketplace/start`,
    status: `${API_BASE_URL}/api/marketplace/status`,
    search: `${API_BASE_URL}/api/marketplace/search`,
    scrape: `${API_BASE_URL}/api/marketplace/scrape`,
    configure: `${API_BASE_URL}/api/marketplace/configure`,
    stop: `${API_BASE_URL}/api/marketplace/stop`,
    history: `${API_BASE_URL}/api/marketplace/history`,
    searchTerms: `${API_BASE_URL}/api/marketplace/search-terms`,
  },
  csv: {
    upload: `${API_BASE_URL}/api/csv/upload`,
    analyze: `${API_BASE_URL}/api/csv/analyze`,
    autoAnalyze: `${API_BASE_URL}/api/csv/auto-analyze`,
    download: `${API_BASE_URL}/api/csv/download`,
    health: `${API_BASE_URL}/api/csv/health`,
    sample: `${API_BASE_URL}/api/csv/sample`,
    lastReport: `${API_BASE_URL}/api/csv/last-report`,
    googleSheetsInfo: `${API_BASE_URL}/api/csv/google-sheets/info`,
  },
  test: {
    hello: `${API_BASE_URL}/api/test/hello`,
  }
}

// Helper function for network access info
export const getNetworkInfo = () => {
  const hostIP = process.env.NEXT_PUBLIC_HOST_IP || 'localhost'
  return {
    hostIP,
    frontendUrl: `http://${hostIP}:3000`,
    backendUrl: `http://${hostIP}:8080`,
    isNetworkMode: hostIP !== 'localhost'
  }
}