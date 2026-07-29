package com.personalfinance.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class StockSearchResult(
    val instrumentCode: String,
    val symbol: String,
    val name: String,
    val market: String
)

data class StockQuote(
    val instrumentCode: String,
    val lastPriceToman: Double,
    val closingPriceToman: Double?,
    val fetchedAtMillis: Long
)

/**
 * Minimal client for TSETMC's public CDN endpoints.
 *
 * Prices returned by TSETMC are Rial. They are converted to Toman here so no
 * caller can accidentally mix units with the rest of the application.
 */
object TsetmcStockService {
    private const val BASE_URL = "https://cdn.tsetmc.com/api"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36"

    suspend fun search(query: String): List<StockSearchResult> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
        if (normalized.length < 2) return@withContext emptyList()
        val encoded = URLEncoder.encode(normalized, "UTF-8").replace("+", "%20")
        parseSearch(getJson("$BASE_URL/Instrument/GetInstrumentSearch/$encoded"), normalized)
    }

    suspend fun quote(instrumentCode: String): StockQuote = withContext(Dispatchers.IO) {
        require(instrumentCode.all(Char::isDigit)) { "شناسه نماد نامعتبر است" }
        parseQuote(
            getJson("$BASE_URL/ClosingPrice/GetClosingPriceInfo/$instrumentCode"),
            instrumentCode,
            System.currentTimeMillis()
        )
    }

    internal fun parseSearch(json: String, query: String = ""): List<StockSearchResult> {
        val exactQuery = query.trim()
        return flatObjectsInArray(json, "instrumentSearch")
            .mapNotNull { item ->
                val instrumentCode = stringField(item, "insCode") ?: return@mapNotNull null
                val symbol = stringField(item, "lVal18AFC")?.trim().orEmpty()
                val name = stringField(item, "lVal30")?.trim().orEmpty()
                val market = stringField(item, "flowTitle")?.trim().orEmpty()
                if (instrumentCode.isBlank() || symbol.isBlank() || name.isBlank()) return@mapNotNull null
                StockSearchResult(instrumentCode, symbol, name, market)
            }
            // Options and futures share the searched symbol text but are not
            // ordinary portfolio shares.
            .filterNot {
                it.market.contains("مشتقه") ||
                    it.name.startsWith("اختيار") ||
                    it.name.startsWith("اختیار") ||
                    it.name.startsWith("آتي ") ||
                    it.name.startsWith("آتی ")
            }
            .distinctBy { it.instrumentCode }
            .sortedWith(
                compareByDescending<StockSearchResult> {
                    exactQuery.isNotEmpty() && it.symbol.equals(exactQuery, ignoreCase = true)
                }.thenBy { it.symbol.length }
                    .thenBy { it.symbol }
            )
            .take(20)
    }

    internal fun parseQuote(
        json: String,
        instrumentCode: String,
        fetchedAtMillis: Long = System.currentTimeMillis()
    ): StockQuote {
        val lastRial = numberField(json, "pDrCotVal")?.takeIf { it > 0.0 }
            ?: numberField(json, "pClosing")?.takeIf { it > 0.0 }
            ?: numberField(json, "priceYesterday")?.takeIf { it > 0.0 }
            ?: error("قیمت معتبر برای این نماد در TSETMC پیدا نشد")
        val closingRial = numberField(json, "pClosing")?.takeIf { it > 0.0 }
        return StockQuote(
            instrumentCode = instrumentCode,
            lastPriceToman = lastRial / 10.0,
            closingPriceToman = closingRial?.div(10.0),
            fetchedAtMillis = fetchedAtMillis
        )
    }

    private fun getJson(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json, text/plain, */*")
            connection.setRequestProperty("Origin", "https://www.tsetmc.com")
            connection.setRequestProperty("Referer", "https://www.tsetmc.com/")
            val code = connection.responseCode
            if (code !in 200..299) error("خطای TSETMC ($code)")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The search response is an array of flat JSON objects. A small string-aware
     * scanner keeps this parser JVM-testable without adding a networking/JSON
     * dependency solely for two stable public endpoints.
     */
    private fun flatObjectsInArray(json: String, key: String): List<String> {
        val keyIndex = json.indexOf("\"$key\"")
        if (keyIndex < 0) return emptyList()
        val arrayStart = json.indexOf('[', keyIndex)
        if (arrayStart < 0) return emptyList()

        val objects = mutableListOf<String>()
        var objectStart = -1
        var depth = 0
        var inString = false
        var escaped = false
        var index = arrayStart + 1
        while (index < json.length) {
            val char = json[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) objectStart = index
                        depth++
                    }
                    '}' -> {
                        if (depth > 0) depth--
                        if (depth == 0 && objectStart >= 0) {
                            objects += json.substring(objectStart, index + 1)
                            objectStart = -1
                        }
                    }
                    ']' -> if (depth == 0) break
                }
            }
            index++
        }
        return objects
    }

    private fun stringField(jsonObject: String, key: String): String? {
        val pattern = Regex(
            "\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        )
        return pattern.find(jsonObject)?.groupValues?.get(1)?.let(::decodeJsonString)
    }

    private fun numberField(json: String, key: String): Double? {
        val pattern = Regex(
            "\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)"
        )
        return pattern.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun decodeJsonString(value: String): String {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index + 1 >= value.length) {
                output.append(char)
                index++
                continue
            }
            when (val escaped = value[index + 1]) {
                '"', '\\', '/' -> output.append(escaped)
                'b' -> output.append('\b')
                'f' -> output.append('\u000C')
                'n' -> output.append('\n')
                'r' -> output.append('\r')
                't' -> output.append('\t')
                'u' -> {
                    val end = index + 6
                    val decoded = if (end <= value.length) {
                        value.substring(index + 2, end).toIntOrNull(16)?.toChar()
                    } else {
                        null
                    }
                    if (decoded != null) {
                        output.append(decoded)
                        index += 6
                        continue
                    }
                    output.append("\\u")
                }
                else -> output.append(escaped)
            }
            index += 2
        }
        return output.toString()
    }
}
