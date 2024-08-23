package com.henrythasler.cyclemap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState

class SharedState {
    @OptIn(MapboxExperimental::class)
    private val _mapViewportState: MutableState<MapViewportState> = mutableStateOf(MapViewportState())

    @OptIn(MapboxExperimental::class)
    var mapViewportState: MapViewportState
        get() = _mapViewportState.value
        set(value) {
            _mapViewportState.value = value
        }

    private val _distanceMeasurementPoints: MutableState<List<Point>> = mutableStateOf(listOf())
    var distanceMeasurementPoints: List<Point>
        get() = _distanceMeasurementPoints.value
        set(value) {
            _distanceMeasurementPoints.value = value
        }

    fun addPoint(point: Point) {
        _distanceMeasurementPoints.value = _distanceMeasurementPoints.value + point
    }

    fun clearPoints() {
        _distanceMeasurementPoints.value = listOf()
    }
}