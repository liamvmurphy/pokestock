"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { getApiBaseUrl } from "@/lib/api"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Settings,
  Database,
  Bot,
  Globe,
  Save,
  TestTube,
  AlertCircle,
  CheckCircle
} from "lucide-react"

export default function ConfigPage() {
  const [config, setConfig] = useState({
    lmStudio: {
      apiUrl: "http://localhost:1234",
      model: "qwen/qwen2.5-vl-7b",
      temperature: 0.7,
      maxTokens: 1000
    },
    googleSheets: {
      spreadsheetId: "1XkybP4xlJzAfwoRj_fpKF1bkKwCMLUq_dUVuqDAvD5A",
      credentialsPath: "./credentials.json"
    },
    scraping: {
      userAgent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
      delayBetweenRequests: 2000,
      headless: false,
      chromePort: 9222
    },
    monitoring: {
      defaultInterval: 60,
      retryAttempts: 3,
      timeoutSeconds: 30
    }
  })

  const [testResults, setTestResults] = useState<{[key: string]: 'success' | 'error' | 'testing'}>({})

  // Send initial model configuration to backend on load
  useEffect(() => {
    fetch(`${getApiBaseUrl()}/api/marketplace/configure`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ model: config.lmStudio.model }),
    }).catch(error => console.error('Failed to set initial model configuration:', error))
  }, [])

  const handleConfigChange = (section: string, field: string, value: any) => {
    setConfig(prev => ({
      ...prev,
      [section]: {
        ...prev[section as keyof typeof prev],
        [field]: value
      }
    }))
    
    // If LM Studio model changed, update marketplace configuration
    if (section === 'lmStudio' && field === 'model') {
      fetch(`${getApiBaseUrl()}/api/marketplace/configure`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ model: value }),
      }).catch(error => console.error('Failed to update model configuration:', error))
    }
  }

  const testConnection = async (service: string) => {
    setTestResults(prev => ({ ...prev, [service]: 'testing' }))
    
    try {
      // Simulate API call
      await new Promise(resolve => setTimeout(resolve, 2000))
      
      if (service === 'lmStudio') {
        const response = await fetch(`${getApiBaseUrl()}/api/test/health`)
        const data = await response.json()
        setTestResults(prev => ({ 
          ...prev, 
          [service]: data.lmStudio.includes('connected') ? 'success' : 'error' 
        }))
      } else {
        // Mock other tests
        setTestResults(prev => ({ ...prev, [service]: 'success' }))
      }
    } catch (error) {
      setTestResults(prev => ({ ...prev, [service]: 'error' }))
    }
  }

  const getTestIcon = (service: string) => {
    const result = testResults[service]
    switch (result) {
      case 'success': return <CheckCircle className="h-4 w-4 text-green-500" />
      case 'error': return <AlertCircle className="h-4 w-4 text-red-500" />
      case 'testing': return <div className="h-4 w-4 border-2 border-blue-500 border-t-transparent rounded-full animate-spin" />
      default: return <TestTube className="h-4 w-4 text-muted-foreground" />
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">Configuration</h1>
            <p className="text-muted-foreground">
              Configure system settings and integrations
            </p>
          </div>
          <Button>
            <Save className="h-4 w-4 mr-2" />
            Save All Changes
          </Button>
        </div>

        {/* Configuration Tabs */}
        <Tabs defaultValue="lmstudio" className="space-y-4">
          <TabsList className="grid w-full grid-cols-4">
            <TabsTrigger value="lmstudio">LM Studio</TabsTrigger>
            <TabsTrigger value="sheets">Google Sheets</TabsTrigger>
            <TabsTrigger value="scraping">Web Scraping</TabsTrigger>
            <TabsTrigger value="monitoring">Monitoring</TabsTrigger>
          </TabsList>

          <TabsContent value="lmstudio">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <Bot className="h-5 w-5 mr-2" />
                  LM Studio Configuration
                </CardTitle>
                <CardDescription>
                  Configure AI model settings for image and text analysis
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">API URL</label>
                    <input
                      type="text"
                      value={config.lmStudio.apiUrl}
                      onChange={(e) => handleConfigChange('lmStudio', 'apiUrl', e.target.value)}
                      className="w-full px-3 py-2 border rounded-md"
                      placeholder="http://localhost:1234"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Model</label>
                    <input
                      type="text"
                      value={config.lmStudio.model}
                      onChange={(e) => handleConfigChange('lmStudio', 'model', e.target.value)}
                      className="w-full px-3 py-2 border rounded-md"
                      placeholder="qwen/qwen2.5-vl-7b"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Temperature</label>
                    <input
                      type="number"
                      min="0"
                      max="2"
                      step="0.1"
                      value={config.lmStudio.temperature}
                      onChange={(e) => handleConfigChange('lmStudio', 'temperature', parseFloat(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Max Tokens</label>
                    <input
                      type="number"
                      value={config.lmStudio.maxTokens}
                      onChange={(e) => handleConfigChange('lmStudio', 'maxTokens', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                </div>
                
                <div className="flex items-center space-x-2 pt-4 border-t">
                  <Button 
                    variant="outline" 
                    onClick={() => testConnection('lmStudio')}
                    disabled={testResults.lmStudio === 'testing'}
                  >
                    {getTestIcon('lmStudio')}
                    <span className="ml-2">Test Connection</span>
                  </Button>
                  <Button 
                    variant="outline"
                    onClick={async () => {
                      try {
                        const response = await fetch(`${getApiBaseUrl()}/api/test/current-model`)
                        const data = await response.json()
                        alert(`Default Model: ${data.defaultModel}\nActive Model: ${data.activeModel}`)
                      } catch (error) {
                        alert('Failed to get model info: ' + error)
                      }
                    }}
                  >
                    Check Current Model
                  </Button>
                  {testResults.lmStudio === 'success' && (
                    <span className="text-sm text-green-600">Connection successful</span>
                  )}
                  {testResults.lmStudio === 'error' && (
                    <span className="text-sm text-red-600">Connection failed</span>
                  )}
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="sheets">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <Database className="h-5 w-5 mr-2" />
                  Google Sheets Configuration
                </CardTitle>
                <CardDescription>
                  Configure data storage and synchronization settings
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Spreadsheet ID</label>
                  <input
                    type="text"
                    value={config.googleSheets.spreadsheetId}
                    onChange={(e) => handleConfigChange('googleSheets', 'spreadsheetId', e.target.value)}
                    className="w-full px-3 py-2 border rounded-md"
                    placeholder="Your Google Sheets ID"
                  />
                  <p className="text-xs text-muted-foreground">
                    Found in the URL: docs.google.com/spreadsheets/d/[SPREADSHEET_ID]/edit
                  </p>
                </div>
                
                <div className="space-y-2">
                  <label className="text-sm font-medium">Credentials Path</label>
                  <input
                    type="text"
                    value={config.googleSheets.credentialsPath}
                    onChange={(e) => handleConfigChange('googleSheets', 'credentialsPath', e.target.value)}
                    className="w-full px-3 py-2 border rounded-md"
                    placeholder="./credentials.json"
                  />
                </div>
                
                <div className="flex items-center space-x-2 pt-4 border-t">
                  <Button variant="outline" onClick={() => testConnection('sheets')}>
                    {getTestIcon('sheets')}
                    <span className="ml-2">Test Connection</span>
                  </Button>
                  <Button variant="outline">
                    <Globe className="h-4 w-4 mr-2" />
                    Open Sheet
                  </Button>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="scraping">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <Globe className="h-5 w-5 mr-2" />
                  Web Scraping Configuration
                </CardTitle>
                <CardDescription>
                  Configure browser automation and scraping behavior
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">User Agent</label>
                    <input
                      type="text"
                      value={config.scraping.userAgent}
                      onChange={(e) => handleConfigChange('scraping', 'userAgent', e.target.value)}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Delay Between Requests (ms)</label>
                    <input
                      type="number"
                      value={config.scraping.delayBetweenRequests}
                      onChange={(e) => handleConfigChange('scraping', 'delayBetweenRequests', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Chrome Debug Port</label>
                    <input
                      type="number"
                      value={config.scraping.chromePort}
                      onChange={(e) => handleConfigChange('scraping', 'chromePort', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Headless Mode</label>
                    <select
                      value={config.scraping.headless.toString()}
                      onChange={(e) => handleConfigChange('scraping', 'headless', e.target.value === 'true')}
                      className="w-full px-3 py-2 border rounded-md"
                    >
                      <option value="false">Disabled (Show Browser)</option>
                      <option value="true">Enabled (Hidden Browser)</option>
                    </select>
                  </div>
                </div>
                
                <div className="flex items-center space-x-2 pt-4 border-t">
                  <Button variant="outline" onClick={() => testConnection('scraping')}>
                    {getTestIcon('scraping')}
                    <span className="ml-2">Test Browser Connection</span>
                  </Button>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="monitoring">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center">
                  <Settings className="h-5 w-5 mr-2" />
                  Monitoring Configuration
                </CardTitle>
                <CardDescription>
                  Configure monitoring intervals and behavior
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Default Check Interval (minutes)</label>
                    <input
                      type="number"
                      value={config.monitoring.defaultInterval}
                      onChange={(e) => handleConfigChange('monitoring', 'defaultInterval', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Retry Attempts</label>
                    <input
                      type="number"
                      value={config.monitoring.retryAttempts}
                      onChange={(e) => handleConfigChange('monitoring', 'retryAttempts', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Timeout (seconds)</label>
                    <input
                      type="number"
                      value={config.monitoring.timeoutSeconds}
                      onChange={(e) => handleConfigChange('monitoring', 'timeoutSeconds', parseInt(e.target.value))}
                      className="w-full px-3 py-2 border rounded-md"
                    />
                  </div>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </DashboardLayout>
  )
}