package com.henrythasler.cyclemap

import android.content.Context
import android.location.Location
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.Style
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import java.text.DecimalFormat

fun getFormattedDistance(distance: Double): String {
    return if (distance > 5000) {
        DecimalFormat("#.0 km").format(distance / 1000)
    } else {
        DecimalFormat("# m").format(distance)
    }
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

data class StyleDefinition(
    val styleName: String,
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