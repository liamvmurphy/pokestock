'use client'

import { useState, useEffect } from 'react'
import { DashboardLayout } from '@/components/layout/dashboard-layout'
import { apiEndpoints } from '@/lib/api'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Progress } from '@/components/ui/progress'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Badge } from '@/components/ui/badge'
import { 
  Search, 
  TrendingUp, 
  DollarSign, 
  ShoppingCart, 
  ExternalLink,
  AlertCircle,
  CheckCircle,
  BarChart3,
  Target,
  Activity
} from 'lucide-react'

interface EbayPriceResult {
  searchName: string
  originalName: string
  set: string
  productType: string
  facebookPrice: string
  ebayMedian: string
  resultCount: string
  listingDetails: string
  allPrices: string
  top5Prices: string
  searchTime: string
}

export default function EbayPricePage() {
  const [isSearching, setIsSearching] = useState(false)
  const [progress, setProgress] = useState(0)
  const [results, setResults] = useState<EbayPriceResult[]>([])
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)
  const [hasExistingData, setHasExistingData] = useState(false)
  const [hasMarketplaceData, setHasMarketplaceData] = useState(false)

  useEffect(() => {
    loadExistingResults()
    checkMarketplaceData()
  }, [])

  const loadExistingResults = async () => {
    try {
      const response = await fetch(apiEndpoints.ebayPrice.results())
      const data = await response.json()
      
      if (data.hasData && data.data) {
        setResults(data.data)
        setHasExistingData(true)
      }
    } catch (error) {
      console.log('No existing data found:', error)
    }
  }

  const checkMarketplaceData = async () => {
    try {
      const response = await fetch(apiEndpoints.marketplace.listings())
      const data = await response.json()
      
      if (data.allListings && data.allListings.length > 0) {
        setHasMarketplaceData(true)
      }
    } catch (error) {
      console.log('No marketplace data found:', error)
      setHasMarketplaceData(false)
    }
  }

  const handleSearch = async () => {
    if (!hasMarketplaceData) {
      setError('No Facebook Marketplace data found. Please run marketplace scanning first.')
      return
    }

    setIsSearching(true)
    setError(null)
    setSuccess(null)
    setProgress(0)

    try {
      // Simulate progress updates
      const progressInterval = setInterval(() => {
        setProgress(prev => Math.min(prev + 5, 90))
      }, 1000)

      const response = await fetch(apiEndpoints.ebayPrice.searchFromMarketplace(), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      clearInterval(progressInterval)
      setProgress(100)

      if (!response.ok) {
        const errorData = await response.json()
        throw new Error(errorData.error || 'Search failed')
      }

      const data = await response.json()
      
      if (data.error) {
        throw new Error(data.error)
      }

      setSuccess(`✅ Fresh eBay search completed! Analyzed ${data.totalSearched} items with current market prices.`)
      
      // Reload results to show new data
      await loadExistingResults()
      
    } catch (error) {
      setError(error instanceof Error ? error.message : 'An error occurred during search')
    } finally {
      setIsSearching(false)
      setProgress(0)
    }
  }

  const formatPrice = (price: string) => {
    if (!price || price === '') return 'N/A'
    const num = parseFloat(price)
    return isNaN(num) ? price : `$${num.toFixed(2)}`
  }

  const calculateSavings = (facebookPrice: string, ebayPrice: string) => {
    const fbPrice = parseFloat(facebookPrice?.replace(/[^0-9.]/g, '') || '0')
    const ebPrice = parseFloat(ebayPrice || '0')
    
    if (fbPrice <= 0 || ebPrice <= 0) return null
    
    const savings = ((ebPrice - fbPrice) / ebPrice * 100)
    return savings
  }

  const getSavingsBadge = (savings: number | null) => {
    if (savings === null) return null
    
    if (savings > 30) {
      return <Badge className="bg-red-100 text-red-800">+{savings.toFixed(1)}% more expensive</Badge>
    } else if (savings > 10) {
      return <Badge className="bg-yellow-100 text-yellow-800">+{savings.toFixed(1)}% more</Badge>
    } else if (savings > -10) {
      return <Badge className="bg-gray-100 text-gray-800">Similar price</Badge>
    } else if (savings > -30) {
      return <Badge className="bg-green-100 text-green-800">{Math.abs(savings).toFixed(1)}% cheaper</Badge>
    } else {
      return <Badge className="bg-green-100 text-green-800 font-bold">{Math.abs(savings).toFixed(1)}% GREAT DEAL!</Badge>
    }
  }
  
  const renderPriceGraph = (pricesStr: string, allMedian: number) => {
    if (!pricesStr) return null
    
    const prices = pricesStr.split(',').map(p => parseFloat(p)).filter(p => !isNaN(p))
    if (prices.length === 0) return null
    
    // Take up to 5 prices and reverse them so index 0 = item 5, index 4 = item 1 (most recent)
    const top5Prices = prices.slice(0, 5).reverse()
    
    const min = Math.min(...top5Prices)
    const max = Math.max(...top5Prices)
    const top5Median = top5Prices.length > 0 ? calculateMedian(top5Prices) : 0
    
    // Calculate SVG path for the line graph
    const width = 100
    const height = 40
    const padding = 4
    
    const xStep = (width - padding * 2) / Math.max(1, top5Prices.length - 1)
    const yRange = max - min || 1 // Avoid division by zero
    
    const points = top5Prices.map((price, index) => {
      const x = padding + (index * xStep)
      const y = height - padding - ((price - min) / yRange) * (height - padding * 2)
      return `${x},${y}`
    }).join(' ')
    
    return (
      <div className="mt-1">
        <div className="text-xs text-muted-foreground mb-2 font-medium">Price Trajectory (5→1)</div>
        <div className="relative">
          <svg width="100%" height="40" viewBox={`0 0 ${width} ${height}`} className="overflow-visible">
            {/* Grid lines */}
            <defs>
              <pattern id="grid" width="20" height="10" patternUnits="userSpaceOnUse">
                <path d="M 20 0 L 0 0 0 10" fill="none" stroke="currentColor" strokeWidth="0.5" opacity="0.1"/>
              </pattern>
            </defs>
            <rect width="100%" height="100%" fill="url(#grid)" />
            
            {/* Price line */}
            <polyline
              points={points}
              fill="none"
              stroke="rgb(59, 130, 246)"
              strokeWidth="2"
              className="drop-shadow-sm"
            />
            
            {/* Data points */}
            {top5Prices.map((price, index) => {
              const x = padding + (index * xStep)
              const y = height - padding - ((price - min) / yRange) * (height - padding * 2)
              return (
                <circle
                  key={index}
                  cx={x}
                  cy={y}
                  r="2"
                  fill="rgb(59, 130, 246)"
                  className="drop-shadow-sm"
                />
              )
            })}
          </svg>
          
          {/* Labels */}
          <div className="flex justify-between text-[9px] text-muted-foreground mt-1">
            <span>Item 5</span>
            <span className="font-medium">→ Item 1 (Recent)</span>
          </div>
        </div>
        
        <div className="flex justify-between text-[10px] text-muted-foreground mt-2">
          <span>Min: ${min.toFixed(2)}</span>
          <span className="font-medium text-purple-600">Top5 Med: ${top5Median.toFixed(2)}</span>
          <span>Max: ${max.toFixed(2)}</span>
        </div>
      </div>
    )
  }
  
  const calculateMedian = (prices: number[]) => {
    const sorted = [...prices].sort((a, b) => a - b)
    const mid = Math.floor(sorted.length / 2)
    return sorted.length % 2 === 0 
      ? (sorted[mid - 1] + sorted[mid]) / 2 
      : sorted[mid]
  }

  return (
    <DashboardLayout>
      <div className="space-y-8 p-6">
        {/* Header */}
        <div className="flex items-center justify-between bg-gradient-to-r from-green-600/10 to-blue-600/10 rounded-2xl p-6 border border-green-500/20 dark:from-green-900/20 dark:to-blue-900/20 dark:border-green-800/30">
          <div className="space-y-2">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-green-600 rounded-lg">
                <BarChart3 className="h-6 w-6 text-white" />
              </div>
              <h1 className="text-4xl font-bold bg-gradient-to-r from-green-600 to-blue-600 bg-clip-text text-transparent">
                eBay Price Analysis
              </h1>
            </div>
            <p className="text-muted-foreground text-lg">
              Compare Facebook Marketplace prices with eBay sold listings
            </p>
          </div>
        </div>

        {/* Analysis Section */}
        <Card className="hover:border-green-500/50 transition-all duration-300">
          <CardHeader>
            <CardTitle className="flex items-center text-xl">
              <Activity className="h-5 w-5 mr-2 text-green-600" />
              Analyze Facebook Marketplace Data
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="bg-muted/50 p-4 rounded-lg">
              <div className="flex items-center space-x-3 mb-2">
                <div className={`w-3 h-3 rounded-full ${hasMarketplaceData ? 'bg-green-500' : 'bg-red-500'}`}></div>
                <span className="font-medium">
                  Facebook Marketplace Data: {hasMarketplaceData ? 'Available' : 'Not Found'}
                </span>
              </div>
              <p className="text-sm text-muted-foreground">
                {hasMarketplaceData 
                  ? 'Ready to analyze eBay prices for your marketplace listings'
                  : 'Run Facebook marketplace scanning first to populate data'
                }
              </p>
            </div>

            <Button 
              onClick={handleSearch}
              disabled={!hasMarketplaceData || isSearching}
              className="w-full bg-gradient-to-r from-green-600 to-blue-600 hover:from-green-700 hover:to-blue-700 text-white"
            >
              <Search className="h-4 w-4 mr-2" />
              {isSearching ? 'Searching eBay (Fresh Results)...' : 'Start Fresh eBay Price Search'}
            </Button>

            {!hasMarketplaceData && (
              <Button 
                onClick={() => window.open('/marketplace', '_blank')}
                variant="outline"
                className="w-full"
              >
                <Target className="h-4 w-4 mr-2" />
                Go to Facebook Marketplace Scanner
              </Button>
            )}

          {isSearching && (
            <div className="space-y-2">
              <Progress value={progress} className="w-full" />
              <p className="text-sm text-gray-600 text-center">
                Searching eBay sold listings... This may take a few minutes.
              </p>
            </div>
          )}

          {error && (
            <Alert className="border-red-200 bg-red-50 dark:bg-red-900/20 dark:border-red-800">
              <AlertCircle className="h-4 w-4 text-red-600" />
              <AlertDescription className="text-red-600 dark:text-red-400">
                {error}
              </AlertDescription>
            </Alert>
          )}

          {success && (
            <Alert className="border-green-200 bg-green-50 dark:bg-green-900/20 dark:border-green-800">
              <CheckCircle className="h-4 w-4 text-green-600" />
              <AlertDescription className="text-green-600 dark:text-green-400">
                {success}
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      </Card>

        {/* Results Section */}
        {results.length > 0 && (
          <Card className="hover:border-blue-500/50 transition-all duration-300">
            <CardHeader>
              <CardTitle className="flex items-center justify-between text-xl">
                <div className="flex items-center">
                  <TrendingUp className="h-5 w-5 mr-2 text-blue-600" />
                  Price Comparison Results
                </div>
                <Badge className="bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400">
                  {results.length} items analyzed
                </Badge>
              </CardTitle>
            </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {results.map((result, index) => {
                const savings = calculateSavings(result.facebookPrice, result.ebayMedian)
                
                return (
                  <div key={index} className="border rounded-lg p-4 hover:bg-muted/50 transition-all duration-300 dark:border-gray-700">
                    <div className="flex justify-between items-start mb-3">
                      <div className="flex-1">
                        <h3 className="font-semibold text-lg mb-1">
                          {result.originalName}
                        </h3>
                        <div className="text-sm text-muted-foreground space-x-4">
                          {result.set && <span>Set: {result.set}</span>}
                          {result.productType && <span>Type: {result.productType}</span>}
                          {result.language && <span>Language: {result.language}</span>}
                        </div>
                      </div>
                      {getSavingsBadge(savings)}
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm">
                      <div className="bg-blue-50 p-4 rounded-lg dark:bg-blue-900/20">
                        <div className="font-medium text-blue-800 flex items-center dark:text-blue-400 mb-2">
                          <ShoppingCart className="h-4 w-4 mr-1" />
                          Facebook Price
                        </div>
                        <div className="text-2xl font-bold text-blue-900 dark:text-blue-300">
                          {formatPrice(result.facebookPrice)}
                        </div>
                      </div>

                      <div className="bg-green-50 p-4 rounded-lg dark:bg-green-900/20">
                        <div className="font-medium text-green-800 flex items-center dark:text-green-400 mb-2">
                          <DollarSign className="h-4 w-4 mr-1" />
                          eBay Median
                        </div>
                        <div className="text-2xl font-bold text-green-900 dark:text-green-300">
                          {formatPrice(result.ebayMedian)}
                        </div>
                      </div>

                      <div className="bg-purple-50 p-4 rounded-lg dark:bg-purple-900/20">
                        <div className="font-medium text-purple-700 dark:text-purple-400 mb-2">
                          Price Trajectory (Top 5)
                        </div>
                        <div className="h-16">
                          {result.top5Prices && renderPriceGraph(result.top5Prices, parseFloat(result.ebayMedian))}
                        </div>
                      </div>
                    </div>

                    {result.listingDetails && (
                      <details className="mt-4">
                        <summary className="cursor-pointer text-sm text-muted-foreground hover:text-foreground font-medium">
                          Recent eBay Listings (All Results)
                        </summary>
                        <div className="mt-3 text-xs bg-muted/50 p-3 rounded-lg">
                          <div className="space-y-2">
                            {result.listingDetails.split(' | ').map((detail, index) => {
                              // Parse markdown link format: [Title_$Price](URL)
                              const match = detail.match(/\[(.*?)\]\((.*?)\)/);
                              if (match) {
                                const [, titlePrice, url] = match;
                                return (
                                  <div key={index} className="flex items-center space-x-2">
                                    <span className="text-blue-600 dark:text-blue-400">•</span>
                                    <a 
                                      href={url} 
                                      target="_blank" 
                                      rel="noopener noreferrer"
                                      className="text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300 underline hover:no-underline transition-colors"
                                    >
                                      {titlePrice}
                                    </a>
                                  </div>
                                );
                              }
                              return (
                                <div key={index} className="break-words text-muted-foreground">
                                  {detail}
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      </details>
                    )}
                  </div>
                )
              })}
            </div>
          </CardContent>
        </Card>
        )}

        {!hasExistingData && results.length === 0 && (
          <Card className="hover:border-green-500/50 transition-all duration-300">
            <CardContent className="text-center py-12">
              <div className="p-4 bg-green-500/20 rounded-full w-24 h-24 mx-auto mb-4 flex items-center justify-center">
                <BarChart3 className="h-12 w-12 text-green-500" />
              </div>
              <h3 className="text-xl font-semibold mb-2">No Price Data Yet</h3>
              <p className="text-muted-foreground mb-6">
                Run Facebook Marketplace scanning first to populate data for eBay price analysis
              </p>
              <Button 
                onClick={() => window.open('/marketplace', '_blank')}
                className="bg-green-600 hover:bg-green-700 text-white"
              >
                <Target className="h-4 w-4 mr-2" />
                Go to Marketplace Scanner
              </Button>
            </CardContent>
          </Card>
        )}
      </div>
    </DashboardLayout>
  )
}