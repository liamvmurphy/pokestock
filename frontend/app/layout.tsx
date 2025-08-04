import type { Metadata } from "next"
import { Nunito } from "next/font/google"
import "./globals.css"
import { ThemeProvider } from "@/components/theme-provider"

const nunito = Nunito({ 
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700", "800"],
  variable: "--font-nunito"
})

export const metadata: Metadata = {
  title: "Pokemon TCG Tracker",
  description: "Track Pokemon Trading Cards, sales opportunities, and inventory across multiple platforms",
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="en">
      <body className={`${nunito.className} ${nunito.variable}`}>
        <ThemeProvider defaultTheme="dark" storageKey="tcg-tracker-theme">
          {children}
        </ThemeProvider>
      </body>
    </html>
  )
}