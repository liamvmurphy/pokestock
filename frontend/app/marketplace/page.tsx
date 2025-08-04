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
  condition: string
  price: number
  pricePerPack?: number
  seller: string
  dateFound: string
  marketplaceUrl: string
  status: string
  description?: string
}

export default function MarketplacePage() {
  const [listings, setListings] = useState<MarketplaceListing[]>([])
  const [loading, setLoading] = useState(false)
  const [monitoring, setMonitoring] = useState(false)

  // Generate some sample data
  useEffect(() => {
    const sampleListings: MarketplaceListing[] = [
      {
        id: "1",
        itemName: "Pokemon Stellar Crown Booster Box",
        set: "Stellar Crown",
        productType: "Booster Box",
        condition: "New",
        price: 165.00,
        pricePerPack: 4.58,
        seller: "CardCollector123",
        dateFound: "2025-01-30T10:30:00Z",
        marketplaceUrl: "https://facebook.com/marketplace/item/123",
        status: "Available",
        description: "Brand new sealed booster box"
      },
      {
        id: "2",
        itemName: "Pokemon Paradox Rift Elite Trainer Box",
        set: "Paradox Rift",
        productType: "Elite Trainer Box",
        condition: "New",
        price: 55.00,
        seller: "PokemonMaster",
        dateFound: "2025-01-30T09:15:00Z",
        marketplaceUrl: "https://facebook.com/marketplace/item/456",
        status: "Available"
      },
      {
        id: "3",
        itemName: "Pokemon 151 Booster Bundle",
        set: "Pokemon 151",
        productType: "Bundle",
        condition: "Like New",
        price: 89.99,
        seller: "TCG_Deals",
        dateFound: "2025-01-30T08:45:00Z",
        marketplaceUrl: "https://facebook.com/marketplace/item/789",
        status: "Sold"
      }
    ]
    setListings(sampleListings)
  }, [])

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case 'available': return 'default'
      case 'sold': return 'secondary'
      case 'pending': return 'outline'
      default: return 'secondary'
    }
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
            <Button variant="outline">
              <Filter className="h-4 w-4 mr-2" />
              Filter
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
              <div className="text-2xl font-bold">{listings.length}</div>
              <p className="text-xs text-muted-foreground">
                +2 from last hour
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
                {listings.filter(l => l.status === 'Available').length}
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
                ${(listings.reduce((sum, l) => sum + l.price, 0) / listings.length || 0).toFixed(0)}
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
              <div className="text-2xl font-bold">5min</div>
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
              {listings.map((listing) => (
                <Card key={listing.id}>
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 mb-2">
                          {getProductTypeIcon(listing.productType)}
                          <h3 className="text-lg font-semibold">{listing.itemName}</h3>
                          <Badge variant={getStatusColor(listing.status)}>
                            {listing.status}
                          </Badge>
                        </div>
                        
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm text-muted-foreground">
                          <div>
                            <span className="font-medium">Set:</span> {listing.set}
                          </div>
                          <div>
                            <span className="font-medium">Type:</span> {listing.productType}
                          </div>
                          <div>
                            <span className="font-medium">Condition:</span> {listing.condition}
                          </div>
                          <div className="flex items-center">
                            <User className="h-3 w-3 mr-1" />
                            {listing.seller}
                          </div>
                        </div>

                        {listing.description && (
                          <p className="text-sm text-muted-foreground mt-2">
                            {listing.description}
                          </p>
                        )}

                        <div className="flex items-center justify-between mt-4">
                          <div className="text-xs text-muted-foreground">
                            Found: {new Date(listing.dateFound).toLocaleString()}
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
                          ${listing.price}
                        </div>
                        {listing.pricePerPack && (
                          <div className="text-sm text-muted-foreground">
                            ${listing.pricePerPack.toFixed(2)}/pack
                          </div>
                        )}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="available">
            <div className="grid gap-4">
              {listings
                .filter(listing => listing.status === 'Available')
                .map((listing) => (
                  <Card key={listing.id}>
                    <CardContent className="p-6">
                      <div className="flex items-center justify-between">
                        <div>
                          <h3 className="text-lg font-semibold">{listing.itemName}</h3>
                          <p className="text-muted-foreground">{listing.set} • {listing.seller}</p>
                        </div>
                        <div className="text-right">
                          <div className="text-xl font-bold text-green-600">${listing.price}</div>
                          <Button size="sm" className="mt-2">
                            <ExternalLink className="h-3 w-3 mr-1" />
                            View
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}