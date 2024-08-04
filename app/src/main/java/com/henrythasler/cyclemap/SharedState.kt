package com.henrythasler.cyclemap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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
    // Add other shared variables here
}