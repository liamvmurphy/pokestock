"use client"

import { useEffect, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import {
  ShoppingCart,
  Store,
  TrendingUp,
  Activity,
  ExternalLink,
  RefreshCw,
  AlertCircle,
  CheckCircle,
  Clock
} from "lucide-react"

interface SystemStatus {
  status: string
  googleSheets: string
  lmStudio: string
}

interface MarketplaceListing {
  itemName: string
  set: string
  price: number
  seller: string
  dateFound: string
  status: string
}

export default function Dashboard() {
  const [systemStatus, setSystemStatus] = useState<SystemStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [recentListings, setRecentListings] = useState<MarketplaceListing[]>([])
  const [availableModels, setAvailableModels] = useState<any[]>([])
  const [selectedModel, setSelectedModel] = useState<string>("")
  const [testImage, setTestImage] = useState<File | null>(null)

  const fetchStatus = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/test/health')
      const data = await response.json()
      setSystemStatus(data)
    } catch (error) {
      console.error('Failed to fetch status:', error)
    } finally {
      setLoading(false)
    }
  }

  const fetchModels = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/test/models')
      const data = await response.json()
      setAvailableModels(data)
      // Set default model if available
      if (data.length > 0 && !selectedModel) {
        setSelectedModel(data[0].id)
      }
    } catch (error) {
      console.error('Failed to fetch models:', error)
    }
  }

  useEffect(() => {
    fetchStatus()
    fetchModels()
    const interval = setInterval(fetchStatus, 30000) // Refresh every 30 seconds
    return () => clearInterval(interval)
  }, [])

  const handleTestMarketplace = async () => {
    try {
      const testData = {
        itemName: `Test Item ${Date.now()}`,
        set: "Stellar Crown",
        price: (Math.random() * 100 + 50).toFixed(2)
      }
      
      const response = await fetch('http://localhost:8080/api/test/test-marketplace', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(testData)
      })
      
      const result = await response.json()
      console.log('Test result:', result)
      
      // Add to recent listings
      setRecentListings(prev => [{
        ...testData,
        price: parseFloat(testData.price),
        seller: "Test Seller",
        dateFound: new Date().toISOString(),
        status: "New"
      }, ...prev.slice(0, 4)])
      
    } catch (error) {
      console.error('Test failed:', error)
    }
  }

  const handleTestRealScraping = async () => {
    try {
      console.log('Starting scalable marketplace scraping...')
      
      const response = await fetch('http://localhost:8080/api/marketplace/scrape', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          searchTerm: "Pokemon ETB",
          maxItems: 15
        })
      })
      
      const result = await response.json()
      console.log('Scalable scraping result:', result)
      
      if (result.status === 'completed') {
        alert(`✅ Scraping completed!\n\n` +
              `Search Term: ${result.searchTerm}\n` +
              `Items Found: ${result.itemsFound}\n` +
              `JSON file saved to output/marketplace/\n\n` +
              `Check console for full results.`)
      } else {
        alert(`❌ Scraping failed: ${result.error || 'Unknown error'}`)
      }
      
    } catch (error) {
      console.error('Scalable scraping failed:', error)
      alert('Scalable scraping failed: ' + error.message)
    }
  }

  const handleTestLMStudio = async () => {
    if (!testImage) {
      alert('Please select an image file first')
      return
    }

    if (!selectedModel) {
      alert('Please select a model first')
      return
    }

    try {
      console.log('Testing LM Studio with model:', selectedModel)
      
      const formData = new FormData()
      formData.append('image', testImage)
      formData.append('type', 'marketplace')
      formData.append('description', 'Facebook Marketplace listing. Analyze this screenshot.')
      formData.append('model', selectedModel)
      
      const response = await fetch('http://localhost:8080/api/test/analyze-image', {
        method: 'POST',
        body: formData
      })
      
      const result = await response.json()
      console.log('LM Studio analysis result:', result)
      
      // Pretty print the result
      alert(`LM Studio Analysis Result:\n\n${JSON.stringify(result, null, 2)}`)
      
    } catch (error) {
      console.error('LM Studio test failed:', error)
      alert('LM Studio test failed: ' + error.message)
    }
  }

  const getStatusIcon = (status: string) => {
    if (status === "OK" || status.includes("connected successfully")) {
      return <CheckCircle className="h-4 w-4 text-green-500" />
    }
    return <AlertCircle className="h-4 w-4 text-red-500" />
  }

  const getStatusColor = (status: string) => {
    if (status === "OK" || status.includes("connected successfully")) {
      return "text-green-600"
    }
    return "text-red-600"
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold">Pokemon TCG Tracker</h1>
          <p className="text-muted-foreground">
            Monitor marketplace listings and store inventory in real-time
          </p>
        </div>
        <Button onClick={fetchStatus} variant="outline" size="sm">
          <RefreshCw className="h-4 w-4 mr-2" />
          Refresh
        </Button>
      </div>

      {/* Status Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">System Status</CardTitle>
            {systemStatus && getStatusIcon(systemStatus.status)}
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {loading ? "Loading..." : systemStatus?.status || "Unknown"}
            </div>
            <p className="text-xs text-muted-foreground">
              Backend services operational
            </p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Google Sheets</CardTitle>
            {systemStatus && getStatusIcon("OK")}
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-green-600">Connected</div>
            <p className="text-xs text-muted-foreground">
              Data storage active
            </p>
            {systemStatus?.googleSheets && (
              <Button
                variant="outline"
                size="sm"
                className="mt-2"
                onClick={() => window.open(systemStatus.googleSheets, '_blank')}
              >
                <ExternalLink className="h-3 w-3 mr-1" />
                View Sheet
              </Button>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">LM Studio</CardTitle>
            {systemStatus && getStatusIcon(systemStatus.lmStudio)}
          </CardHeader>
          <CardContent>
            <div className={`text-2xl font-bold ${systemStatus ? getStatusColor(systemStatus.lmStudio) : ''}`}>
              {loading ? "Loading..." : 
               systemStatus?.lmStudio.includes("connected") ? "Connected" : "Disconnected"}
            </div>
            <p className="text-xs text-muted-foreground">
              AI analysis ready
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Main Dashboard */}
      <Tabs defaultValue="overview" className="space-y-4">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="marketplace">Marketplace</TabsTrigger>
          <TabsTrigger value="stores">Store Monitoring</TabsTrigger>
          <TabsTrigger value="testing">Testing</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <ShoppingCart className="h-5 w-5 mr-2" />
                  Recent Marketplace Finds
                </CardTitle>
                <CardDescription>
                  Latest Pokemon TCG listings discovered
                </CardDescription>
              </CardHeader>
              <CardContent>
                {recentListings.length === 0 ? (
                  <p className="text-muted-foreground text-center py-4">
                    No recent listings found
                  </p>
                ) : (
                  <div className="space-y-3">
                    {recentListings.map((listing, index) => (
                      <div key={index} className="flex items-center justify-between p-3 border rounded-lg">
                        <div>
                          <p className="font-medium">{listing.itemName}</p>
                          <p className="text-sm text-muted-foreground">
                            {listing.set} • {listing.seller}
                          </p>
                        </div>
                        <div className="text-right">
                          <p className="font-bold">${listing.price}</p>
                          <Badge variant="secondary">
                            {listing.status}
                          </Badge>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <Store className="h-5 w-5 mr-2" />
                  Store Monitoring
                </CardTitle>
                <CardDescription>
                  Inventory tracking across retail stores
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <div className="flex items-center justify-between p-3 border rounded-lg">
                    <div>
                      <p className="font-medium">EB Games</p>
                      <p className="text-sm text-muted-foreground">Last checked: 2 hours ago</p>
                    </div>
                    <Badge variant="outline">
                      <Clock className="h-3 w-3 mr-1" />
                      Scheduled
                    </Badge>
                  </div>
                  
                  <div className="flex items-center justify-between p-3 border rounded-lg">
                    <div>
                      <p className="font-medium">JB Hi-Fi</p>
                      <p className="text-sm text-muted-foreground">Last checked: 4 hours ago</p>
                    </div>
                    <Badge variant="outline">
                      <Activity className="h-3 w-3 mr-1" />
                      Monitoring
                    </Badge>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>

        <TabsContent value="testing" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>System Testing</CardTitle>
              <CardDescription>
                Test the various components of the TCG Tracker system
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* LM Studio Model Selection */}
              <div className="space-y-2">
                <Label htmlFor="model-select">LM Studio Model</Label>
                <Select value={selectedModel} onValueChange={setSelectedModel}>
                  <SelectTrigger id="model-select">
                    <SelectValue placeholder="Select a model" />
                  </SelectTrigger>
                  <SelectContent>
                    {availableModels.length === 0 ? (
                      <SelectItem value="none" disabled>No models available</SelectItem>
                    ) : (
                      availableModels.map((model) => (
                        <SelectItem key={model.id} value={model.id}>
                          {model.id}
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
                {availableModels.length > 0 && (
                  <p className="text-sm text-muted-foreground">
                    {availableModels.length} model{availableModels.length !== 1 ? 's' : ''} available
                  </p>
                )}
              </div>

              {/* Image Upload for Testing */}
              <div className="space-y-2">
                <Label htmlFor="test-image">Test Image (for LM Studio Analysis)</Label>
                <Input
                  id="test-image"
                  type="file"
                  accept="image/*"
                  onChange={(e) => setTestImage(e.target.files?.[0] || null)}
                />
                {testImage && (
                  <p className="text-sm text-muted-foreground">
                    Selected: {testImage.name}
                  </p>
                )}
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Button onClick={handleTestMarketplace} className="w-full">
                  <ShoppingCart className="h-4 w-4 mr-2" />
                  Test Marketplace Integration
                </Button>
                
                <Button onClick={handleTestRealScraping} variant="default" className="w-full bg-blue-600 hover:bg-blue-700">
                  <ShoppingCart className="h-4 w-4 mr-2" />
                  Test Real Scraping
                </Button>
                
                <Button variant="outline" className="w-full">
                  <Store className="h-4 w-4 mr-2" />
                  Test Store Monitoring
                </Button>
                
                <Button 
                  onClick={handleTestLMStudio} 
                  variant="outline" 
                  className="w-full"
                  disabled={!testImage || !selectedModel}
                >
                  <Activity className="h-4 w-4 mr-2" />
                  Test LM Studio Analysis
                </Button>
                
                <Button variant="outline" className="w-full">
                  <TrendingUp className="h-4 w-4 mr-2" />
                  Generate Test Data
                </Button>
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}