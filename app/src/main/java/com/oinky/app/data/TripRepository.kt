package com.oinky.app.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.oinky.app.widget.WidgetRefresher
import com.oinky.core.Category
import com.oinky.core.Countries
import com.oinky.core.TripMath
import com.oinky.core.WorldMapData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** A search hit for "add a place to this trip". */
data class PlaceCandidate(val name: String, val countryCode: String, val lat: Double, val lon: Double, val detail: String)

class TripRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val ledger: LedgerRepository,
) {
    private val worldLock = Mutex()
    private var world: WorldMapData? = null

    /** Bundled country outlines, parsed once off the main thread. */
    suspend fun world(): WorldMapData = worldLock.withLock {
        world ?: withContext(Dispatchers.Default) {
            WorldMapData.parse(context.assets.open("world_50m.txt").bufferedReader().use { it.readText() })
        }.also { world = it }
    }

    fun observeTrips(): Flow<List<TripEntity>> = db.trips().observeAll()
    fun observeAllPlaces(): Flow<List<TripPlaceEntity>> = db.trips().observeAllPlaces()
    fun observeTrip(id: Long): Flow<TripEntity?> = db.trips().observe(id)
    fun observePlaces(tripId: Long): Flow<List<TripPlaceEntity>> = db.trips().observePlaces(tripId)
    fun observeTxns(tripId: Long): Flow<List<TxnEntity>> = db.trips().observeTxns(tripId)
    fun observeAllTripTxns(): Flow<List<TxnEntity>> = db.trips().observeAllTripTxns()
    fun observeTripOn(day: Long): Flow<TripEntity?> = db.trips().observeCovering(day)

    suspend fun trips(): List<TripEntity> = db.trips().all()

    /** Saves the trip and re-links the money entries that fall inside its dates. */
    suspend fun saveTrip(trip: TripEntity): Long {
        val old = if (trip.id == 0L) null else db.trips().get(trip.id)
        val id = if (trip.id == 0L) db.trips().insert(trip) else trip.id.also { db.trips().update(trip) }
        if (old != null) {
            db.trips().releaseDateLinkedOutside(id, old.startEpochDay, old.endEpochDay, trip.startEpochDay, trip.endEpochDay)
        }
        db.trips().claimTxns(id, trip.startEpochDay, trip.endEpochDay)
        WidgetRefresher.request(context)
        return id
    }

    /**
     * Expenses that might be bookings for [trip]: from a year before it until a month after,
     * outside its dates, not linked to another trip. Travel-looking ones first.
     */
    suspend fun bookingCandidates(trip: TripEntity): List<TxnEntity> =
        db.trips().bookingCandidates(
            trip.id, trip.startEpochDay - 365, trip.endEpochDay + 30, trip.startEpochDay, trip.endEpochDay,
        ).sortedByDescending { it.category == Category.TRAVEL || it.category == Category.HOUSING }

    /** Makes exactly [selected] (among [candidates]) the trip's linked bookings. */
    suspend fun setBookings(trip: TripEntity, candidates: List<TxnEntity>, selected: Set<Long>) {
        val link = candidates.filter { it.id in selected && it.tripId != trip.id }.map { it.id }
        val unlink = candidates.filter { it.id !in selected && it.tripId == trip.id }.map { it.id }
        if (link.isNotEmpty()) db.trips().linkTxns(trip.id, link)
        if (unlink.isNotEmpty()) db.trips().unlinkTxns(trip.id, unlink)
        WidgetRefresher.request(context)
    }

    suspend fun deleteTrip(trip: TripEntity) {
        db.trips().releaseAllTxns(trip.id)
        db.trips().deletePlacesOf(trip.id)
        db.trips().delete(trip)
    }

    suspend fun setCover(trip: TripEntity, uri: Uri) {
        val file = ledger.copyToAppStorage(uri, "covers")
        db.trips().update(trip.copy(coverPhotoPath = file.absolutePath))
    }

    /** Adds photos to the trip, each on the day it was taken (EXIF), and returns how many were added. */
    suspend fun importPhotos(trip: TripEntity, uris: List<Uri>): Int {
        var added = 0
        for (uri in uris) {
            val taken = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) }
                }.getOrNull()
            }
            val day = TripMath.dayForPhoto(taken, trip.range)
            if (runCatching { ledger.addPhoto(day.toEpochDay(), uri) }.isSuccess) added++
        }
        return added
    }

    suspend fun addPlace(tripId: Long, c: PlaceCandidate) {
        db.trips().insertPlace(TripPlaceEntity(tripId = tripId, name = c.name, countryCode = c.countryCode, lat = c.lat, lon = c.lon))
    }

    suspend fun deletePlace(place: TripPlaceEntity) = db.trips().deletePlace(place)

    /**
     * Countries from the bundled list (always available offline) followed by cities from the
     * platform geocoder (needs network and a device geocoder; silently skipped otherwise).
     */
    suspend fun searchPlaces(query: String): List<PlaceCandidate> {
        if (query.isBlank()) return emptyList()
        val countries = world().search(query, limit = 4).map {
            PlaceCandidate(it.name, it.iso2, it.label.lat, it.label.lon, "${it.flag} Country · ${it.continent}")
        }
        val cities = geocode(query).mapNotNull { a ->
            val cc = a.countryCode ?: return@mapNotNull null
            val name = a.locality ?: a.featureName ?: a.adminArea ?: return@mapNotNull null
            PlaceCandidate(
                name, cc.uppercase(), a.latitude, a.longitude,
                "${Countries.flag(cc)} " + listOfNotNull(a.adminArea?.takeIf { it != name }, a.countryName).joinToString(", "),
            )
        }
        return (countries + cities).distinctBy { "${it.name}|${it.countryCode}" }
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(query: String): List<Address> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        return withTimeoutOrNull(6_000) {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) { if (cont.isActive) cont.resume(addresses) }
                        override fun onError(errorMessage: String?) { if (cont.isActive) cont.resume(emptyList()) }
                    })
                }
            } else {
                withContext(Dispatchers.IO) { runCatching { geocoder.getFromLocationName(query, 5) }.getOrNull().orEmpty() }
            }
        }.orEmpty()
    }
}
