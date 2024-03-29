package com.stockapi

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.util.Collections.emptyList
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random


data class StockGroup(val name: String, val tickers: List<Stock>)

data class PricePoint(val timestamp: Long, val price: Double)

enum class Interval {
    MINUTE, FIFTEEN_MINUTES, HOUR, DAY
}

@Service
class UpdateStock {
    private var simulatedTime: Int = 0

        private val Stocks = ConcurrentHashMap<String, Stock>() // All individual stocks
        private val stockGroups = ConcurrentHashMap<String, StockGroup>() // Groups of stocks
        private val folderPath = "tickers" // Folder to store files
        private val stockNationalities: ConcurrentHashMap<String, String> = ConcurrentHashMap()

    private val debugMode = false // Set to true to enable debug mode, false to disable

    private fun debugPrint(message: String) {
        if (debugMode) { println(message) } }

        init {


            stockGroups["C25"] = StockGroup("C25", listOf(Stock("NOVO", 750.0),
                Stock("MAERSK", 150.0),Stock("ORSTED", 15.0),
                Stock("NETC", 30.0), Stock("DANSKE", 25.0)))

            stockGroups["S&P500"] = StockGroup("S&P500", listOf(Stock("MSFT", 382.0)
                ,Stock("AAPL", 189.0),Stock("NVDA", 477.0),Stock("AMZN", 150.0),
                Stock("GOOGL", 150.0), Stock("META", 340.0),
                Stock("BRK.B", 350.0)))

            stockGroups["WORLD"] = StockGroup("WORLD", listOf(Stock("OIL", 50.0)))

            // Add all stocks to the comprehensive list
            stockGroups.values.forEach { group ->
                group.tickers.forEach { stock ->
                    Stocks[stock.ticker] = stock
                    //loadHistoricalData(stock.ticker)
                }
            }
        }

    fun getTickersByGroup(groupName: String): List<String> {
        val group = stockGroups[groupName] ?: throw IllegalArgumentException("Stock group not found")
        return group.tickers.map { it.ticker }
    }

    fun getNationalities(tickers: List<String>): Map<String, String> {
        val nationalities = mutableMapOf<String, String>()

        stockGroups.forEach { (groupName, group) ->
            group.tickers.forEach { stock ->
                if (tickers.contains(stock.ticker)) {
                    nationalities[stock.ticker] = groupName
                }
            }
        }

        return nationalities
    }

    fun searchStocksBySubstring(query: String): List<String> {
        return Stocks.keys.filter { it.contains(query, ignoreCase = true) }
    }
    private fun loadHistoricalData(ticker: String) {
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) {
            file.createNewFile()
        } else {
            var lastPrice: Double? = null
            file.forEachLine { line ->
                // Assuming the average is always present and is the first value
                val averagePart = line.substringAfter("Average=").substringBefore(",")
                val price = averagePart.toDoubleOrNull()
                price?.let {
                   // Stocks[ticker]?.addHistoricalData(it)
                    lastPrice = it
                }
            }
            // Set the current price to the last historical price
            if (lastPrice != null) {
                Stocks[ticker]?.currentPrice = lastPrice!!
            }
        }
    }

    // This method now only reads historical data from the file system when called
    fun getHistoricalData(ticker: String, interval: Interval, count: Int): List<Triple<Double, Double, Double>> {
        val file = File("$folderPath/$ticker.txt")
        if (!file.exists()) return emptyList()

        val data = file.readLines()
        val intervalData = data.filter { it.startsWith(interval.name) }.takeLast(count)

        return intervalData.mapNotNull { line ->
            val average = line.substringAfter("Average=").substringBefore(",").toDoubleOrNull()
            val max = line.substringAfter("Max=").substringBefore(",").toDoubleOrNull()
            val min = line.substringAfter("Min=").toDoubleOrNull()
            if (average != null && max != null && min != null) Triple(average, max, min) else null
        }
    }


    @Scheduled(fixedRate = 10)
    fun updateStockPrices() {

        simulatedTime += 1
        Stocks.values.forEach { stock ->
            //debugPrint("Processing stock: ${stock.ticker}")

            val change = (Random.nextDouble() - 0.5) * 0.1 // Change in price
            stock.currentPrice += change
            stock.addHistoricalData(stock.currentPrice)

            if (simulatedTime % 60 == 0) { // Minute
                val relevantData = stock.getAggregatedData(Interval.MINUTE, calculateCountForInterval(Interval.MINUTE))

                // Calculate the average, max, and min for the relevant data
                val averagePrice = if (relevantData.isNotEmpty()) relevantData.average() else 0.0
                val maxPrice = relevantData.maxOrNull() ?: 0.0
                val minPrice = relevantData.minOrNull() ?: 0.0

                // Append the average, max, and min values to the file
                val dataString = "${Interval.MINUTE.name}: Average=$averagePrice, Max=$maxPrice, Min=$minPrice\n"
                appendToFile(stock.ticker,dataString)
            }

            if (simulatedTime % (15 * 60) == 0) { // Fifteen minutes
                aggregateDataForInterval(Interval.FIFTEEN_MINUTES,Interval.MINUTE,15, stock)
            }
            if (simulatedTime % (60 * 60) == 0) { // Hour
                aggregateDataForInterval(Interval.HOUR,Interval.FIFTEEN_MINUTES,4, stock)




            }
            if (simulatedTime % (24 * 60 * 60) == 0) { // Day
                aggregateDataForInterval(Interval.DAY,Interval.HOUR,24, stock)

                Stocks.keys.forEach { ticker ->
                    cleanUpFileForTicker(ticker)
                }
            }

        }
    }

    private fun appendToFile(ticker: String, data: String) {
        File("$folderPath/$ticker.txt").appendText(data)
        debugPrint("appending ticker: $ticker data:$data")
    }



    private fun aggregateDataForInterval(interval: Interval, earlyInterval: Interval, timesEarlyInterval: Int, stock: Stock) {
            val historicalData = getHistoricalData(stock.ticker, earlyInterval, timesEarlyInterval)

            val averagePrice = historicalData.map { it.first }.average()
            val maxOfMax = historicalData.maxOfOrNull { it.second } ?: 0.0
            val minOfMin = historicalData.minOfOrNull { it.third } ?: 0.0

            val dataString = "${interval.name}: Average=$averagePrice, Max=$maxOfMax, Min=$minOfMin\n"
            appendToFile(stock.ticker, dataString)
    }

    private fun calculateCountForInterval(interval: Interval): Int {
        return when (interval) {
            Interval.MINUTE -> 60 // 60 seconds
            Interval.FIFTEEN_MINUTES -> 15 * 60 // 15 minutes
            Interval.HOUR -> 4 // 4 fifteen-minute intervals
            Interval.DAY -> 24 // 24 hours
        }
    }

private fun cleanUpFileForTicker(ticker: String) {
    val file = File("$folderPath/$ticker.txt")
    if (!file.exists()) return

    val allLines = file.readLines()
    val cleanedLines = allLines.groupBy { it.substringBefore(':') } // Group by interval
        .flatMap { (_, lines) -> lines.takeLast(300) } // Keep only last 300 entries per interval

    file.writeText(cleanedLines.joinToString("\n")

    )
    appendToFile("$ticker","\n")
}
}