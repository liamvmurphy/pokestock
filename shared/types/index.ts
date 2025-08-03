export interface PokemonItem {
  id: string
  name: string
  set: string
  productType: 'Booster Box' | 'Bundle' | 'Single Pack' | 'Singles' | 'Other'
  condition: string
  price: number
  pricePerPack?: number
  seller: string
  dateFound: Date
  marketplaceUrl: string
  marketplace: 'Facebook' | 'Store'
  imageUrl?: string
}

export interface StoreConfig {
  id: string
  name: string
  url: string
  selectors?: {
    products?: string
    price?: string
    stock?: string
  }
  enabled: boolean
  checkInterval: number // in minutes
}

export interface MonitoringSchedule {
  storeId: string
  lastCheck: Date
  nextCheck: Date
  status: 'pending' | 'running' | 'completed' | 'failed'
  error?: string
}

export interface ScrapingSession {
  id: string
  type: 'facebook' | 'store'
  startTime: Date
  endTime?: Date
  itemsFound: number
  status: 'running' | 'completed' | 'failed'
  error?: string
}

export interface LMStudioRequest {
  model: string
  messages: Array<{
    role: 'system' | 'user' | 'assistant'
    content: string | Array<{
      type: 'text' | 'image_url'
      text?: string
      image_url?: {
        url: string
      }
    }>
  }>
  temperature?: number
  max_tokens?: number
}

export interface LMStudioResponse {
  id: string
  object: string
  created: number
  model: string
  choices: Array<{
    index: number
    message: {
      role: string
      content: string
    }
    finish_reason: string
  }>
}