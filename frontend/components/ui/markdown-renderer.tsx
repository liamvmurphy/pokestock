"use client"

import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { Badge } from './badge'
import { Card, CardContent, CardHeader, CardTitle } from './card'
import { Button } from './button'
import { 
  Star, 
  TrendingUp, 
  AlertTriangle, 
  DollarSign, 
  ExternalLink,
  Crown,
  Target,
  ShieldCheck,
  Zap
} from 'lucide-react'

interface MarkdownRendererProps {
  content: string
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const getActionColor = (action: string) => {
    if (action.includes('IMMEDIATE')) return 'text-red-600 bg-red-100 border-red-300 animate-pulse'
    if (action.includes('SOON')) return 'text-orange-600 bg-orange-100 border-orange-300'
    if (action.includes('MONITOR')) return 'text-blue-600 bg-blue-100 border-blue-300'
    return 'text-gray-600 bg-gray-100 border-gray-300'
  }

  const getInvestmentColor = (grade: string) => {
    if (grade.includes('EXCEPTIONAL')) return 'text-green-600 bg-green-100 border-green-300'
    if (grade.includes('GREAT')) return 'text-blue-600 bg-blue-100 border-blue-300'
    if (grade.includes('GOOD')) return 'text-cyan-600 bg-cyan-100 border-cyan-300'
    if (grade.includes('FAIR')) return 'text-yellow-600 bg-yellow-100 border-yellow-300'
    return 'text-red-600 bg-red-100 border-red-300'
  }

  const getDealScoreStars = (score: string) => {
    const numScore = parseInt(score) || 0
    return Array(5).fill(0).map((_, i) => (
      <Star 
        key={i} 
        className={`h-4 w-4 ${i < numScore ? 'text-yellow-500 fill-current' : 'text-gray-300'}`} 
      />
    ))
  }

