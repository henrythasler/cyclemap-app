package com.henrythasler.cyclemap

import androidx.lifecycle.ViewModel
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CycleMapViewModel : ViewModel() {
    @OptIn(MapboxExperimental::class)
    private val _mapViewportState = MutableStateFlow(MapViewportState())
    @OptIn(MapboxExperimental::class)
    val mapViewportState: StateFlow<MapViewportState> = _mapViewportState.asStateFlow()

    @OptIn(MapboxExperimental::class)
    fun updateMapViewport(newState: MapViewportState) {
        _mapViewportState.value = newState
    }

    // Add other map-related operations here
}