"use client"

import { useState, useEffect } from "react"
import { DashboardLayout } from "@/components/layout/dashboard-layout"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import {
  Activity,
  Clock,
  CheckCircle,
  AlertCircle,
  Zap,
  Database,
  Bot,
  Globe
} from "lucide-react"

interface SystemStatus {
  service: string
  status: 'online' | 'offline' | 'warning'
  lastCheck: string
  uptime: string
  details?: string
}

interface ActivityLog {
  id: string
  timestamp: string
  service: string
  action: string
  status: 'success' | 'error' | 'info'
  message: string
}

export default function StatusPage() {
  const [systemStatus, setSystemStatus] = useState<SystemStatus[]>([])
  const [activityLog, setActivityLog] = useState<ActivityLog[]>([])

  useEffect(() => {
    // Sample system status
    const status: SystemStatus[] = [
      {
        service: "Backend API",
        status: 'online',
        lastCheck: new Date().toISOString(),
        uptime: "99.9%",
        details: "All endpoints responding normally"
      },
      {
        service: "Google Sheets",
        status: 'online',
        lastCheck: new Date().toISOString(),
        uptime: "100%",
        details: "Connected and syncing data"
      },
      {
        service: "LM Studio",
        status: 'online',
        lastCheck: new Date().toISOString(),
        uptime: "98.5%",
        details: "Vision model ready"
      },
      {
        service: "Web Scraping",
        status: 'warning',
        lastCheck: new Date().toISOString(),
        uptime: "95.2%",
        details: "Some rate limiting detected"
      }
    ]

    // Sample activity log
    const activity: ActivityLog[] = [
      {
        id: "1",
        timestamp: new Date(Date.now() - 60000).toISOString(),
        service: "Marketplace Scraper",
        action: "New listing found",
        status: 'success',
        message: "Found Pokemon Stellar Crown Booster Box for $165"
      },
      {
        id: "2",
        timestamp: new Date(Date.now() - 180000).toISOString(),
        service: "Store Monitor",
        action: "Price change detected",
        status: 'info',
        message: "EB Games: Pokemon 151 ETB price dropped to $59.95"
      },
      {
        id: "3",
        timestamp: new Date(Date.now() - 300000).toISOString(),
        service: "LM Studio",
        action: "Image analysis",
        status: 'success',
        message: "Analyzed marketplace screenshot successfully"
      },
      {
        id: "4",
        timestamp: new Date(Date.now() - 600000).toISOString(),
        service: "Google Sheets",
        action: "Data sync",
        status: 'success',
        message: "Synced 15 new marketplace listings"
      },
      {
        id: "5",
        timestamp: new Date(Date.now() - 900000).toISOString(),
        service: "Web Scraping",
        action: "Rate limit warning",
        status: 'error',
        message: "Facebook temporarily limited requests - backing off"
      }
    ]

    setSystemStatus(status)
    setActivityLog(activity)

    // Simulate real-time updates
    const interval = setInterval(() => {
      const newActivity: ActivityLog = {
        id: Date.now().toString(),
        timestamp: new Date().toISOString(),
        service: "System",
        action: "Health check",
        status: 'success',
        message: "All systems operational"
      }
      setActivityLog(prev => [newActivity, ...prev.slice(0, 9)])
    }, 30000)

    return () => clearInterval(interval)
  }, [])

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'online': return <CheckCircle className="h-4 w-4 text-green-500" />
      case 'warning': return <AlertCircle className="h-4 w-4 text-yellow-500" />
      case 'offline': return <AlertCircle className="h-4 w-4 text-red-500" />
      default: return <Clock className="h-4 w-4 text-gray-500" />
    }
  }

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'online': return 'default'
      case 'warning': return 'secondary'
      case 'offline': return 'destructive'
      default: return 'outline'
    }
  }

  const getActivityIcon = (service: string) => {
    switch (service.toLowerCase()) {
      case 'lm studio': return <Bot className="h-4 w-4" />
      case 'google sheets': return <Database className="h-4 w-4" />
      case 'web scraping':
      case 'marketplace scraper': return <Globe className="h-4 w-4" />
      default: return <Activity className="h-4 w-4" />
    }
  }

  const getActivityStatusColor = (status: string) => {
    switch (status) {
      case 'success': return 'default'
      case 'error': return 'destructive'
      case 'info': return 'secondary'
      default: return 'outline'
    }
  }

  return (
    <DashboardLayout>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold">System Status</h1>
            <p className="text-muted-foreground">
              Real-time monitoring of all system components
            </p>
          </div>
          <div className="flex items-center space-x-2">
            <div className="h-2 w-2 bg-green-500 rounded-full animate-pulse" />
            <span className="text-sm text-muted-foreground">Live Updates</span>
          </div>
        </div>

        {/* System Status Cards */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {systemStatus.map((service) => (
            <Card key={service.service}>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{service.service}</CardTitle>
                {getStatusIcon(service.status)}
              </CardHeader>
              <CardContent>
                <div className="flex items-center space-x-2 mb-2">
                  <Badge variant={getStatusColor(service.status)}>
                    {service.status.toUpperCase()}
                  </Badge>
                  <span className="text-sm text-muted-foreground">
                    {service.uptime} uptime
                  </span>
                </div>
                <p className="text-xs text-muted-foreground">
                  {service.details}
                </p>
                <p className="text-xs text-muted-foreground mt-1">
                  Last check: {new Date(service.lastCheck).toLocaleString()}
                </p>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Activity Feed */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center">
              <Activity className="h-5 w-5 mr-2" />
              Live Activity Feed
            </CardTitle>
            <CardDescription>
              Real-time system events and notifications
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {activityLog.map((activity) => (
                <div key={activity.id} className="flex items-start space-x-3 p-3 border rounded-lg">
                  <div className="flex-shrink-0 mt-0.5">
                    {getActivityIcon(activity.service)}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <span className="text-sm font-medium">{activity.service}</span>
                      <Badge variant={getActivityStatusColor(activity.status)} className="text-xs">
                        {activity.action}
                      </Badge>
                      <span className="text-xs text-muted-foreground">
                        {new Date(activity.timestamp).toLocaleString()}
                      </span>
                    </div>
                    <p className="text-sm text-muted-foreground">
                      {activity.message}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Performance Metrics */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center">
                <Zap className="h-5 w-5 mr-2" />
                Response Times
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span>Backend API</span>
                  <span className="text-green-600">45ms</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>Google Sheets</span>
                  <span className="text-green-600">120ms</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>LM Studio</span>
                  <span className="text-yellow-600">2.3s</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center">
                <Database className="h-5 w-5 mr-2" />
                Data Statistics
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span>Marketplace Listings</span>
                  <span className="font-medium">1,247</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>Store Products</span>
                  <span className="font-medium">892</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>Analysis Jobs</span>
                  <span className="font-medium">156</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="flex items-center">
                <Globe className="h-5 w-5 mr-2" />
                Network Activity
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span>Requests Today</span>
                  <span className="font-medium">2,341</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>Success Rate</span>
                  <span className="text-green-600">98.2%</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span>Rate Limited</span>
                  <span className="text-yellow-600">12</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </DashboardLayout>
  )
}