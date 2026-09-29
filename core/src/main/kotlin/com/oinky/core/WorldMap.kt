package com.oinky.core

import java.util.Currency
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tan

data class GeoPoint(val lon: Double, val lat: Double)

/**
 * One country outline. [rings] are outer polygon rings as flat `[lon0, lat0, lon1, lat1, ...]`
 * arrays (holes are dropped; at world zoom they are invisible).
 */
class CountryShape(
    val iso2: String,
    val name: String,
    val continent: String,
    val label: GeoPoint,
    val rings: List<DoubleArray>,
) {
    val minLon = rings.minOf { r -> r.filterIndexed { i, _ -> i % 2 == 0 }.min() }
    val maxLon = rings.maxOf { r -> r.filterIndexed { i, _ -> i % 2 == 0 }.max() }
    val minLat = rings.minOf { r -> r.filterIndexed { i, _ -> i % 2 == 1 }.min() }
    val maxLat = rings.maxOf { r -> r.filterIndexed { i, _ -> i % 2 == 1 }.max() }

    val flag: String get() = Countries.flag(iso2)

    fun contains(lon: Double, lat: Double): Boolean {
        if (lon < minLon || lon > maxLon || lat < minLat || lat > maxLat) return false
        return rings.any { pointInRing(it, lon, lat) }
    }

    private fun pointInRing(r: DoubleArray, x: Double, y: Double): Boolean {
        var inside = false
        val n = r.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = r[2 * i]; val yi = r[2 * i + 1]
            val xj = r[2 * j]; val yj = r[2 * j + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/**
 * Country outlines bundled with the app (simplified Natural Earth 1:50m, public domain), so the
 * travel map works offline and without a maps API key.
 */
class WorldMapData(val countries: List<CountryShape>) {

    private val byIso = countries.associateBy { it.iso2 }

    operator fun get(iso2: String): CountryShape? = byIso[iso2.uppercase()]

    /** The country under a point, preferring the smallest match (enclaves, islands). */
    fun countryAt(lon: Double, lat: Double): CountryShape? =
        countries.filter { it.contains(lon, lat) }
            .minByOrNull { (it.maxLon - it.minLon) * (it.maxLat - it.minLat) }

    /** Case-insensitive search by name or ISO code: exact code, then name prefix, then substring. */
    fun search(query: String, limit: Int = 8): List<CountryShape> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return countries
            .filter { it.name.lowercase().contains(q) || it.iso2.lowercase() == q }
            .sortedWith(compareBy({ it.iso2.lowercase() != q }, { !it.name.lowercase().startsWith(q) }, { it.name }))
            .take(limit)
    }

    companion object {
        /** Format: `C|iso2|name|continent|labelLon|labelLat` followed by `R|lon,lat lon,lat ...` lines. */
        fun parse(text: String): WorldMapData {
            data class Builder(val iso: String, val name: String, val continent: String, val label: GeoPoint, val rings: MutableList<DoubleArray>)
            val builders = LinkedHashMap<String, Builder>()
            var current: Builder? = null
            text.lineSequence().forEach { line ->
                when {
                    line.startsWith("C|") -> {
                        val p = line.split('|')
                        // Several Natural Earth units can share one ISO code (e.g. Australian
                        // territories); merge them into the first, main entry.
                        current = builders.getOrPut(p[1]) {
                            Builder(p[1], p[2], p[3], GeoPoint(p[4].toDouble(), p[5].toDouble()), mutableListOf())
                        }
                    }
                    line.startsWith("R|") -> {
                        val pts = line.substring(2).split(' ')
                        val arr = DoubleArray(pts.size * 2)
                        pts.forEachIndexed { i, s ->
                            val comma = s.indexOf(',')
                            arr[2 * i] = s.substring(0, comma).toDouble()
                            arr[2 * i + 1] = s.substring(comma + 1).toDouble()
                        }
                        current?.rings?.add(arr)
                    }
                }
            }
            return WorldMapData(
                builders.values.filter { it.rings.isNotEmpty() }
                    .map { CountryShape(it.iso, it.name, it.continent, it.label, it.rings) },
            )
        }
    }
}

/** Web-Mercator in unit space: x and y both in [0, 1], y growing southwards. */
object Mercator {
    private const val MAX_LAT = 85.0511

    fun x(lon: Double): Double = (lon + 180.0) / 360.0

    fun y(lat: Double): Double {
        val phi = Math.toRadians(lat.coerceIn(-MAX_LAT, MAX_LAT))
        return (1.0 - ln(tan(PI / 4 + phi / 2)) / PI) / 2.0
    }

    fun lon(x: Double): Double = x * 360.0 - 180.0

    fun lat(y: Double): Double = Math.toDegrees(2 * atan(exp(PI * (1 - 2 * y))) - PI / 2)
}

object Countries {
    /** 🇸🇬 from "SG": two regional indicator symbols. */
    fun flag(iso2: String): String {
        if (iso2.length != 2 || !iso2.all { it.isLetter() }) return "🏳️"
        return iso2.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }

    /** Local currency for a country, e.g. "JP" -> "JPY". Null for places without one. */
    fun currency(iso2: String): String? = runCatching {
        Currency.getInstance(Locale.Builder().setRegion(iso2.uppercase()).build())?.currencyCode
    }.getOrNull()?.takeIf { it != "XXX" }
}
