package com.henrythasler.cyclemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.MapViewportState
import com.mapbox.maps.extension.compose.style.MapStyle

@OptIn(MapboxExperimental::class)
@Preview
@Composable
fun CyclemapView(styleUrl: String = stringResource(id = R.string.style_cyclemap_url)) {
    val mapViewportState = remember {
        MapViewportState().apply {
            setCameraOptions {
                zoom(12.0)
                center(Point.fromLngLat(10.85, 48.05))
                pitch(0.0)
                bearing(0.0)
            }
        }
    }
    var useCustomStyle by remember { mutableStateOf(true) }
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                if (useCustomStyle) {
                    MapStyle(style = styleUrl)
                } else {
                    MapStyle(style = Style.STANDARD)
                }
            },
            scaleBar = {
                ScaleBar(
                    alignment = Alignment.BottomEnd,
                    height = 5.dp,
                    borderWidth = 1.dp,
                    isMetricUnit = true,
                )
            },
        )

        // A button to toggle between styles
        Button(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            onClick = { useCustomStyle = !useCustomStyle },
        ) {
            Text("Toggle Style")
        }
    }
}
