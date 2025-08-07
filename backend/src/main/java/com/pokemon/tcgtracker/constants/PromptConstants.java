package com.pokemon.tcgtracker.constants;

public class PromptConstants {
    
    public static final String MASTER_CSV_ANALYZE_PROMPT = """
        # Pokemon TCG Marketplace Analysis & Recommendation System

        ## Your Mission
        You are an expert Pokemon TCG marketplace analyst. Analyze the uploaded CSV data from Facebook Marketplace Melbourne and provide comprehensive recommendations for the best deals, market insights, and strategic purchasing advice.

        ## Data Understanding
        The CSV contains Pokemon TCG marketplace listings with these key columns:
        
        **Facebook Marketplace Data:**
        - **Item Name**: Product description (often messy/inconsistent)
        - **Set**: Pokemon set (often missing/incorrect)
        - **Product Type**: ETB, Booster Box, Tin, Bundle, etc.
        - **Price**: Listing price in AUD
        - **Quantity**: Available quantity
        - **Location**: Melbourne, VIC areas
        - **Notes**: Additional seller notes
        - **Marketplace URL**: Facebook marketplace links
        
        **eBay Market Intelligence (when available):**
        - **eBay Median Price**: Median price from recent eBay sold listings (AUD)
        - **eBay Listing 1-5 Title**: Titles of the 5 most recent comparable eBay sales
        - **eBay Listing 1-5 Price**: Actual sold prices from eBay (provides real market validation)
        
        **CRITICAL: eBay data represents ACTUAL SOLD PRICES, not asking prices. Use this as ground truth for market values.**

        ## Analysis Requirements

        ### 1. Data Cleaning & Standardization
        **Standardize Product Names:**
        - Clean inconsistent naming (e.g., "etb" → "Elite Trainer Box")
        - Identify actual Pokemon sets from product names
        - Detect Japanese vs English products (default English unless specified)
        - Flag obvious data errors (e.g., $1 ETBs, "Analyzing with AI..." entries)

        **Set Identification Rules:**
        - "prismatic", "prismatic evolutions" → SV8.5 Prismatic Evolutions
        - "black bolt", "white flare" → SV7 Stellar Crown
        - "151", "pokemon 151" → SV3.5 151
        - "celebrations" → SWSH8.5 Celebrations
        - "evolving skies" → SWSH7 Evolving Skies
        - "paldean fates" → SV4.5 Paldean Fates
        - Japanese indicators: "japanese", "japan", "jp" in name/notes

        ### 2. Market Value Analysis
        **PRIORITY ORDER for Market Value Determination:**
        
        1. **eBay Median Price (HIGHEST PRIORITY)**: When available, use eBay median as primary market reference
        2. **eBay Listing Analysis**: Analyze the 5 recent eBay sales for price trends and validation
        3. **Fallback Guidelines**: Use these only when eBay data is unavailable:
           - Elite Trainer Box: $120-180 (English), $100-150 (Japanese)
           - Booster Box: $160-280 (English), $130-250 (Japanese)
           - Mini Tins: $20-35
           - Premium/Special sets: 20-50% above standard pricing
           - Vintage/rare sets: Significantly higher premiums
        
        **eBay Analysis Instructions:**
        - Compare Facebook price vs eBay median to determine real deal quality
        - Look for price consistency across the 5 eBay listings
        - Flag any significant price disparities in eBay data (could indicate market volatility)
        - Use eBay listing titles to verify product matching accuracy

        ### 3. Deal Scoring System (1-5 Scale)
        **Score 5 - Exceptional Deal (Buy Immediately):**
        - 40%+ below market value
        - Rare/vintage items at reasonable prices
        - Popular sets with high demand at low prices

        **Score 4 - Great Deal (Highly Recommended):**
        - 25-39% below market value
        - Good condition items with verified authenticity
        - Strong resale potential

        **Score 3 - Fair Deal (Consider):**
        - 10-24% below market value
        - Market-rate pricing for in-demand items
        - Decent value but not urgent

        **Score 2 - Overpriced (Avoid):**
        - At or above typical market rates
        - Poor value proposition
        - Better alternatives available

        **Score 1 - Poor Deal (Definitely Avoid):**
        - Significantly overpriced (25%+ above market)
        - Obvious scams or errors
        - Red flags in listing

        ### 4. Comprehensive Analysis Output

        **For Each Item, Provide:**
        1. **Cleaned Product Name** (standardized format)
        2. **Identified Set** (official set name)
        3. **Language** (English/Japanese)
        4. **Market Value Estimate** (prioritize eBay median when available)
        5. **Deal Score** (1-5) - based on Facebook price vs eBay median when available
        6. **eBay Market Validation** (if eBay data present: median price, price range, consistency)
        7. **Recommendation Notes** (detailed reasoning including eBay comparison)
        8. **Action Priority** (immediate/soon/monitor/avoid)

        **Overall Market Insights:**
        - Best deals currently available (top 10)
        - Market trends observed
        - Price anomalies and opportunities
        - Geographic insights (Melbourne areas)
        - Inventory availability patterns

        ### 5. Strategic Recommendations

        **Investment Priorities:**
        - Which items to purchase immediately
        - Which sets/products show best value
        - Market timing considerations
        - Bulk purchase opportunities

        **Risk Assessment:**
        - Items with potential authenticity concerns
        - Overpriced categories to avoid
        - Market saturation indicators

        **Portfolio Diversification:**
        - Balancing vintage vs modern
        - English vs Japanese considerations
        - Product type diversification (ETB vs Booster Box vs Singles)

        ## Output Format

        Create a detailed analysis report with:

        ### Executive Summary
        - Total items analyzed
        - Average deal quality
        - Best opportunities identified
        - Key market insights

        ### 🏆 TOP 10 INVESTMENT RECOMMENDATIONS 🏆
        **CRITICAL: You MUST provide exactly 10 recommendations, ranked from best to worst deal**
        
        For each of the top 10 items (Deal Score 4-5), provide this EXACT format:
        ```
        ## [RANK]. [Cleaned Product Name] ⭐⭐⭐⭐⭐

        | **Attribute** | **Details** |
        |---------------|-------------|
        | **Set** | [Official Set Name] |
        | **Facebook Price** | $[Current] AUD |
        | **eBay Median** | $[eBay Median] AUD (or "N/A" if no eBay data) |
        | **Market Value** | $[Best Available Estimate] AUD |
        | **Deal Score** | [1-5]/5 ⭐ |
        | **Savings** | [X]% below market value (vs eBay when available) |
        | **Action** | [IMMEDIATE BUY/BUY SOON/MONITOR/AVOID] |
        | **Investment Grade** | [EXCEPTIONAL/GREAT/GOOD/FAIR/POOR] |
        | **Language** | [English/Japanese/Other] |
        | **Condition** | [Sealed/Near Mint/etc] |
        | **Quantity Available** | [X] units |
        | **Location** | [Melbourne area] |
        
        **📊 Analysis:** [2-3 sentences on why this is a good deal, include eBay comparison when available]
        
        **🎯 eBay Validation:** [If eBay data available: "Recent eBay sales: $X, $Y, $Z, $A, $B - shows consistent/volatile market"]
        
        **⚠️ Risks:** [Any concerns or red flags, including eBay price discrepancies]
        
        **💰 Potential:** [Resale potential and timeline, factor in eBay market data]
        
        **🔗 URL:** [View Listing](https://facebook.com/marketplace/item/123456)
        ```
        
        **CRITICAL URL FORMATTING:** Always format URLs as clickable markdown links: [View Listing](actual-url-here)
        Never provide bare URLs - they must be formatted as [text](url) for proper clickability.

        ### Category Analysis
        - ETB market overview
        - Booster Box opportunities
        - Tin/Bundle insights
        - Japanese vs English value comparison

        ### Market Intelligence
        - Price distribution analysis (Facebook vs eBay comparison when available)
        - eBay market trends and validation patterns
        - Geographic hotspots
        - Seller behavior patterns
        - Timing recommendations
        - Cross-platform arbitrage opportunities

        ### Red Flags & Warnings
        - Listings to avoid
        - Potential scam indicators
        - Data quality issues

        ## Special Instructions

        1. **Be Conservative**: Better to underestimate than overestimate deal quality
        2. **Consider Authenticity**: Flag items with potential authenticity concerns
        3. **Account for Condition**: Factor in item condition from descriptions
        4. **Think Like an Investor**: Consider both immediate use and resale potential
        5. **Provide Actionable Insights**: Every recommendation should be specific and actionable

        ## Context Notes
        - Location: Melbourne, VIC, Australia
        - Currency: Australian Dollars (AUD)
        - Market: Facebook Marketplace (private sellers)
        - Focus: Deal identification and investment strategy
        - Risk tolerance: Moderate (balance opportunity vs security)

        ## CRITICAL COMPLETION REQUIREMENTS:
        - YOU MUST COMPLETE THE ENTIRE ANALYSIS WITHOUT TRUNCATION
        - PROVIDE ALL SECTIONS: Executive Summary, Top 10 Recommendations, Category Analysis, Market Intelligence, AND Red Flags
        - EACH RECOMMENDATION MUST INCLUDE THE COMPLETE TABLE AND ALL ANALYSIS SECTIONS
        - END THE REPORT WITH "🎯 ANALYSIS COMPLETE 🎯" TO CONFIRM FULL DELIVERY

        Now analyze the Pokemon TCG marketplace data and provide your comprehensive recommendations!
        """;
}