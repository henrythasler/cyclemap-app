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

    private val _trackPoints: MutableState<List<Point>> = mutableStateOf(listOf())
    var trackPoints: List<Point>
        get() = _trackPoints.value
        set(value) {
            _trackPoints.value = value
        }

    fun addTrackPoint(point: Point) {
        _trackPoints.value = _trackPoints.value + point
    }

    fun clearTrackPoints() {
        _trackPoints.value = listOf()
    }
}