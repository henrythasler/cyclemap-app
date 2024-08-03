package com.henrythasler.cyclemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.MapStyle

@OptIn(MapboxExperimental::class)
@Composable
fun CyclemapView(styleUrl: String = stringResource(id = R.string.style_cyclemap_url)) {
    val mapViewportState = rememberMapViewportState() {
        setCameraOptions {
            zoom(12.0)
            center(Point.fromLngLat(10.85, 48.05))
            pitch(0.0)
            bearing(0.0)
        }
    }
    val mapState = rememberMapState() {
        gesturesSettings = gesturesSettings.toBuilder()
            .setRotateEnabled(false)
            .setPitchEnabled(false)
//            .setFocalPoint(ScreenCoordinate(100.0, 200.0))
//            .focalPoint(pixelForCoordinate(mapViewportState.cameraState?.center))
            .build()
    }
    var useCustomStyle by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            mapState = mapState,
            style = {
                if (useCustomStyle) {
                    MapStyle(style = styleUrl)
                } else {
                    MapStyle(style = Style.STANDARD)
                }
            },
            scaleBar = {
                ScaleBar(
                    Modifier.padding(bottom = 48.dp),
                    alignment = Alignment.BottomStart,
                    height = 5.dp,
                    borderWidth = 1.dp,
                    isMetricUnit = true,
                    textSize = 14.sp,
                )
            },
        ) {
            // do stuff if needed
        }

        Image(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(0.5f),
            painter = painterResource(id = R.drawable.my_location_48px),
            contentDescription = "",
        )

        val padding = 8.dp
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            SmallFloatingActionButton(
                onClick = {
                    useCustomStyle = !useCustomStyle
                },
            ) {
                Icon(Icons.Filled.Home, "Small floating action button.")
            }

            SmallFloatingActionButton(
                onClick = {
                    useCustomStyle = !useCustomStyle
                },
            ) {
                Icon(Icons.Filled.LocationOn, "Small floating action button.")
            }

            SmallFloatingActionButton(
                onClick = {
                    useCustomStyle = !useCustomStyle
                },
            ) {
                Icon(Icons.Filled.Add, "Small floating action button.")
            }
        }
    }
}
