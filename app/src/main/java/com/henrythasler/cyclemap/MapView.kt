package com.henrythasler.cyclemap

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.Style
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions

@OptIn(MapboxExperimental::class)
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
    var requestLocationTracking by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }
    var trackLocation by remember { mutableStateOf(false) }
    var recordLocation by remember { mutableStateOf(false) }
    var useCustomStyle by remember { mutableStateOf(true) }
    var showCircleMenu by remember { mutableStateOf(false) }
    var permissionRequestCount by remember { mutableIntStateOf(1) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }
    val context = LocalContext.current

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
            Row {
                SmallFloatingActionButton(
                    onClick = {
                        showCircleMenu = !showCircleMenu
                    },
                ) {
                    Icon(Icons.Filled.Menu, stringResource(R.string.button_menu_desc))
                }
                if (showCircleMenu) {
                    SemiCircleButtons(
                        buttons = listOf(
                            "Button 1",
                            "Button 2",
                            "Button 3",
                            "Button 4",
                            "Button 5"
                        ),
                        radius = 128f,
                        startAngle = -60f,
                        endAngle = 60f
                    )
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

            if (trackLocation) {
                SmallFloatingActionButton(
                    onClick = {
                        recordLocation = !recordLocation
                        if(recordLocation) {
                            enableLocationService()
                        }
                        else {
                            disableLocationService()
                        }
                    },
                ) {
                    Icon(Icons.Filled.AddCircle, stringResource(R.string.button_record_desc))
                }
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
    }
}
