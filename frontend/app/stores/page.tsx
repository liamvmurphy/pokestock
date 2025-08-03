"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Store,
  Clock,
  TrendingUp,
  TrendingDown,
  AlertTriangle,
  CheckCircle,
  Eye,
  Settings,
  Play,
  Pause,
  RotateCcw
} from "lucide-react"

interface StoreConfig {
  id: string
  name: string
  url: string
  enabled: boolean
  checkInterval: number
  lastCheck?: string
  nextCheck?: string
  status: 'active' | 'paused' | 'error'
}

interface ProductStock {
  id: string
  name: string
  set: string
  price: number
  stockStatus: 'In Stock' | 'Out of Stock' | 'Limited Stock' | 'Preorder'
  availability: 'Online' | 'In-Store' | 'Both'
  lastChecked: string
  priceChange?: 'up' | 'down' | 'same'
  previousPrice?: number
}

export default function StoresPage() {
  const [stores, setStores] = useState<StoreConfig[]>([])
  const [inventory, setInventory] = useState<ProductStock[]>([])
  const [selectedStore, setSelectedStore] = useState<string | null>(null)

  useEffect(() => {
    // Sample store configurations
    const sampleStores: StoreConfig[] = [
      {
        id: "eb-games",
        name: "EB Games",
        url: "https://www.ebgames.com.au",
        enabled: true,
        checkInterval: 60,
        lastCheck: "2025-01-30T14:30:00Z",
        nextCheck: "2025-01-30T15:30:00Z",
        status: 'active'
      },
      {
        id: "jb-hifi",
        name: "JB Hi-Fi",
        url: "https://www.jbhifi.com.au",
        enabled: true,
        checkInterval: 120,
        lastCheck: "2025-01-30T13:45:00Z",
        nextCheck: "2025-01-30T15:45:00Z",
        status: 'active'
      },
      {
        id: "big-w",
        name: "Big W",
        url: "https://www.bigw.com.au",
        enabled: false,
        checkInterval: 180,
        status: 'paused'
      }
    ]

    const sampleInventory: ProductStock[] = [
      {
        id: "1",
        name: "Pokemon Stellar Crown Booster Box",
        set: "Stellar Crown",
        price: 199.95,
        stockStatus: 'In Stock',
        availability: 'Online',
        lastChecked: "2025-01-30T14:30:00Z",
        priceChange: 'down',
        previousPrice: 219.95
      },
      {
        id: "2",
        name: "Pokemon Paradox Rift Elite Trainer Box",
        set: "Paradox Rift",
        price: 69.95,
        stockStatus: 'Limited Stock',
        availability: 'Both',
        lastChecked: "2025-01-30T14:30:00Z",
        priceChange: 'same'
      },
      {
        id: "3",
        name: "Pokemon 151 Ultra Premium Collection",
        set: "Pokemon 151",
        price: 149.95,
        stockStatus: 'Out of Stock',
        availability: 'Online',
        lastChecked: "2025-01-30T14:30:00Z",
        priceChange: 'up',
        previousPrice: 139.95
      }
    ]

    setStores(sampleStores)
    setInventory(sampleInventory)
    setSelectedStore(sampleStores[0]?.id)
  }, [])

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case 'in stock': return 'default'
      case 'limited stock': return 'secondary'
      case 'out of stock': return 'destructive'
      case 'preorder': return 'outline'
      default: return 'secondary'
    }
  }

  const getStoreStatusIcon = (status: string) => {
    switch (status) {
      case 'active': return <CheckCircle className="h-4 w-4 text-green-500" />
      case 'paused': return <Pause className="h-4 w-4 text-yellow-500" />
      case 'error': return <AlertTriangle className="h-4 w-4 text-red-500" />
      default: return <Clock className="h-4 w-4 text-gray-500" />
    }
  }

  const getPriceChangeIcon = (change?: string) => {
    switch (change) {
      case 'up': return <TrendingUp className="h-3 w-3 text-red-500" />
      case 'down': return <TrendingDown className="h-3 w-3 text-green-500" />
      default: return null
    }
  }

  const toggleStoreStatus = (storeId: string) => {
    setStores(stores.map(store => 
      store.id === storeId 
        ? { ...store, enabled: !store.enabled, status: store.enabled ? 'paused' : 'active' }
        : store
    ))
  }

  const activeStores = stores.filter(s => s.enabled).length
  const totalProducts = inventory.length
  const inStockProducts = inventory.filter(p => p.stockStatus === 'In Stock').length

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Store Monitoring</h1>
            <p className="text-muted-foreground">
              Track Pokemon TCG inventory across retail stores
            </p>
          </div>
          <div className="flex space-x-2">
            <Button variant="outline">
              <Settings className="h-4 w-4 mr-2" />
              Configure
            </Button>
            <Button>
              <Play className="h-4 w-4 mr-2" />
              Start All
            </Button>
          </div>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Active Stores</CardTitle>
              <Store className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{activeStores}</div>
              <p className="text-xs text-muted-foreground">
                of {stores.length} configured
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Products Tracked</CardTitle>
              <Eye className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{totalProducts}</div>
              <p className="text-xs text-muted-foreground">
                Across all stores
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">In Stock</CardTitle>
              <CheckCircle className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold text-green-600">{inStockProducts}</div>
              <p className="text-xs text-muted-foreground">
                Available now
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">Last Update</CardTitle>
              <Clock className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">2min</div>
              <p className="text-xs text-muted-foreground">
                ago
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Main Content */}
        <Tabs defaultValue="inventory" className="space-y-4">
          <TabsList>
            <TabsTrigger value="inventory">Current Inventory</TabsTrigger>
            <TabsTrigger value="stores">Store Configuration</TabsTrigger>
            <TabsTrigger value="history">Price History</TabsTrigger>
          </TabsList>

          <TabsContent value="inventory" className="space-y-4">
            <div className="grid gap-4">
              {inventory.map((product) => (
                <Card key={product.id}>
                  <CardContent className="p-6">
                    <div className="flex items-start justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 mb-2">
                          <h3 className="text-lg font-semibold">{product.name}</h3>
                          <Badge variant={getStatusColor(product.stockStatus)}>
                            {product.stockStatus}
                          </Badge>
                        </div>
                        
                        <div className="grid grid-cols-2 md:grid-cols-3 gap-4 text-sm text-muted-foreground">
                          <div>
                            <span className="font-medium">Set:</span> {product.set}
                          </div>
                          <div>
                            <span className="font-medium">Availability:</span> {product.availability}
                          </div>
                          <div>
                            <span className="font-medium">Last Checked:</span> {new Date(product.lastChecked).toLocaleString()}
                          </div>
                        </div>
                      </div>

                      <div className="text-right ml-6">
                        <div className="flex items-center justify-end space-x-1">
                          <div className="text-2xl font-bold">
                            ${product.price}
                          </div>
                          {getPriceChangeIcon(product.priceChange)}
                        </div>
                        {product.previousPrice && product.priceChange !== 'same' && (
                          <div className="text-sm text-muted-foreground">
                            was ${product.previousPrice}
                          </div>
                        )}
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="stores" className="space-y-4">
            <div className="grid gap-4">
              {stores.map((store) => (
                <Card key={store.id}>
                  <CardContent className="p-6">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center space-x-4">
                        {getStoreStatusIcon(store.status)}
                        <div>
                          <h3 className="text-lg font-semibold">{store.name}</h3>
                          <p className="text-sm text-muted-foreground">{store.url}</p>
                          <div className="flex items-center space-x-4 mt-2 text-xs text-muted-foreground">
                            <span>Check every {store.checkInterval} minutes</span>
                            {store.lastCheck && (
                              <span>Last: {new Date(store.lastCheck).toLocaleString()}</span>
                            )}
                            {store.nextCheck && (
                              <span>Next: {new Date(store.nextCheck).toLocaleString()}</span>
                            )}
                          </div>
                        </div>
                      </div>
                      
                      <div className="flex items-center space-x-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => toggleStoreStatus(store.id)}
                        >
                          {store.enabled ? (
                            <>
                              <Pause className="h-3 w-3 mr-1" />
                              Pause
                            </>
                          ) : (
                            <>
                              <Play className="h-3 w-3 mr-1" />
                              Resume
                            </>
                          )}
                        </Button>
                        <Button variant="outline" size="sm">
                          <RotateCcw className="h-3 w-3 mr-1" />
                          Check Now
                        </Button>
                        <Button variant="outline" size="sm">
                          <Settings className="h-3 w-3 mr-1" />
                          Configure
                        </Button>
                      </div>
                    </div>
                  </CardContent>
                </Card>
              ))}
            </div>
          </TabsContent>

          <TabsContent value="history">
            <Card>
              <CardHeader>
                <CardTitle>Price History</CardTitle>
                <CardDescription>
                  Track price changes over time for monitored products
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="h-[400px] flex items-center justify-center text-muted-foreground">
                  Price history chart would be displayed here using Recharts
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}