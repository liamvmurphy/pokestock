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
  Package
} from "lucide-react"

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
      
      const response = await fetch('http://localhost:8080/api/marketplace/listings')
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
      case 'booster box': return <Package className="h-4 w-4" />
      case 'bundle': return <Package className="h-4 w-4" />
      default: return <Package className="h-4 w-4" />
    }
  }

  const startMarketplaceMonitoring = async () => {
    setLoading(true)
    try {
      console.log('Starting marketplace monitoring...')
      const response = await fetch('http://localhost:8080/api/marketplace/start', {
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
    } catch (error) {
      alert(`Error starting marketplace monitoring: ${error.message}`)
      console.error('Error starting marketplace monitoring:', error)
    } finally {
      setLoading(false)
    }
  }

  const checkMonitoringStatus = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/marketplace/status')
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
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Facebook Marketplace</h1>
            <p className="text-muted-foreground">
              Monitor Pokemon TCG listings across Facebook Marketplace
            </p>
          </div>
          <div className="flex space-x-2">
            <Button variant="outline" onClick={fetchListings} disabled={loading}>
              <Search className="h-4 w-4 mr-2" />
              {loading ? 'Refreshing...' : 'Refresh'}
            </Button>
            <Button 
              onClick={startMarketplaceMonitoring}
              disabled={loading || monitoring}
            >
              <Search className="h-4 w-4 mr-2" />
              {loading ? 'Starting...' : monitoring ? 'Monitoring Active' : 'Start Scraping'}
            </Button>
          </div>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Total Listings</CardTitle>
              <Package className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{allListings.length}</div>
              <p className="text-xs text-muted-foreground">
                Total items found
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Available</CardTitle>
              <TrendingUp className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">
                {availableListings.length}
              </div>
              <p className="text-xs text-muted-foreground">
                Ready to purchase
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Avg Price</CardTitle>
              <DollarSign className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                ${allListings.length > 0 ? (allListings.reduce((sum, l) => {
                  const price = typeof l.price === 'string' ? parseFloat(l.price) || 0 : l.price || 0
                  return sum + price
                }, 0) / allListings.length).toFixed(0) : '0'}
              </div>
              <p className="text-xs text-muted-foreground">
                Across all listings
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Last Updated</CardTitle>
              <Calendar className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">
                {lastUpdate ? Math.floor((Date.now() - lastUpdate) / 60000) : 0}min
              </div>
              <p className="text-xs text-muted-foreground">
                ago
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Listings */}
        <Tabs defaultValue="all" className="space-y-4">
          <TabsList>
            <TabsTrigger value="all">All Listings</TabsTrigger>
            <TabsTrigger value="available">Available</TabsTrigger>
            <TabsTrigger value="watchlist">Watchlist</TabsTrigger>
          </TabsList>

          <TabsContent value="all" className="space-y-4">
            <div className="grid gap-4">
              {allListings.length === 0 && !loading ? (
                <Card>
                  <CardContent className="p-6 text-center">
                    <div className="text-muted-foreground">
                      <Package className="h-12 w-12 mx-auto mb-4 opacity-50" />
                      <h3 className="text-lg font-semibold mb-2">No listings found</h3>
                      <p className="mb-4">No marketplace listings are currently available.</p>
                      <Button onClick={fetchListings} variant="outline">
                        <Search className="h-4 w-4 mr-2" />
                        Refresh Listings
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ) : (
                allListings.map((listing) => (
                <Card key={listing.id}>
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 mb-2">
                          {getProductTypeIcon(listing.productType)}
                          <h3 className="text-lg font-semibold">{listing.itemName}</h3>
                          <Badge variant={getStatusColor(listing.available)}>
                            {getStatusText(listing.available)}
                          </Badge>
                        </div>
                        
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm text-muted-foreground">
                          <div>
                            <span className="font-medium">Set:</span> {listing.set || 'Unknown'}
                          </div>
                          <div>
                            <span className="font-medium">Type:</span> {listing.productType || 'Unknown'}
                          </div>
                          <div>
                            <span className="font-medium">Quantity:</span> {listing.quantity} {listing.priceUnit}
                          </div>
                          <div>
                            <span className="font-medium">Location:</span> {listing.location || 'Unknown'}
                          </div>
                        </div>

                        {listing.notes && (
                          <p className="text-sm text-muted-foreground mt-2">
                            {listing.notes}
                          </p>
                        )}

                        <div className="flex items-center justify-between mt-4">
                          <div className="text-xs text-muted-foreground">
                            Found: {listing.dateFound ? new Date(listing.dateFound).toLocaleString() : 'Unknown'}
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
                        <div className="text-2xl font-bold text-green-600">
                          ${typeof listing.price === 'string' ? listing.price : listing.price?.toFixed(2) || '0.00'}
                        </div>
                        {listing.mainListingPrice && (
                          <div className="text-sm text-muted-foreground">
                            Main: ${listing.mainListingPrice}
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
            <div className="grid gap-4">
              {availableListings.length === 0 && !loading ? (
                <Card>
                  <CardContent className="p-6 text-center">
                    <div className="text-muted-foreground">
                      <TrendingUp className="h-12 w-12 mx-auto mb-4 opacity-50" />
                      <h3 className="text-lg font-semibold mb-2">No available listings</h3>
                      <p className="mb-4">No available marketplace listings found.</p>
                      <Button onClick={fetchListings} variant="outline">
                        <Search className="h-4 w-4 mr-2" />
                        Refresh Listings
                      </Button>
                    </div>
                  </CardContent>
                </Card>
              ) : (
                availableListings.map((listing) => (
                <Card key={listing.id}>
                  <CardContent className="p-6">
                    <div className="flex items-center justify-between">
                      <div>
                        <h3 className="text-lg font-semibold">{listing.itemName}</h3>
                        <p className="text-muted-foreground">
                          {listing.set || 'Unknown Set'} • {listing.location || 'Unknown Location'}
                        </p>
                        <div className="text-sm text-muted-foreground mt-1">
                          Quantity: {listing.quantity} {listing.priceUnit}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-xl font-bold text-green-600">
                          ${typeof listing.price === 'string' ? listing.price : listing.price?.toFixed(2) || '0.00'}
                        </div>
                        <Button 
                          size="sm" 
                          className="mt-2"
                          onClick={() => window.open(listing.marketplaceUrl, '_blank')}
                        >
                          <ExternalLink className="h-3 w-3 mr-1" />
                          View
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