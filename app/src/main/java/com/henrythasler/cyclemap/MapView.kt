package com.henrythasler.cyclemap

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.common.location.DeviceLocationProvider
import com.mapbox.common.location.Location
import com.mapbox.common.location.LocationObserver
import com.mapbox.common.location.LocationServiceFactory
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.ScreenCoordinate
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
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions

@OptIn(MapboxExperimental::class, ExperimentalFoundationApi::class)
@Composable
@Preview
fun CycleMapView(
    sharedState: SharedState = SharedState(),
    enableLocationService: () -> Unit = {},
    disableLocationService: () -> Unit = {}
) {
    val mapState = rememberMapState {
        gesturesSettings = gesturesSettings.toBuilder()
            .setRotateEnabled(false)
            .setPitchEnabled(false)
            .build()
    }
//    val trackPoints = remember { sharedState.trackPoints }
//    val styleUrl: String = stringResource(id = R.string.style_cyclemap_url)
    var styleUrl by remember { mutableStateOf<String>("https://www.cyclemap.link/cyclemap-style.json") }
    var currentStyleId by remember { mutableStateOf<String>("cyclemap") }

    var requestLocationTracking by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }

    var distanceMeasurementPoints by remember { mutableStateOf(listOf<Point>()) }
    var distanceMeasurement by remember { mutableStateOf(false) }
    var distance by remember { mutableDoubleStateOf(0.0) }

    var showRoute by remember { mutableStateOf(false) }
    var trackLocation by remember { mutableStateOf(false) }
    var followLocation by remember { mutableStateOf(false) }
    var recordLocation by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showStyleSelection by remember { mutableStateOf(false) }
    var permissionRequestCount by remember { mutableIntStateOf(1) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }
    var lastClick by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current

    val mapboxLocationService: com.mapbox.common.location.LocationService =
        LocationServiceFactory.getOrCreate()
    var locationProvider: DeviceLocationProvider? = null

    val distanceMeasurementLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val routeLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val trackLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val styleDefinitions: List<StyleDefinition> = parseStyleDefinitions(context)

    // File handling
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            Log.i(TAG, "selected file: ${it.path}")
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (LocalInspectionMode.current) {
            Image(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                painter = painterResource(id = R.drawable.map_preview),
                contentDescription = ""
            )
        } else {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = sharedState.mapViewportState,
                mapState = mapState,
                style = {
                    MapStyle(style = styleUrl)
                },
                scaleBar = {
                    ScaleBar(
                        Modifier.padding(bottom = 48.dp),
                        alignment = Alignment.BottomEnd,
                        height = 5.dp,
                        borderWidth = 1.dp,
                        isMetricUnit = true,
                        textSize = 14.sp,
                    )
                },
            ) {
                MapEffect(key1 = trackLocation, Unit) { mapView ->
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck(withBearing = true)
                        enabled = trackLocation
                        showAccuracyRing = true
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                    }

                    if (trackLocation) {
                        sharedState.mapViewportState.transitionToFollowPuckState(
                            followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                                .bearing(null).padding(null).pitch(null).zoom(null).build()
                        )

                        val result = mapboxLocationService.getDeviceLocationProvider(null)
                        if (result.isValue) {
                            locationProvider = result.value!!
                            locationProvider!!.addLocationObserver { location ->
                                Log.i(
                                    TAG,
                                    "Location update received: ${location.last().speed?.times(3.6)} km/h"
                                )
                            }
                            Log.i(TAG, "location provider: ${locationProvider!!.getName()}")
                        } else {
                            Log.e(TAG, "Failed to get device location provider")
                        }
                    } else {
                        sharedState.mapViewportState.idle()
                    }
                }

                if (distanceMeasurement) {
                    LineLayer(
                        sourceState = distanceMeasurementLayer,
                        lineWidth = DoubleValue(7.0),
                        lineCap = LineCapValue.ROUND,
                        lineJoin = LineJoinValue.ROUND,
                        lineColor = ColorValue(colorResource(R.color.distanceMeasurementLine)),
                        lineBorderWidth = DoubleValue(1.0),
                        lineBorderColor = ColorValue(colorResource(R.color.distanceMeasurementLineCasing)),
                    )
                }
                if (showRoute) {
                    LineLayer(
                        sourceState = routeLayer,
                        lineWidth = DoubleValue(11.0),
                        lineOpacity = DoubleValue(0.75),
                        lineCap = LineCapValue.ROUND,
                        lineJoin = LineJoinValue.ROUND,
                        lineColor = ColorValue(colorResource(R.color.routeLine)),
                        lineBorderWidth = DoubleValue(1.0),
                        lineBorderColor = ColorValue(colorResource(R.color.routeLineCasing)),
                    )
                }

                // always show the recorded track for future reference even after recording was stopped
                LineLayer(
                    sourceState = trackLayer,
                    lineWidth = DoubleValue(11.0),
                    lineOpacity = DoubleValue(0.75),
                    lineCap = LineCapValue.ROUND,
                    lineJoin = LineJoinValue.ROUND,
                    lineColor = ColorValue(colorResource(R.color.trackLine)),
                    lineBorderWidth = DoubleValue(1.0),
                    lineBorderColor = ColorValue(colorResource(R.color.trackLineCasing)),
                )
            }
        }

        // Listen for camera movements when tracking is enabled
        LaunchedEffect(distanceMeasurement) {
            if (distanceMeasurement) {
                snapshotFlow { sharedState.mapViewportState.cameraState!!.center }
                    .collect { center ->
                        // add current position temporarily to calculate the distance while dragging
                        val points = distanceMeasurementPoints.toMutableList()
                        points.add(center)
                        distance = measureDistance(points)
                        distanceMeasurementLayer.data = GeoJSONData(LineString.fromLngLats(points))
                    }
            }
        }

        LaunchedEffect(key1 = sharedState.trackPoints) {
            snapshotFlow { sharedState.trackPoints }
                .collect { points ->
                    trackLayer.data = GeoJSONData(LineString.fromLngLats(points))
                }
        }

        // Crosshair and Distance Measurement
        BadgedBox(
            modifier = Modifier
                .align(Alignment.Center),
            badge = {
                if (distanceMeasurement) {
                    Badge(
                        containerColor = colorResource(R.color.distanceMeasurementBadgeBackground),
                        contentColor = Color.Black
                    ) {
                        DistanceBadge(distance)
                    }
                }
            }
        ) {
            Image(
                modifier = Modifier
                    .alpha(0.5f)
                    .clickable {
                        // manual double-click evaluation since combinedClickable() introduces a delay for normal clicks
                        if (System.currentTimeMillis() - lastClick <= 300L) {
                            distanceMeasurementPoints = listOf()
                            distanceMeasurement = false
                        } else {
                            distanceMeasurement = true
                            sharedState.mapViewportState.cameraState?.let {
                                distanceMeasurementPoints = distanceMeasurementPoints + it.center
                            }
                            distance = measureDistance(distanceMeasurementPoints)
                            distanceMeasurementLayer.data =
                                GeoJSONData(LineString.fromLngLats(distanceMeasurementPoints))
                        }
                        lastClick = System.currentTimeMillis()
                    }
                    .onGloballyPositioned { coordinates ->
                        // This will give us the screen coordinates
                        val screenPos = coordinates.boundsInWindow()
                        Log.i(TAG, "Crosshair Center: $screenPos")
                        mapState.gesturesSettings = mapState.gesturesSettings
                            .toBuilder()
                            .setFocalPoint(
                                ScreenCoordinate(
                                    screenPos.center.x.toDouble(),
                                    screenPos.center.y.toDouble()
                                )
                            )
                            .build()
                    },
                painter = painterResource(id = R.drawable.my_location_48px),
                contentDescription = "Crosshair",
            )
        }

        // Menu
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
                            showStyleSelection = true
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(id = R.drawable.baseline_map_24),
                                stringResource(R.string.menu_map_style)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.menu_gpx_load))
                        },
                        onClick = {
                            showMainMenu = false
                            launcher.launch("*/*")
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(id = R.drawable.baseline_directions_24),
                                stringResource(R.string.menu_gpx_load)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.menu_gpx_save))
                        },
                        onClick = {
                            showMainMenu = false
                            // TODO: Implement GPX writer
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(id = R.drawable.baseline_save_alt_24),
                                stringResource(R.string.menu_gpx_save)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(text = stringResource(R.string.menu_about))
                        },
                        onClick = {
                            showMainMenu = false
                            showAbout = true
                        },
                        leadingIcon = {
                            Icon(
                                painterResource(id = R.drawable.baseline_info_24),
                                stringResource(R.string.menu_about)
                            )
                        }
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

                    if (!trackLocation) {
                        recordLocation = false
                        disableLocationService()
                    }
                },
            ) {
                Icon(Icons.Filled.LocationOn, stringResource(R.string.button_location_desc))
            }

            SmallFloatingActionButton(
                onClick = {
                },
            ) {
                Icon(Icons.Filled.Search, stringResource(R.string.button_search_desc))
            }

            if (trackLocation) {
                SmallFloatingActionButton(
                    onClick = {
                        recordLocation = !recordLocation
                        if (recordLocation) {
                            enableLocationService()
                        } else {
                            disableLocationService()
                        }
                    },
                ) {
                    Icon(
                        tint = if (recordLocation) Color.Red else Color.Unspecified,
                        painter = painterResource(id = R.drawable.baseline_fiber_manual_record_24),
                        contentDescription = stringResource(R.string.button_record_desc)
                    )
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

        if (showStyleSelection) {
            StyleSelectionSheet(
                onDismiss = {
                    showStyleSelection = false
                },
                styleDefinitions = styleDefinitions,
                currentStyle = currentStyleId
            )
            { styleDefinition ->
                Log.i(TAG, "selected $styleDefinition")
                currentStyleId = styleDefinition.styleId
                if (styleDefinition.styleSource.startsWith("http")) {
                    styleUrl = styleDefinition.styleSource
                } else {
                    mapboxStyleIdMapping[styleDefinition.styleSource]?.let { styleUrl = it }
                }
                showStyleSelection = false
            }
        }

        if (trackLocation) {
//            val speed = mapState.
            Text(
                modifier = Modifier
                    .background(Color.White)
                    .align(Alignment.TopCenter)
                    .padding(4.dp),
                text = "XXX km/h"
            )
        }
    }

    if (showAbout) {
        AlertDialog(
            title = {
                Text(text = "CycleMap")
            },
            text = {
                val sdk = Build.VERSION.SDK_INT // SDK version
//                val versionName = BuildConfig.VERSION_NAME // App version name from BuildConfig
//                val packageName = BuildConfig.APPLICATION_ID // App package name from BuildConfig
//                val build = BuildConfig.VERSION_CODE // Build type (debug/release) from BuildConfig
                Text(text = "API $sdk")
            },
            onDismissRequest = {
                showAbout = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAbout = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    selectedUri?.let { uri ->
        ReadSelectedGpx(uri) { gpx ->
            // process GPX data after loading
            val route: MutableList<Point> = mutableListOf()

            gpx.track?.segments?.forEach { segment ->
                segment.trackPoints?.forEach { point ->
                    route.add(Point.fromLngLat(point.longitude, point.latitude))
                }
            }

            if (route.size > 1) {
                val routeGeometry = LineString.fromLngLats(route)
                routeLayer.data =
                    GeoJSONData(routeGeometry)
                showRoute = true
                sharedState.mapViewportState.transitionToOverviewState(
                    overviewViewportStateOptions = OverviewViewportStateOptions.Builder()
                        .geometry(routeGeometry)
                        .padding(EdgeInsets(100.0, 100.0, 100.0, 100.0))
                        .build()
                )
            }
        }
    }
}

@Composable
fun DistanceBadge(distance: Double) {
    Text(
        modifier = Modifier.padding(3.dp),
        fontSize = 18.sp,
        fontStyle = FontStyle.Italic,
        text = getFormattedDistance(distance)
    )
}
