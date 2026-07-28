package com.personalfinance.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MetalPrices(
    val gold18TomanPerGram: Double,
    val silver999TomanPerGram: Double,
    val updatedAt: String
)

/**
 * BRS exposes Iranian gold/currency prices and global silver spot prices.
 * The key belongs to the user and is stored locally; no credential ships in the APK.
 */
object MetalPriceService {
    suspend fun fetch(apiKey: String): MetalPrices = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "کلید API وارد نشده است" }
        val market = getJson("https://api.brsapi.ir/Market/Gold_Currency.php?key=${encode(apiKey)}")
        val commodities = getJson("https://api.brsapi.ir/Market/Commodity.php?key=${encode(apiKey)}")
        val marketRows = flatten(market)
        val commodityRows = flatten(commodities)

        val gold = marketRows.firstOrNull {
            val symbol = it.optString("symbol").uppercase()
            val name = it.optString("name")
            symbol.contains("GOLD_18") || name.contains("18") && name.contains("طلا")
        } ?: error("قیمت طلای ۱۸ عیار در پاسخ API پیدا نشد")
        val dollar = marketRows.firstOrNull {
            val symbol = it.optString("symbol").uppercase()
            symbol == "USD" || it.optString("name").contains("دلار آمریکا")
        } ?: error("قیمت دلار در پاسخ API پیدا نشد")
        val silver = commodityRows.firstOrNull {
            it.optString("symbol").uppercase() == "XAGUSD" || it.optString("name").contains("نقره")
        } ?: error("قیمت نقره در پاسخ API پیدا نشد")

        fun toman(row: JSONObject): Double {
            val value = row.optDouble("price", Double.NaN)
            require(value.isFinite()) { "قیمت نامعتبر از API" }
            return if (row.optString("unit").contains("ریال")) value / 10.0 else value
        }

        val goldPerGram = toman(gold)
        val usdToman = toman(dollar)
        val silverPerOunceUsd = silver.optDouble("price", Double.NaN)
        require(silverPerOunceUsd.isFinite()) { "قیمت نقره نامعتبر است" }
        MetalPrices(
            gold18TomanPerGram = goldPerGram,
            silver999TomanPerGram = silverPerOunceUsd * usdToman / 31.1034768,
            updatedAt = gold.optString("date") + " " + gold.optString("time")
        )
    }

    private fun getJson(url: String): Any {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error("خطای سرویس قیمت (${connection.responseCode})")
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).also {
                if (it.optBoolean("successful", true).not()) {
                    error(it.optString("message_error", "سرویس قیمت پاسخ نامعتبر داد"))
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun flatten(value: Any): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        fun visit(item: Any?) {
            when (item) {
                is JSONObject -> {
                    if (item.has("price") && (item.has("symbol") || item.has("name"))) result += item
                    val keys = item.keys()
                    while (keys.hasNext()) visit(item.opt(keys.next()))
                }
                is JSONArray -> for (i in 0 until item.length()) visit(item.opt(i))
            }
        }
        visit(value)
        return result
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
