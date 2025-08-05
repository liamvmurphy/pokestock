"use client"

import { Card, CardContent, CardHeader, CardTitle } from "./card"
import { Badge } from "./badge"
import { Button } from "./button"
import { Wifi, Monitor, Smartphone, Copy } from "lucide-react"
import { getNetworkInfo } from "@/lib/api"

export function NetworkInfo() {
  const networkInfo = getNetworkInfo()

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
  }

  if (!networkInfo.isNetworkMode) {
    return null
  }

  return (
    <Card className="border-green-200 bg-green-50 dark:bg-green-900/20">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center text-sm">
          <Wifi className="h-4 w-4 mr-2 text-green-600" />
          Network Access Available
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Monitor className="h-4 w-4 text-blue-600" />
              <span className="text-sm font-medium">Frontend:</span>
            </div>
            <div className="flex items-center space-x-2">
              <code className="text-xs bg-white dark:bg-gray-800 px-2 py-1 rounded">
                {networkInfo.frontendUrl}
              </code>
              <Button
                size="sm"
                variant="outline"
                onClick={() => copyToClipboard(networkInfo.frontendUrl)}
              >
                <Copy className="h-3 w-3" />
              </Button>
            </div>
          </div>
          
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <Smartphone className="h-4 w-4 text-purple-600" />
              <span className="text-sm font-medium">Backend:</span>
            </div>
            <div className="flex items-center space-x-2">
              <code className="text-xs bg-white dark:bg-gray-800 px-2 py-1 rounded">
                {networkInfo.backendUrl}
              </code>
              <Button
                size="sm"
                variant="outline"
                onClick={() => copyToClipboard(networkInfo.backendUrl)}
              >
                <Copy className="h-3 w-3" />
              </Button>
            </div>
          </div>
        </div>
        
        <div className="pt-2 border-t">
          <p className="text-xs text-muted-foreground">
            🌐 Access from any device on your local network (IP: {networkInfo.hostIP})
          </p>
        </div>
      </CardContent>
    </Card>
  )
}