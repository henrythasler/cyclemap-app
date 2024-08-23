package com.henrythasler.cyclemap

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.ColorValue
import com.mapbox.maps.extension.compose.style.DoubleValue
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.extension.compose.style.layers.generated.LineCapValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineJoinValue
import com.mapbox.maps.extension.compose.style.layers.generated.LineLayer
import com.mapbox.maps.extension.compose.style.sources.GeoJSONData
import com.mapbox.maps.extension.compose.style.sources.generated.GeoJsonSourceState
import com.mapbox.maps.extension.compose.style.sources.generated.rememberGeoJsonSourceState
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location

@OptIn(MapboxExperimental::class, ExperimentalFoundationApi::class)
@Composable
fun CycleMapView(
    sharedState: SharedState,
    enableLocationService: () -> Unit,
    disableLocationService: () -> Unit
) {
    val styleUrl: String = stringResource(id = R.string.style_cyclemap_url)
    val mapState = rememberMapState {
        gesturesSettings = gesturesSettings.toBuilder()
            .setRotateEnabled(false)
            .setPitchEnabled(false)
//            .setFocalPoint(ScreenCoordinate(100.0, 200.0))
//            .focalPoint(pixelForCoordinate(mapViewportState.cameraState?.center))
            .build()
    }
//    val mapViewportState = remember { sharedState.mapViewportState }

    var requestLocationTracking by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }

    var distanceMeasurement by remember { mutableStateOf(false) }

    var trackLocation by remember { mutableStateOf(false) }
    var recordLocation by remember { mutableStateOf(false) }
    var useCustomStyle by remember { mutableStateOf(true) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showStyleCards by remember { mutableStateOf(false) }
    var permissionRequestCount by remember { mutableIntStateOf(1) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val geoJsonSource: GeoJsonSourceState = rememberGeoJsonSourceState {
        data = GeoJSONData(Point.fromLngLat(11.0, 48.0))
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = sharedState.mapViewportState,
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
            if (trackLocation) {
                MapEffect(Unit) { mapView ->
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck(withBearing = true)
                        enabled = true
                        showAccuracyRing = true
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                    }
//                    sharedState.mapViewportState.transitionToFollowPuckState(
//                        followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
//                            .bearing(null).padding(null).pitch(null).zoom(null).build()
//                    )
                }
            }
            if (distanceMeasurement) {
                LineLayer(
                    sourceState = geoJsonSource,
                    lineWidth = DoubleValue(7.0),
                    lineCap = LineCapValue.ROUND,
                    lineJoin = LineJoinValue.ROUND,
                    lineColor = ColorValue(Color.Red),
                    lineBorderWidth = DoubleValue(1.0),
                    lineBorderColor = ColorValue(Color.White),
                )
            }
        }

        // Listen for camera movements when tracking is enabled
        LaunchedEffect(distanceMeasurement) {
            if (distanceMeasurement) {
                snapshotFlow { sharedState.mapViewportState.cameraState!!.center }
                    .collect { center ->
                        val points = sharedState.distanceMeasurementPoints.toMutableList()
                        points.add(center)
                        geoJsonSource.data = GeoJSONData(LineString.fromLngLats(points))
                    }
            }
        }

        Image(
            modifier = Modifier
                .align(Alignment.Center)
                .alpha(0.5f)
                .combinedClickable(
                    onClick = {
                        distanceMeasurement = true
                        sharedState.mapViewportState.cameraState?.let {
                            sharedState.addPoint(it.center)
                        }
                        geoJsonSource.data =
                            GeoJSONData(LineString.fromLngLats(sharedState.distanceMeasurementPoints))
                    },
                    onDoubleClick = {
                        sharedState.clearPoints()
                        distanceMeasurement = false
                    }
                ),
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
            Row {
                SmallFloatingActionButton(
                    onClick = {
                        showMainMenu = !showMainMenu
                    },
                ) {
                    Icon(Icons.Filled.Menu, stringResource(R.string.button_menu_desc))
                }
                // Create a drop-down menu with list of cities,
                // when clicked, set the Text Field text as the city selected
                DropdownMenu(
                    expanded = showMainMenu,
                    offset = DpOffset(48.dp, 0.dp),
                    onDismissRequest = { showMainMenu = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.menu_map_style))
                        },
                        onClick = {
                            showMainMenu = false
                            showStyleCards = true
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.CheckCircle, stringResource(R.string.menu_map_style))
                        })
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    if (locationPermission) {
                        trackLocation = !trackLocation
                    } else {
                        requestLocationTracking = true
                    }
                },
            ) {
                Icon(Icons.Filled.LocationOn, stringResource(R.string.button_location_desc))
            }

            SmallFloatingActionButton(
                onClick = {
                    useCustomStyle = !useCustomStyle
                },
            ) {
                Icon(Icons.Filled.Search, stringResource(R.string.button_search_desc))
            }
        }

        if (requestLocationTracking) {
            RequestLocationPermission(
                requestCount = permissionRequestCount,
                onPermissionDenied = {
                    Log.i(TAG, "Permission not granted")
                    showRequestPermissionButton = true
                    locationPermission = false
                    trackLocation = false
                },
                onPermissionReady = {
                    Log.i(TAG, "Permission granted")
                    showRequestPermissionButton = false
                    locationPermission = true
                    trackLocation = true
                }
            )
        }

        if (showRequestPermissionButton) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                Button(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { permissionRequestCount += 1 }
                ) {
                    Text("Request permission again ($permissionRequestCount)")
                }
            }
        }

        if (showStyleCards) {
            StyleCards()
        }
    }
}
