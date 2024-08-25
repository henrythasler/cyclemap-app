package com.henrythasler.cyclemap

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
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
    return TurfMeasurement.length(
        LineString.fromLngLats(lineString),
        TurfConstants.UNIT_METERS
    )
}
