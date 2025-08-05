"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Search,
  Filter,
  ExternalLink,
  TrendingUp,
  DollarSign,
  Calendar,
  User,
  Package,
  Zap,
  Target,
  Activity
} from "lucide-react"
import { apiEndpoints } from "@/lib/api"

interface MarketplaceListing {
  id: string
  itemName: string
  set: string
  productType: string
  price: string | number
  quantity: string | number
  priceUnit: string
  mainListingPrice: string
  location: string
  hasMultipleItems: boolean | string
  marketplaceUrl: string
  notes: string
  dateFound: string
  source: string
  available: boolean
}

export default function MarketplacePage() {
  const [allListings, setAllListings] = useState<MarketplaceListing[]>([])
  const [availableListings, setAvailableListings] = useState<MarketplaceListing[]>([])
  const [loading, setLoading] = useState(false)
  const [monitoring, setMonitoring] = useState(false)
  const [lastUpdate, setLastUpdate] = useState<number>(0)

  // Fetch listings from API
  const fetchListings = async () => {
    try {
      setLoading(true)
      console.log('Attempting to fetch listings from API...')
      
      const response = await fetch(apiEndpoints.marketplace.listings)
      console.log('API Response status:', response.status)
      
      if (response.ok) {
        const data = await response.json()
        console.log('Raw API response data:', data)
        
        const allListings = data.allListings || []
        const availableListings = data.availableListings || []
        
        console.log('All listings count:', allListings.length)
        console.log('Available listings count:', availableListings.length)
        
        setAllListings(allListings)
        setAvailableListings(availableListings)
        setLastUpdate(data.lastUpdate || Date.now())
        
        if (allListings.length === 0) {
          console.warn('No listings found in API response')
        }
      } else {
        const errorText = await response.text()
        console.error('Failed to fetch listings:', response.status, errorText)
      }
    } catch (error) {
      console.error('Error fetching listings:', error)
      console.error('Is the backend server running on port 8080?')
    } finally {
      setLoading(false)
    }
  }

  // Fetch listings on component mount and set up auto-refresh
  useEffect(() => {
    fetchListings()
    
    // Set up 15-minute auto-refresh (15 * 60 * 1000 = 900000ms)
    const refreshInterval = setInterval(fetchListings, 15 * 60 * 1000)
    
    // Cleanup interval on component unmount
    return () => clearInterval(refreshInterval)
  }, [])

  const getStatusColor = (available: boolean) => {
    return available ? 'default' : 'secondary'
  }
  
  const getStatusText = (available: boolean) => {
    return available ? 'Available' : 'Unavailable'
  }

  const getProductTypeIcon = (type: string) => {
    switch (type.toLowerCase()) {
      case 'etb':
      case 'elite trainer box': 
        return <Package className="h-5 w-5 text-blue-500" />
      case 'booster box': 
        return <Package className="h-5 w-5 text-purple-500" />
      case 'bundle': 
        return <Package className="h-5 w-5 text-green-500" />
      case 'tin':
        return <Package className="h-5 w-5 text-yellow-500" />
      case 'single':
        return <Package className="h-5 w-5 text-pink-500" />
      default: 
        return <Package className="h-5 w-5 text-muted-foreground" />
    }
  }

  const startMarketplaceMonitoring = async () => {
    setLoading(true)
    try {
      console.log('Starting marketplace monitoring...')
      const response = await fetch(apiEndpoints.marketplace.start, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
      })
      
      console.log('Response status:', response.status)
      const data = await response.json()
      console.log('Response data:', data)
      
      if (response.ok) {
        setMonitoring(true)
        alert('Marketplace monitoring started successfully!')
        console.log('Marketplace monitoring started successfully')
        // Refresh listings after a short delay to get updated data
        setTimeout(() => {
          fetchListings()
        }, 2000)
      } else {
        alert(`Failed to start marketplace monitoring: ${data.error || 'Unknown error'}`)
        console.error('Failed to start marketplace monitoring:', data)
      }
    } catch (error: any) {
      alert(`Error starting marketplace monitoring: ${error.message}`)
      console.error('Error starting marketplace monitoring:', error)
    } finally {
      setLoading(false)
    }
  }

  const checkMonitoringStatus = async () => {
    try {
      const response = await fetch(apiEndpoints.marketplace.status)
      if (response.ok) {
        const data = await response.json()
        setMonitoring(data.running || false)
      }
    } catch (error) {
      console.error('Error checking monitoring status:', error)
    }
  }

  useEffect(() => {
    checkMonitoringStatus()
  }, [])

  return (
    <DashboardLayout>
      <div className="space-y-8 p-6">
        {/* Header */}
        <div className="flex items-center justify-between bg-gradient-to-r from-blue-600/10 to-purple-600/10 rounded-2xl p-6 border border-blue-500/20 dark:from-blue-900/20 dark:to-purple-900/20 dark:border-blue-800/30">
          <div className="space-y-2">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-blue-600 rounded-lg">
                <Target className="h-6 w-6 text-white" />
              </div>
              <h1 className="text-4xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent">
                Pokemon Marketplace Scanner
              </h1>
            </div>
            <p className="text-muted-foreground text-lg">
              AI-powered Pokemon TCG marketplace monitoring & analysis
            </p>
          </div>
          <div className="flex space-x-3">
            <Button 
              variant="outline" 
              onClick={fetchListings} 
              disabled={loading}
            >
              <Activity className="h-4 w-4 mr-2" />
              {loading ? 'Refreshing...' : 'Refresh Data'}
            </Button>
            <Button 
              variant="outline"
              onClick={() => window.open('/csv-analysis', '_blank')}
            >
              <Package className="h-4 w-4 mr-2" />
              Analyze CSV
            </Button>
            <Button 
              onClick={startMarketplaceMonitoring}
              disabled={loading || monitoring}
              className="bg-gradient-to-r from-blue-600 to-purple-600 hover:from-blue-700 hover:to-purple-700 text-white"
            >
              <Zap className="h-4 w-4 mr-2" />
              {loading ? 'Starting...' : monitoring ? 'Monitoring Active' : 'Start Scanning'}
            </Button>
          </div>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <Card className="hover:border-blue-500/50 transition-all duration-300">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Listings</CardTitle>
              <div className="p-2 bg-blue-500/20 rounded-lg">
                <Package className="h-4 w-4 text-blue-500" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">{allListings.length}</div>
              <p className="text-xs text-muted-foreground mt-1">
                Items tracked across marketplace
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-green-500/50 transition-all duration-300">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Available Now</CardTitle>
              <div className="p-2 bg-green-500/20 rounded-lg">
                <TrendingUp className="h-4 w-4 text-green-500" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-green-600">
                {availableListings.length}
              </div>
              <p className="text-xs text-muted-foreground mt-1">
                Ready for purchase
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-yellow-500/50 transition-all duration-300">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Avg Price</CardTitle>
              <div className="p-2 bg-yellow-500/20 rounded-lg">
                <DollarSign className="h-4 w-4 text-yellow-500" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-yellow-600">
                ${allListings.length > 0 ? (allListings.reduce((sum, l) => {
                  const price = typeof l.price === 'string' ? parseFloat(l.price) || 0 : l.price || 0
                  return sum + price
                }, 0) / allListings.length).toFixed(0) : '0'}
              </div>
              <p className="text-xs text-muted-foreground mt-1">
                Across all active listings
              </p>
            </CardContent>
          </Card>

          <Card className="hover:border-purple-500/50 transition-all duration-300">
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Last Scan</CardTitle>
              <div className="p-2 bg-purple-500/20 rounded-lg">
                <Calendar className="h-4 w-4 text-purple-500" />
              </div>
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-purple-600">
                {lastUpdate ? Math.floor((Date.now() - lastUpdate) / 60000) : 0}m
              </div>
              <p className="text-xs text-muted-foreground mt-1">
                minutes ago
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Listings */}
        <Tabs defaultValue="all" className="space-y-6">
          <TabsList>
            <TabsTrigger 
              value="all" 
              className="data-[state=active]:bg-blue-600 data-[state=active]:text-white"
            >
              All Listings
            </TabsTrigger>
            <TabsTrigger 
              value="available" 
              className="data-[state=active]:bg-green-600 data-[state=active]:text-white"
            >
              Available
            </TabsTrigger>
            <TabsTrigger 
              value="watchlist" 
              className="data-[state=active]:bg-purple-600 data-[state=active]:text-white"
            >
              Watchlist
            </TabsTrigger>
          </TabsList>

          <TabsContent value="all" className="space-y-4">
            <div className="grid gap-6">
              {allListings.length === 0 && !loading ? (
                <Card>
                  <CardContent className="p-8 text-center">
                    <div className="p-4 bg-muted/50 rounded-full w-24 h-24 mx-auto mb-4 flex items-center justify-center">
                      <Package className="h-12 w-12 text-muted-foreground" />
                    </div>
                    <h3 className="text-xl font-semibold mb-2">No listings found</h3>
                    <p className="mb-6 text-muted-foreground">Start scanning to discover Pokemon TCG listings</p>
                    <Button 
                      onClick={fetchListings} 
                      className="bg-blue-600 hover:bg-blue-700 text-white"
                    >
                      <Search className="h-4 w-4 mr-2" />
                      Start Scanning
                    </Button>
                  </CardContent>
                </Card>
              ) : (
                allListings.map((listing) => (
                  <Card key={listing.id} className="hover:border-primary/50 transition-all duration-300">
                    <CardContent className="p-6">
                      <div className="flex items-start justify-between">
                        <div className="flex-1">
                          <div className="flex items-center space-x-3 mb-4">
                            <div className="p-2 bg-blue-500/20 rounded-lg">
                              {getProductTypeIcon(listing.productType)}
                            </div>
                            <div className="flex-1">
                              <h3 className="text-xl font-semibold mb-1">{listing.itemName}</h3>
                              <Badge 
                                variant="outline" 
                                className={`${
                                  listing.available 
                                    ? 'bg-green-500/20 text-green-600 border-green-500/50' 
                                    : 'bg-muted text-muted-foreground'
                                }`}
                              >
                                {getStatusText(listing.available)}
                              </Badge>
                            </div>
                          </div>
                          
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm mb-4">
                            <div className="bg-muted/50 p-3 rounded-lg">
                              <div className="text-muted-foreground text-xs uppercase tracking-wider">Set</div>
                              <div className="font-medium">{listing.set || 'Unknown'}</div>
                            </div>
                            <div className="bg-muted/50 p-3 rounded-lg">
                              <div className="text-muted-foreground text-xs uppercase tracking-wider">Type</div>
                              <div className="font-medium">{listing.productType || 'Unknown'}</div>
                            </div>
                            <div className="bg-muted/50 p-3 rounded-lg">
                              <div className="text-muted-foreground text-xs uppercase tracking-wider">Quantity</div>
                              <div className="font-medium">{listing.quantity} {listing.priceUnit}</div>
                            </div>
                            <div className="bg-muted/50 p-3 rounded-lg">
                              <div className="text-muted-foreground text-xs uppercase tracking-wider">Location</div>
                              <div className="font-medium">{listing.location || 'Unknown'}</div>
                            </div>
                          </div>

                          {listing.notes && (
                            <div className="bg-muted/30 p-3 rounded-lg mb-4">
                              <p className="text-sm">
                                {listing.notes}
                              </p>
                            </div>
                          )}

                          <div className="flex items-center justify-between">
                            <div className="text-xs text-muted-foreground">
                              Discovered: {listing.dateFound ? new Date(listing.dateFound).toLocaleString() : 'Unknown'}
                            </div>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => window.open(listing.marketplaceUrl, '_blank')}
                            >
                              <ExternalLink className="h-3 w-3 mr-1" />
                              View Listing
                            </Button>
                          </div>
                        </div>

                        <div className="text-right ml-6">
                          <div className="bg-gradient-to-r from-green-600 to-emerald-600 text-white px-4 py-2 rounded-lg mb-2">
                            <div className="text-3xl font-bold">
                              ${typeof listing.price === 'string' ? listing.price : listing.price?.toFixed(2) || '0.00'}
                            </div>
                          </div>
                          {listing.mainListingPrice && (
                            <div className="text-sm text-muted-foreground">
                              Listed: ${listing.mainListingPrice}
                            </div>
                          )}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))
              )}
            </div>
          </TabsContent>

          <TabsContent value="available">
            <div className="grid gap-6">
              {availableListings.length === 0 && !loading ? (
                <Card>
                  <CardContent className="p-8 text-center">
                    <div className="p-4 bg-green-500/20 rounded-full w-24 h-24 mx-auto mb-4 flex items-center justify-center">
                      <TrendingUp className="h-12 w-12 text-green-500" />
                    </div>
                    <h3 className="text-xl font-semibold mb-2">No available listings</h3>
                    <p className="mb-6 text-muted-foreground">Scan for listings to find available items</p>
                    <Button 
                      onClick={fetchListings} 
                      className="bg-green-600 hover:bg-green-700 text-white"
                    >
                      <Search className="h-4 w-4 mr-2" />
                      Refresh Data
                    </Button>
                  </CardContent>
                </Card>
              ) : (
                availableListings.map((listing) => (
                  <Card key={listing.id} className="hover:border-green-500/50 transition-all duration-300">
                    <CardContent className="p-6">
                      <div className="flex items-center justify-between">
                        <div className="flex-1">
                          <div className="flex items-center space-x-3 mb-3">
                            <div className="p-2 bg-green-500/20 rounded-lg">
                              <Package className="h-5 w-5 text-green-500" />
                            </div>
                            <div>
                              <h3 className="text-xl font-semibold">{listing.itemName}</h3>
                              <p className="text-muted-foreground">
                                {listing.set || 'Unknown Set'} • {listing.location || 'Unknown Location'}
                              </p>
                            </div>
                          </div>
                          <div className="bg-muted/50 p-3 rounded-lg">
                            <div className="text-muted-foreground text-xs uppercase tracking-wider mb-1">Available Quantity</div>
                            <div className="font-medium">{listing.quantity} {listing.priceUnit}</div>
                          </div>
                        </div>
                        <div className="text-right ml-6">
                          <div className="bg-gradient-to-r from-green-600 to-emerald-600 text-white px-6 py-3 rounded-lg mb-3">
                            <div className="text-2xl font-bold">
                              ${typeof listing.price === 'string' ? listing.price : listing.price?.toFixed(2) || '0.00'}
                            </div>
                          </div>
                          <Button 
                            size="sm" 
                            onClick={() => window.open(listing.marketplaceUrl, '_blank')}
                            className="bg-green-600 hover:bg-green-700 text-white w-full"
                          >
                            <ExternalLink className="h-3 w-3 mr-1" />
                            Purchase Now
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))
              )}
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}