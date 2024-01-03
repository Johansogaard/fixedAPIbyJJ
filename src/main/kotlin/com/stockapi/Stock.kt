package com.stockapi

data class Stock(val ticker: String, val originalPrice: Double) {
    var currentPrice: Double = originalPrice
   // private val historicalData = mutableListOf<PricePoint>()
/*
    fun addHistoricalData(price: Double) {
        historicalData.add(PricePoint(System.currentTimeMillis(), price))
    }

    fun getAggregatedData(interval: Interval, count: Int): List<Double> {
        // Return the last 'count' price points relevant to the interval
        return historicalData.takeLast(count).map { it.price }
    }*/
}