  return (
    <div className="space-y-6">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => (
            <div className="bg-gradient-to-r from-purple-600 to-blue-600 text-white p-6 rounded-lg mb-6">
              <h1 className="text-3xl font-bold flex items-center">
                <Crown className="h-8 w-8 mr-3" />
                {children}
              </h1>
            </div>
          ),
          h2: ({ children }) => {
            const childText = children?.toString() || ''
            
            // Check if this is a recommendation item (contains a number and product name)
            if (childText.match(/^\d+\./)) {
              const parts = childText.split('.')
              const rank = parts[0]
              const productName = parts.slice(1).join('.').trim()
              
              return (
                <Card className="mt-6 border-2 border-purple-200 hover:border-purple-400 transition-all duration-300">
                  <CardHeader className="bg-gradient-to-r from-purple-50 to-blue-50 border-b">
                    <CardTitle className="flex items-center justify-between">
                      <div className="flex items-center space-x-3">
                        <Badge className="bg-purple-600 text-white text-lg px-3 py-1">
                          #{rank}
                        </Badge>
                        <span className="text-xl font-bold text-purple-800">{productName}</span>
                      </div>
                      <div className="flex items-center space-x-1">
                        {getDealScoreStars('5')}
                      </div>
                    </CardTitle>
                  </CardHeader>
                </Card>
              )
            }
            
            return (
              <h2 className="text-2xl font-bold text-gray-800 dark:text-gray-200 mt-8 mb-4 flex items-center">
                <Target className="h-6 w-6 mr-2 text-blue-600" />
                {children}
              </h2>
            )
          },
          h3: ({ children }) => (
            <h3 className="text-xl font-semibold text-gray-700 dark:text-gray-300 mt-6 mb-3 flex items-center">
              <Zap className="h-5 w-5 mr-2 text-purple-600" />
              {children}
            </h3>
          ),
          p: ({ children }) => {
            const childText = children?.toString() || ''
            
            // Check for special formatted lines
            if (childText.startsWith('**Set:**') || 
                childText.startsWith('**Price:**') || 
                childText.startsWith('**Deal Score:**') ||
                childText.startsWith('**Savings:**') ||
                childText.startsWith('**Action:**') ||
                childText.startsWith('**Investment Grade:**')) {
              
              const [label, ...valueParts] = childText.split(':')
              const value = valueParts.join(':').trim()
              const cleanLabel = label.replace(/\*\*/g, '')
              
              let icon = <DollarSign className="h-4 w-4" />
              let badgeClass = 'bg-gray-100 text-gray-800'
              
              if (cleanLabel === 'Action') {
                icon = <AlertTriangle className="h-4 w-4" />
                badgeClass = getActionColor(value)
              } else if (cleanLabel === 'Investment Grade') {
                icon = <ShieldCheck className="h-4 w-4" />
                badgeClass = getInvestmentColor(value)
              } else if (cleanLabel === 'Deal Score') {
                icon = <Star className="h-4 w-4" />
                badgeClass = 'bg-yellow-100 text-yellow-800 border-yellow-300'
              } else if (cleanLabel === 'Savings') {
                icon = <TrendingUp className="h-4 w-4" />
                badgeClass = 'bg-green-100 text-green-800 border-green-300'
              }
              
              return (
                <div className="flex items-center justify-between py-2 border-b border-gray-100 last:border-b-0">
                  <div className="flex items-center space-x-2">
                    {icon}
                    <span className="font-medium text-gray-700 dark:text-gray-300">{cleanLabel}:</span>
                  </div>
                  <Badge className={`${badgeClass} border font-semibold`}>
                    {value}
                  </Badge>
                </div>
              )
            }
            
            // Check for analysis sections with emoji prefixes
            if (childText.startsWith('**📊 Analysis:**') ||
                childText.startsWith('**⚠️ Risks:**') ||
                childText.startsWith('**💰 Potential:**') ||
                childText.startsWith('**🔗 URL:**')) {
              
              const [label, ...valueParts] = childText.split(':**')
              const value = valueParts.join(':**').trim()
              const cleanLabel = label.replace(/\*\*/g, '')
              
              if (cleanLabel === '🔗 URL') {
                return (
                  <div className="mt-4 pt-4 border-t border-gray-200">
                    <Button 
                      onClick={() => window.open(value, '_blank')}
                      className="w-full bg-blue-600 hover:bg-blue-700 text-white"
                    >
                      <ExternalLink className="h-4 w-4 mr-2" />
                      View on Marketplace
                    </Button>
                  </div>
                )
              }
              
              let bgColor = 'bg-blue-50 border-blue-200'
              if (cleanLabel.includes('⚠️')) bgColor = 'bg-yellow-50 border-yellow-200'
              if (cleanLabel.includes('💰')) bgColor = 'bg-green-50 border-green-200'
              
              return (
                <div className={`p-4 rounded-lg border ${bgColor} mt-3`}>
                  <div className="font-semibold text-gray-800 mb-2">{cleanLabel}</div>
                  <div className="text-gray-700">{value}</div>
                </div>
              )
            }
            
            // Check for completion indicator
            if (childText.includes('🎯 ANALYSIS COMPLETE 🎯')) {
              return (
                <div className="my-8 p-6 bg-gradient-to-r from-green-50 to-emerald-50 border-2 border-green-200 rounded-lg text-center">
                  <div className="text-2xl font-bold text-green-800 mb-2">{children}</div>
                  <p className="text-green-600">Full analysis delivered successfully</p>
                </div>
              )
            }
            
            return <p className="text-gray-600 dark:text-gray-400 leading-relaxed mb-4">{children}</p>
          },
          ul: ({ children }) => (
            <ul className="list-disc list-inside space-y-2 text-gray-600 dark:text-gray-400 ml-4">
              {children}
            </ul>
          ),
          li: ({ children }) => (
            <li className="flex items-start space-x-2">
              <span className="text-purple-600 mt-1">•</span>
              <span>{children}</span>
            </li>
          ),
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-purple-600 pl-4 italic text-gray-600 dark:text-gray-400 bg-purple-50 dark:bg-purple-900/20 p-4 rounded-r-lg">
              {children}
            </blockquote>
          ),
          code: ({ children }) => (
            <code className="bg-gray-100 dark:bg-gray-800 px-2 py-1 rounded text-sm font-mono">
              {children}
            </code>
          ),
          pre: ({ children }) => (
            <pre className="bg-gray-100 dark:bg-gray-800 p-4 rounded-lg overflow-x-auto">
              {children}
            </pre>
          ),
          table: ({ children }) => (
            <div className="my-6 w-full overflow-x-auto">
              <table className="w-full border-collapse bg-white dark:bg-gray-900 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20">
              {children}
            </thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
              {children}
            </tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
              {children}
            </tr>
          ),
          th: ({ children }) => (
            <th className="px-6 py-4 text-left text-xs font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider border-b border-gray-200 dark:border-gray-700">
              {children}
            </th>
          ),
          td: ({ children }) => {
            const childText = children?.toString() || ''
            
            // Special styling for different cell types
            if (childText.includes('IMMEDIATE BUY')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-red-100 text-red-800 border-red-300 animate-pulse font-bold">
                    🚨 {childText}
                  </Badge>
                </td>
              )
            } else if (childText.includes('BUY SOON')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-orange-100 text-orange-800 border-orange-300 font-bold">
                    ⏰ {childText}
                  </Badge>
                </td>
              )
            } else if (childText.includes('MONITOR')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-blue-100 text-blue-800 border-blue-300 font-bold">
                    👀 {childText}
                  </Badge>
                </td>
              )
            } else if (childText.includes('EXCEPTIONAL')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-green-100 text-green-800 border-green-300 font-bold">
                    💎 {childText}
                  </Badge>
                </td>
              )
            } else if (childText.includes('GREAT')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-blue-100 text-blue-800 border-blue-300 font-bold">
                    ⭐ {childText}
                  </Badge>
                </td>
              )
            } else if (childText.includes('/5 ⭐')) {
              const score = parseInt(childText) || 0
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <div className="flex items-center space-x-2">
                    <div className="flex">
                      {getDealScoreStars(score.toString())}
                    </div>
                    <span className="font-bold text-lg">{childText}</span>
                  </div>
                </td>
              )
            } else if (childText.includes('$') && (childText.includes('AUD') || /^\$\d+/.test(childText))) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <div className="flex items-center">
                    <DollarSign className="h-4 w-4 text-green-600 mr-1" />
                    <span className="font-bold text-green-700 dark:text-green-400">{childText}</span>
                  </div>
                </td>
              )
            } else if (childText.includes('%')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <div className="flex items-center">
                    <TrendingUp className="h-4 w-4 text-green-600 mr-1" />
                    <span className="font-bold text-green-700 dark:text-green-400">{childText}</span>
                  </div>
                </td>
              )
            } else if (childText.includes('Sealed')) {
              return (
                <td className="px-6 py-4 whitespace-nowrap border-b border-gray-200 dark:border-gray-700">
                  <Badge className="bg-blue-100 text-blue-800 border-blue-300">
                    🔒 {childText}
                  </Badge>
                </td>
              )
            }
            
            return (
              <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900 dark:text-gray-100 border-b border-gray-200 dark:border-gray-700">
                {children}
              </td>
            )
          },
          a: ({ href, children }) => (
            <a 
              href={href} 
              target="_blank" 
              rel="noopener noreferrer"
              className="inline-flex items-center text-blue-600 hover:text-blue-800 underline hover:no-underline transition-colors duration-200"
            >
              {children}
              <ExternalLink className="h-3 w-3 ml-1" />
            </a>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}