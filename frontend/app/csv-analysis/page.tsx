"use client"

import React, { useState } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Progress } from "@/components/ui/progress"
import { Alert, AlertDescription } from "@/components/ui/alert"
import { MarkdownRenderer } from "@/components/ui/markdown-renderer"
import { apiEndpoints } from "@/lib/api"
import {
  Upload,
  FileText,
  Download,
  Star,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  BarChart3,
  Package,
  DollarSign,
  Target,
  Zap,
  Crown,
  Sparkles,
  Activity
} from "lucide-react"

interface AnalysisResult {
  success: boolean
  fileName?: string
  fileSize?: number
  analysis?: {
    totalRows: number
    statistics: any
    topDeals: any[]
    productsByType: any
    analyzedProducts: any[]
    analysisTimestamp: string
  }
  // For master prompt analysis
  timestamp?: string
  filename?: string
}

export default function CSVAnalysisPage() {
  const [analyzing, setAnalyzing] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [comprehensiveAnalysis, setComprehensiveAnalysis] = useState<string | null>(null)
  const [marketplaceDataCount, setMarketplaceDataCount] = useState<number>(0)
  const [lastAnalysisTime, setLastAnalysisTime] = useState<string | null>(null)
  const [loadingLastReport, setLoadingLastReport] = useState(false)
  const [hasGoogleSheets, setHasGoogleSheets] = useState(false)
  const [preloadedReport, setPreloadedReport] = useState<boolean>(false)
  const [hasCheckedForReports, setHasCheckedForReports] = useState(false)

  // Check marketplace data count on component mount
  const checkMarketplaceData = async () => {
    try {
      const response = await fetch(apiEndpoints.marketplace.listings)
      const result = await response.json()
      
      if (result.allListings) {
        setMarketplaceDataCount(result.allListings.length)
      }
    } catch (err) {
      console.error('Error checking marketplace data:', err)
    }
  }

  // Check Google Sheets status and capabilities
  const checkGoogleSheetsStatus = async () => {
    try {
      const response = await fetch(apiEndpoints.csv.googleSheetsInfo)
      const result = await response.json()
      
      if (result.success && result.available) {
        setHasGoogleSheets(true)
        console.log('Google Sheets integration available:', result.spreadsheetUrl)
      } else {
        setHasGoogleSheets(false)
        console.log('Google Sheets integration not available:', result.error)
      }
    } catch (err) {
      console.error('Error checking Google Sheets status:', err)
      setHasGoogleSheets(false)
    }
  }

  // Load the last saved report from Google Sheets
  const loadLastReport = async () => {
    if (!hasGoogleSheets || hasCheckedForReports) return
    
    setLoadingLastReport(true)
    setHasCheckedForReports(true) // Prevent multiple calls
    
    try {
      const response = await fetch(apiEndpoints.csv.lastReport)
      const result = await response.json()
      
      if (result.success && result.report) {
        setComprehensiveAnalysis(result.report.content)
        setPreloadedReport(true)
        
        // Extract timestamp from filename if available
        const fileName = result.report.sheetName || ''
        const timestamp = result.report.createdTime ? 
          new Date(result.report.createdTime).toLocaleString() : 
          'Recently'
        setLastAnalysisTime(timestamp)
        
        console.log('Successfully loaded last report from Google Sheets:', fileName)
      } else {
        console.log('No previous report found in Google Sheets:', result.error)
      }
    } catch (err) {
      console.error('Error loading last report:', err)
    } finally {
      setLoadingLastReport(false)
    }
  }

  const handleAutoAnalysis = async () => {
    setAnalyzing(true)
    setError(null)
    setComprehensiveAnalysis(null)
    setPreloadedReport(false)
    setHasCheckedForReports(false) // Allow checking for reports again after new analysis

    try {
      const response = await fetch(apiEndpoints.csv.autoAnalyze, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      })

      const result = await response.json()

      if (result.success) {
        setComprehensiveAnalysis(result.analysis)
        setLastAnalysisTime(new Date().toLocaleString())
        console.log('Auto-analysis completed')
      } else {
        setError(result.error || 'Analysis failed')
      }
    } catch (err: any) {
      setError('Error analyzing marketplace data: ' + err.message)
      console.error('Analysis error:', err)
    } finally {
      setAnalyzing(false)
    }
  }

  // Load marketplace data count on component mount
  React.useEffect(() => {
    const initializeData = async () => {
      await checkMarketplaceData()
      await checkGoogleSheetsStatus()
    }
    initializeData()
  }, [])

  // Load last report when Google Sheets is available and no analysis is currently shown
  React.useEffect(() => {
    if (hasGoogleSheets && !comprehensiveAnalysis && !loadingLastReport && !hasCheckedForReports) {
      // Add a small delay to prevent rapid-fire calls
      const timeoutId = setTimeout(() => {
        loadLastReport()
      }, 500)
      
      return () => clearTimeout(timeoutId)
    }
  }, [hasGoogleSheets, comprehensiveAnalysis, loadingLastReport, hasCheckedForReports])


  return (
    <DashboardLayout>
      <div className="space-y-8 p-6">
        {/* Header */}
        <div className="flex items-center justify-between bg-gradient-to-r from-purple-600/10 to-blue-600/10 rounded-2xl p-6 border border-purple-500/20 dark:from-purple-900/20 dark:to-blue-900/20 dark:border-purple-800/30">
          <div className="space-y-2">
            <div className="flex items-center space-x-3">
              <div className="p-2 bg-purple-600 rounded-lg">
                <BarChart3 className="h-6 w-6 text-white" />
              </div>
              <h1 className="text-4xl font-bold bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent">
                Pokemon TCG CSV Analyzer
              </h1>
            </div>
            <p className="text-muted-foreground text-lg">
              Upload your marketplace CSV for AI-powered analysis using Claude 3.5 Sonnet
            </p>
          </div>
        </div>

        {/* Loading last report indicator */}
        {loadingLastReport && (
          <Card className="border-blue-200 bg-blue-50 dark:bg-blue-900/20">
            <CardContent className="p-4">
              <div className="flex items-center space-x-3">
                <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-blue-600"></div>
                <div>
                  <p className="text-sm font-medium text-blue-800 dark:text-blue-200">
                    Loading last market intelligence report from Google Sheets...
                  </p>
                  <p className="text-xs text-blue-600 dark:text-blue-300">
                    📄 Checking for your most recent analysis
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        )}

        {/* Auto-Analysis Section */}
        {!comprehensiveAnalysis && !loadingLastReport && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center">
                <Crown className="h-5 w-5 mr-2 text-purple-600" />
                Marketplace Intelligence Analysis
              </CardTitle>
              <CardDescription>
                Automatically analyze your marketplace scanner data with AI-powered market intelligence
                {hasGoogleSheets && (
                  <span className="block mt-1 text-green-600 dark:text-green-400 text-xs">
                    ✅ Google Sheets integration active - reports will be automatically saved as new tabs
                  </span>
                )}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
              {error && (
                <Alert variant="destructive">
                  <AlertTriangle className="h-4 w-4" />
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}

              {/* Data Status */}
              <div className="bg-gradient-to-r from-blue-50 to-purple-50 border border-purple-200 rounded-lg p-6">
                <div className="flex items-center justify-between">
                  <div className="space-y-2">
                    <div className="flex items-center space-x-3">
                      <div className="p-2 bg-purple-600 rounded-lg">
                        <Package className="h-5 w-5 text-white" />
                      </div>
                      <div>
                        <h3 className="text-lg font-semibold text-purple-800">
                          Marketplace Data Ready
                        </h3>
                        <p className="text-purple-600">
                          {marketplaceDataCount} listings available for analysis
                        </p>
                      </div>
                    </div>
                    {lastAnalysisTime && (
                      <p className="text-sm text-muted-foreground ml-12">
                        Last analyzed: {lastAnalysisTime}
                      </p>
                    )}
                  </div>
                  
                  <div className="flex space-x-2">
                    <Button
                      variant="outline"
                      onClick={checkMarketplaceData}
                      disabled={analyzing}
                    >
                      <Activity className="h-4 w-4 mr-2" />
                      Refresh Data
                    </Button>
                    <Button
                      onClick={handleAutoAnalysis}
                      disabled={analyzing || marketplaceDataCount === 0}
                      className="bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-700 hover:to-blue-700 text-white"
                    >
                      {analyzing ? (
                        <>
                          <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white mr-2"></div>
                          Analyzing...
                        </>
                      ) : (
                        <>
                          <Zap className="h-4 w-4 mr-2" />
                          Generate Market Intelligence
                        </>
                      )}
                    </Button>
                  </div>
                </div>
                
                {analyzing && (
                  <div className="mt-6">
                    <Progress value={75} className="w-full" />
                    <p className="text-sm text-purple-600 mt-2 flex items-center">
                      <Sparkles className="h-4 w-4 mr-2" />
                      🤖 Claude AI is analyzing {marketplaceDataCount} marketplace listings and generating your comprehensive investment report...
                    </p>
                  </div>
                )}

                {marketplaceDataCount === 0 && (
                  <div className="mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
                    <div className="flex items-start space-x-3">
                      <AlertTriangle className="h-5 w-5 text-yellow-600 mt-0.5" />
                      <div>
                        <h4 className="font-medium text-yellow-800">No marketplace data found</h4>
                        <p className="text-sm text-yellow-700 mt-1">
                          Please run the marketplace scanner first to collect Pokemon TCG listings.
                        </p>
                        <Button 
                          variant="outline" 
                          size="sm" 
                          className="mt-2"
                          onClick={() => window.open('/marketplace', '_blank')}
                        >
                          <Target className="h-4 w-4 mr-2" />
                          Go to Marketplace Scanner
                        </Button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        )}

        {/* Comprehensive Analysis Results */}
        {comprehensiveAnalysis && (
          <div className="space-y-6">
            {/* Header Section */}
            <div className="bg-gradient-to-r from-purple-600/10 to-blue-600/10 rounded-2xl p-6 border border-purple-500/20 dark:from-purple-900/20 dark:to-blue-900/20 dark:border-purple-800/30">
              <div className="flex items-center justify-between">
                <div className="space-y-2">
                  <div className="flex items-center space-x-3">
                    <div className="p-2 bg-gradient-to-r from-purple-600 to-blue-600 rounded-lg">
                      <Crown className="h-6 w-6 text-white" />
                    </div>
                    <div className="flex items-center space-x-3">
                      <h1 className="text-3xl font-bold bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent">
                        Pokemon TCG Market Intelligence Report
                      </h1>
                      {preloadedReport && (
                        <Badge variant="secondary" className="bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200">
                          📄 From Google Sheets
                        </Badge>
                      )}
                    </div>
                  </div>
                  <p className="text-muted-foreground text-lg flex items-center">
                    <Sparkles className="h-5 w-5 mr-2 text-purple-600" />
                    Comprehensive analysis powered by Claude AI
                    {preloadedReport && lastAnalysisTime && (
                      <span className="ml-2 text-sm text-green-600 dark:text-green-400">
                        • Generated: {lastAnalysisTime}
                      </span>
                    )}
                  </p>
                </div>
                <div className="flex space-x-2">
                  <Button
                    variant="outline"
                    onClick={() => {
                      setComprehensiveAnalysis(null)
                      setPreloadedReport(false)
                      setLastAnalysisTime(null)
                      setHasCheckedForReports(false) // Allow checking again
                      checkMarketplaceData()
                    }}
                  >
                    <Upload className="h-4 w-4 mr-2" />
                    New Analysis
                  </Button>
                </div>
              </div>
            </div>

            {/* Analysis Content */}
            <Card className="border-0 shadow-xl">
              <CardContent className="p-0">
                <div className="max-w-none">
                  <MarkdownRenderer content={comprehensiveAnalysis} />
                </div>
              </CardContent>
            </Card>
          </div>
        )}

      </div>
    </DashboardLayout>
  )
}