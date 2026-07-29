package com.personalfinance.tracker.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class MetalPrices(
    val gold18TomanPerGram: Double,
    val silver999TomanPerGram: Double,
    val updatedAt: String
)

/** Reads the public TGJU profile pages. Their displayed gram prices are in Rial. */
object MetalPriceService {
    private const val GOLD_URL = "https://www.tgju.org/profile/geram18"
    private const val SILVER_URL = "https://www.tgju.org/profile/silver_999"

    suspend fun fetch(): MetalPrices = withContext(Dispatchers.IO) {
        val goldHtml = getHtml(GOLD_URL)
        val silverHtml = getHtml(SILVER_URL)
        val goldRial = currentPrice(goldHtml)
        val silverRial = currentPrice(silverHtml)
        MetalPrices(
            gold18TomanPerGram = goldRial / 10.0,
            silver999TomanPerGram = silverRial / 10.0,
            updatedAt = serverTime(goldHtml)
        )
    }

    internal fun currentPrice(html: String): Double {
        val pattern = Regex(
            """class=["'][^"']*\bprice\b[^"']*["'][^>]*data-col=["']info\.last_trade\.PDrCotVal["'][^>]*>\s*([\d,۰-۹٠-٩]+)""",
            RegexOption.IGNORE_CASE
        )
        val raw = pattern.find(html)?.groupValues?.get(1)
            ?: error("قیمت فعلی در صفحه TGJU پیدا نشد")
        return Digits.toEnglish(raw).replace(",", "").toDoubleOrNull()
            ?: error("قیمت TGJU نامعتبر است")
    }

    private fun serverTime(html: String): String {
        val raw = Regex("""id=["']server-time["'][^>]*data-value=["']([^"']+)""")
            .find(html)?.groupValues?.get(1)
        return raw ?: JalaliCalendar.formatDateTime(System.currentTimeMillis())
    }

    private fun getHtml(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Maldar)")
            connection.setRequestProperty("Accept", "text/html")
            val code = connection.responseCode
            if (code !in 200..299) error("خطای TGJU ($code)")
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
