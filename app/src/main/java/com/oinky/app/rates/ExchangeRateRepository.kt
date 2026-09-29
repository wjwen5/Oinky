package com.oinky.app.rates

import com.oinky.app.data.RateDao
import com.oinky.app.data.RateEntity
import com.oinky.core.RateTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Online exchange rates with an offline cache.
 *
 *  - Latest rates: open.er-api.com (free, no key, ~160 currencies incl. MYR/SGD), falling back to
 *    frankfurter (European Central Bank reference rates).
 *  - Back-dated entries use frankfurter's historical endpoint so a receipt from last month is
 *    converted at last month's rate.
 *
 * All rates are cached against USD; cross rates (MYR -> SGD) are derived by [RateTable].
 */
class ExchangeRateRepository(private val dao: RateDao) {

    private val historicalCache = ConcurrentHashMap<String, BigDecimal>()

    suspend fun table(): RateTable? {
        val rows = dao.all()
        if (rows.isEmpty()) return null
        return RateTable(rows.first().base, rows.associate { it.code to it.perBase })
    }

    fun observeTable(): Flow<RateTable?> = dao.observeAll().map { rows ->
        if (rows.isEmpty()) null else RateTable(rows.first().base, rows.associate { it.code to it.perBase })
    }

    fun observeLastUpdated(): Flow<Pair<Long, String>?> = dao.observeAll().map { rows ->
        rows.maxByOrNull { it.fetchedAt }?.let { it.fetchedAt to it.source }
    }

    suspend fun refreshIfStale(maxAgeMillis: Long = 6 * 60 * 60 * 1000L): Boolean {
        val newest = dao.all().maxOfOrNull { it.fetchedAt } ?: 0L
        if (System.currentTimeMillis() - newest < maxAgeMillis) return true
        return refresh().isSuccess
    }

    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (rates, source) = runCatching { fetchOpenErApi() to "open.er-api.com" }
                .recoverCatching { fetchFrankfurterLatest() to "frankfurter (ECB)" }
                .getOrThrow()
            val now = System.currentTimeMillis()
            dao.replaceAll(rates.map { (code, v) -> RateEntity(code, v, BASE, now, source) })
        }
    }

    /**
     * Rate to multiply an amount in [from] by to get [to]. Uses the historical rate for dates
     * more than a day in the past when available, else the latest cached rate.
     */
    suspend fun rate(from: String, to: String, date: LocalDate = LocalDate.now()): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        if (date.isBefore(LocalDate.now().minusDays(1))) {
            historical(from, to, date)?.let { return it }
        }
        refreshIfStale()
        return table()?.rate(from, to)
    }

    private suspend fun historical(from: String, to: String, date: LocalDate): BigDecimal? {
        val key = "$from>$to@$date"
        historicalCache[key]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val json = get("https://api.frankfurter.dev/v1/$date?base=$from&symbols=$to")
                BigDecimal(json.getJSONObject("rates").getDouble(to))
            }.getOrNull()?.also { historicalCache[key] = it }
        }
    }

    private fun fetchOpenErApi(): Map<String, Double> {
        val json = get("https://open.er-api.com/v6/latest/$BASE")
        check(json.optString("result") == "success") { "open.er-api error: ${json.optString("error-type")}" }
        return json.getJSONObject("rates").toDoubleMap()
    }

    private fun fetchFrankfurterLatest(): Map<String, Double> {
        val json = get("https://api.frankfurter.dev/v1/latest?base=$BASE")
        return json.getJSONObject("rates").toDoubleMap() + (BASE to 1.0)
    }

    private fun JSONObject.toDoubleMap(): Map<String, Double> =
        keys().asSequence().associateWith { getDouble(it) }

    private fun get(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/json")
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode} from $url" }
            return JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val BASE = "USD"
    }
}
