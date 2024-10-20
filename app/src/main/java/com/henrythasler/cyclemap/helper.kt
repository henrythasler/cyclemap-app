package com.henrythasler.cyclemap

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.Style
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun getFormattedDistance(distance: Double): String {
    return if (distance > 5000) {
        DecimalFormat("#.0 km").format(distance / 1000)
    } else {
        DecimalFormat("# m").format(distance)
    }
}

fun getFormattedLocation(point: Point?, decimals: Int = 2): String {
    point?.let {
        val lat = BigDecimal(it.latitude()).setScale(decimals, RoundingMode.HALF_UP)
        val lon = BigDecimal(it.longitude()).setScale(decimals, RoundingMode.HALF_UP)
        return "${lat}°, ${lon}°"
    }
    return ""
}

fun getFormattedDateTime(): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return LocalDateTime.now().format(formatter)
}

fun measureDistance(lineString: List<Point>): Double {
    return if (lineString.isNotEmpty()) TurfMeasurement.length(
        LineString.fromLngLats(lineString),
        TurfConstants.UNIT_METERS
    ) else 0.0
}

fun locationToPoints(locations: List<Location>): List<Point> {
    val points: MutableList<Point> = mutableListOf()
    locations.forEach { location ->
        points.add(Point.fromLngLat(location.longitude, location.latitude))
    }
    return points
}

fun resolveStyleSource(styleDefinition: StyleDefinition?): String? {
    styleDefinition?.let {
        if (styleDefinition.styleSource.startsWith("http")) {
            return styleDefinition.styleSource
        } else {
            mapboxStyleIdMapping[styleDefinition.styleSource]?.let { return it }
        }
    }
    return null
}

fun resolveStyleId(styleDefinitions: List<StyleDefinition>, id: String?): String? {
    val styleDefinition = styleDefinitions.find { it.styleId == id }
    styleDefinition?.let { return resolveStyleSource(styleDefinition) }
    return null
}

data class StyleDefinition(
    val styleName: String,
    val styleDescription: String?,
    val styleId: String,
    val styleSource: String,
    val drawable: String,
)

fun parseStyleDefinitions(context: Context): List<StyleDefinition> {
    val inputStream = context.resources.openRawResource(R.raw.styles)
    val jsonString = inputStream.bufferedReader().use { it.readText() }
    return Gson().fromJson(jsonString, object : TypeToken<List<StyleDefinition>>() {}.type)
}

val mapboxStyleIdMapping = mapOf(
    "STANDARD" to Style.STANDARD,
    "OUTDOORS" to Style.OUTDOORS,
    "SATELLITE" to Style.SATELLITE,
    "MAPBOX_STREETS" to Style.MAPBOX_STREETS,
    "TRAFFIC_DAY" to Style.TRAFFIC_DAY,
)

// Function to crop the center of a Bitmap
fun cropBitmapToCenter(
    bitmap: Bitmap,
    width: Int,
    height: Int,
    center: ScreenCoordinate? = null
): Bitmap {
    val x = (center?.x?.toInt() ?: bitmap.width) - width / 2
    val y = (center?.y?.toInt() ?: bitmap.height) - height / 2

    Log.d(TAG, center.toString())

    // Ensure the crop area doesn't exceed the original bitmap's bounds
    val cropWidth = width.coerceAtMost(bitmap.width)
    val cropHeight = height.coerceAtMost(bitmap.height)

    return Bitmap.createBitmap(
        bitmap,
        x.coerceAtLeast(0),
        y.coerceAtLeast(0),
        cropWidth,
        cropHeight
    )
}

fun Bitmap.cropAroundCenter(
    centerX: Int,
    centerY: Int,
    targetWidth: Int,
    targetHeight: Int
): ImageBitmap {
    // Ensure target dimensions are positive
    require(targetWidth > 0 && targetHeight > 0) {
        "Target dimensions must be positive"
    }

    // Calculate the crop boundaries
    val halfWidth = targetWidth / 2
    val halfHeight = targetHeight / 2

    val left = (centerX.coerceIn(halfWidth, this.width - halfWidth) - halfWidth).coerceAtLeast(0)
    val top = (centerY.coerceIn(halfHeight, this.height - halfHeight) - halfHeight).coerceAtLeast(0)
    val right =
        (centerX.coerceIn(halfWidth, this.width - halfWidth) + halfWidth).coerceAtMost(width)
    val bottom =
        (centerY.coerceIn(halfHeight, this.height - halfHeight) + halfHeight).coerceAtMost(height)

    // Create the cropped bitmap
    return Bitmap.createBitmap(this, left, top, right - left, bottom - top).asImageBitmap()
}