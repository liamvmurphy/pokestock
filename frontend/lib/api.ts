// API configuration - Dynamic base URL for network access
export function getApiBaseUrl(): string {
  // Use environment variable if set
  if (process.env.NEXT_PUBLIC_API_BASE_URL) {
    return process.env.NEXT_PUBLIC_API_BASE_URL
  }
  
  // Check if we're in the browser
  if (typeof window === 'undefined') {
    return 'http://localhost:8080'
  }
  
  // Use dynamic URL based on current hostname
  return window.location.hostname === 'localhost' 
    ? 'http://localhost:8080' 
    : `http://${window.location.hostname}:8080`
}

// API endpoints - Now using dynamic base URL
export const apiEndpoints = {
  marketplace: {
    listings: () => `${getApiBaseUrl()}/api/marketplace/listings`,
    start: () => `${getApiBaseUrl()}/api/marketplace/start`,
    status: () => `${getApiBaseUrl()}/api/marketplace/status`,
    search: () => `${getApiBaseUrl()}/api/marketplace/search`,
    scrape: () => `${getApiBaseUrl()}/api/marketplace/scrape`,
    configure: () => `${getApiBaseUrl()}/api/marketplace/configure`,
    stop: () => `${getApiBaseUrl()}/api/marketplace/stop`,
    history: () => `${getApiBaseUrl()}/api/marketplace/history`,
    searchTerms: () => `${getApiBaseUrl()}/api/marketplace/search-terms`,
  },
  csv: {
    upload: () => `${getApiBaseUrl()}/api/csv/upload`,
    analyze: () => `${getApiBaseUrl()}/api/csv/analyze`,
    autoAnalyze: () => `${getApiBaseUrl()}/api/csv/auto-analyze`,
    download: () => `${getApiBaseUrl()}/api/csv/download`,
    health: () => `${getApiBaseUrl()}/api/csv/health`,
    sample: () => `${getApiBaseUrl()}/api/csv/sample`,
    lastReport: () => `${getApiBaseUrl()}/api/csv/last-report`,
    googleSheetsInfo: () => `${getApiBaseUrl()}/api/csv/google-sheets/info`,
  },
  test: {
    hello: () => `${getApiBaseUrl()}/api/test/hello`,
  },
  ebayPrice: {
    results: () => `${getApiBaseUrl()}/api/ebay-price/results`,
    searchFromMarketplace: () => `${getApiBaseUrl()}/api/ebay-price/search-from-marketplace`,
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