package com.oinky.app.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.oinky.core.CountryShape
import com.oinky.core.GeoPoint
import com.oinky.core.Mercator
import com.oinky.core.WorldMapData
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class MapPin(val id: Long, val point: GeoPoint, val color: Color)

/** Map coordinates are Web-Mercator scaled to a 1000-unit wide world. */
private const val WORLD = 1000f
private fun wx(lon: Double) = (Mercator.x(lon) * WORLD).toFloat()
private fun wy(lat: Double) = (Mercator.y(lat) * WORLD).toFloat()

private class CountryPath(val shape: CountryShape, val path: Path)

/**
 * Offline world map drawn on a Canvas from the bundled outlines.
 *
 *  - [fills]: ISO code -> fill colour (visited countries, the selected one…)
 *  - [pins]: trip places; tapping one calls [onPinTap], tapping land calls [onCountryTap]
 *  - [focus]: points to frame initially (a trip's places); empty frames the whole world.
 *
 * Pinch to zoom, drag to pan, double-tap to zoom in.
 */
@Composable
fun WorldMap(
    world: WorldMapData?,
    fills: Map<String, Color>,
    landColor: Color,
    borderColor: Color,
    oceanColor: Color,
    modifier: Modifier = Modifier,
    pins: List<MapPin> = emptyList(),
    focus: List<GeoPoint> = emptyList(),
    onCountryTap: (CountryShape?) -> Unit = {},
    onPinTap: (MapPin) -> Unit = {},
) {
    val paths = remember(world) {
        world?.countries.orEmpty()
            .filter { it.iso2 != "AQ" } // Antarctica dominates a Mercator map; nobody logs trips there… mostly.
            .map { c ->
                val p = Path()
                c.rings.forEach { r ->
                    p.moveTo(wx(r[0]), wy(r[1]))
                    for (i in 1 until r.size / 2) p.lineTo(wx(r[2 * i]), wy(r[2 * i + 1]))
                    p.close()
                }
                CountryPath(c, p)
            }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var minScale by remember { mutableFloatStateOf(0.1f) }

    // Fit the focus points (or the inhabited world) whenever the frame or the size changes.
    LaunchedEffect(size, focus) {
        if (size == IntSize.Zero) return@LaunchedEffect
        val worldTop = wy(84.0)
        val worldBottom = wy(-58.0)
        minScale = min(size.width / WORLD, size.height / (worldBottom - worldTop))
        val (l, t, r, b) = if (focus.isEmpty()) {
            listOf(0f, worldTop, WORLD, worldBottom)
        } else {
            val xs = focus.map { wx(it.lon) }
            val ys = focus.map { wy(it.lat) }
            // Pad and keep a sensible minimum extent so one city doesn't zoom to street level.
            val cx = (xs.min() + xs.max()) / 2
            val cy = (ys.min() + ys.max()) / 2
            val w = max((xs.max() - xs.min()) * 1.6f, 40f)
            val h = max((ys.max() - ys.min()) * 1.6f, 40f)
            listOf(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        }
        scale = min(size.width / (r - l), size.height / (b - t))
        offset = Offset(size.width / 2f - (l + r) / 2 * scale, size.height / 2f - (t + b) / 2 * scale)
    }

    val currentPins by rememberUpdatedState(pins)
    val currentPaths by rememberUpdatedState(paths)
    val countryTap by rememberUpdatedState(onCountryTap)
    val pinTap by rememberUpdatedState(onPinTap)

    fun zoomAround(anchor: Offset, factor: Float) {
        val newScale = (scale * factor).coerceIn(minScale, minScale * 60f)
        offset = anchor - (anchor - offset) * (newScale / scale)
        scale = newScale
    }

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    zoomAround(centroid, zoom)
                    offset += pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { zoomAround(it, 2.5f) },
                    onTap = { tap ->
                        val hitPin = currentPins.minByOrNull { pin ->
                            hypot(wx(pin.point.lon) * scale + offset.x - tap.x, wy(pin.point.lat) * scale + offset.y - tap.y)
                        }?.takeIf { pin ->
                            hypot(wx(pin.point.lon) * scale + offset.x - tap.x, wy(pin.point.lat) * scale + offset.y - tap.y) < 28.dp.toPx()
                        }
                        if (hitPin != null) {
                            pinTap(hitPin)
                        } else {
                            val mx = (tap.x - offset.x) / scale / WORLD
                            val my = (tap.y - offset.y) / scale / WORLD
                            val lon = Mercator.lon(mx.toDouble())
                            val lat = Mercator.lat(my.toDouble())
                            countryTap(
                                currentPaths.map { it.shape }.filter { it.contains(lon, lat) }
                                    .minByOrNull { (it.maxLon - it.minLon) * (it.maxLat - it.minLat) },
                            )
                        }
                    },
                )
            },
    ) {
        drawRect(oceanColor)
        withTransform({
            translate(offset.x, offset.y)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            val hairline = Stroke(width = 0.8f / scale)
            paths.forEach { cp ->
                drawPath(cp.path, fills[cp.shape.iso2] ?: landColor)
                drawPath(cp.path, borderColor, style = hairline)
            }
        }
        val r = 7.dp.toPx()
        pins.forEach { pin ->
            val c = Offset(wx(pin.point.lon) * scale + offset.x, wy(pin.point.lat) * scale + offset.y)
            drawCircle(Color.White, radius = r + 2.5f, center = c)
            drawCircle(pin.color, radius = r, center = c)
        }
    }
}
