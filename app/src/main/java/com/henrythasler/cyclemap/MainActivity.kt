package com.henrythasler.cyclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle

class MainActivity : ComponentActivity() {
    @OptIn(MapboxExperimental::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MapboxMap(Modifier.fillMaxSize(), mapViewportState = MapViewportState().apply {
                setCameraOptions {
                    zoom(12.0)
                    center(Point.fromLngLat(10.85, 48.05))
                    pitch(0.0)
                    bearing(0.0)
                }
            }, style = {
                MapboxStandardStyle()
            })
        }
    }
}