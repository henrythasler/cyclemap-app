package com.henrythasler.cyclemap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.common.location.DeviceLocationProvider
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
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.DefaultViewportTransitionOptions
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.mapbox.maps.plugin.viewport.data.OverviewViewportStateOptions
import android.content.ServiceConnection
import android.location.Location
import androidx.compose.runtime.rememberCoroutineScope
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout
import kotlinx.coroutines.launch

@OptIn(MapboxExperimental::class)
@Composable
@Preview
fun CycleMapView(
    sharedState: SharedState = SharedState(),
) {
    val mapState = rememberMapState {
        gesturesSettings = gesturesSettings.toBuilder()
            .setRotateEnabled(false)
            .setPitchEnabled(false)
            .build()
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInsets = WindowInsets.systemBars.asPaddingValues()

    var styleUrl by remember { mutableStateOf("https://www.cyclemap.link/cyclemap-style.json") }
    var currentStyleId by remember { mutableStateOf("cyclemap") }

    var requestLocationTracking by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }

    var distanceMeasurementPoints by remember { mutableStateOf(listOf<Point>()) }
    var distanceMeasurement by remember { mutableStateOf(false) }
    var distance by remember { mutableDoubleStateOf(0.0) }

    var trackLocations by remember { mutableStateOf(listOf<Location>()) }
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
    var locationService by remember { mutableStateOf<LocationService?>(null) }
    var locationServiceBound by remember { mutableStateOf(false) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as LocationService.LocalBinder
                locationService = binder.getService()
                locationServiceBound = true
                Log.i(TAG, "onServiceConnected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                locationService = null
                locationServiceBound = false
                Log.i(TAG, "onServiceDisconnected")
            }
        }
    }

    var currentSpeed by remember { mutableDoubleStateOf(0.0) }
    var routeDistance by remember { mutableDoubleStateOf(0.0) }
    var waypointCount by remember { mutableIntStateOf(0) }

    val mapboxLocationService: com.mapbox.common.location.LocationService =
        LocationServiceFactory.getOrCreate()
    var locationProvider: DeviceLocationProvider? = null

    val distanceMeasurementLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val routeLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val trackLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val styleDefinitions: List<StyleDefinition> = parseStyleDefinitions(context)

    // File handling
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            Log.i(TAG, "selected file: ${it.path}")
        }
    }

    val saveTrack = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            Log.i(TAG, "saving track to: ${it.path}")
            coroutineScope.launch {
                val trackSegment: MutableList<TrackPoint> = mutableListOf()
                trackLocations.forEach { point ->
                    trackSegment.add(TrackPoint().apply {
                        latitude = point.latitude
                        longitude = point.longitude
                        elevation = point.altitude
                        time = point.time.toString()
                    })
                }
                writeGpx(context, trackSegment, uri)
            }
        }
    }

    val saveRoute = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            Log.i(TAG, "saving route to: ${it.path}")
            coroutineScope.launch {
                val trackSegment: MutableList<TrackPoint> = mutableListOf()
                distanceMeasurementPoints.forEach { point ->
                    trackSegment.add(TrackPoint().apply {
                        latitude = point.latitude()
                        longitude = point.longitude()
                    })
                }
                writeGpx(context, trackSegment, uri)
            }
        }
    }

    // location stuff
    val locationObserver = remember {
        LocationObserver { location ->
            location.last().speed?.times(3.6)?.let { currentSpeed = it }
        }
    }

    val onMoveListener = remember {
        object : OnMoveListener {
            override fun onMoveBegin(detector: MoveGestureDetector) {
                if (followLocation) Log.d(TAG, "tracking disabled")
                followLocation = false
            }

            override fun onMove(detector: MoveGestureDetector): Boolean {
                return false // Return true to consume the event, false to propagate it
            }

            override fun onMoveEnd(detector: MoveGestureDetector) {
            }
        }
    }

    fun enableLocationService() {
        /** start the location service to record the track */
        Intent(context, LocationService::class.java).also { intent ->
            Log.i(TAG, "enabling LocationService")
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun disableLocationService() {
        if (locationServiceBound) {
            Log.i(TAG, "disabling LocationService")
            context.unbindService(connection)
            locationServiceBound = false
            locationService = null
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
                        Modifier.padding(windowInsets),
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
                        followLocation = true

                        Log.d(TAG, "addOnMoveListener")
                        mapView.gestures.addOnMoveListener(onMoveListener)

                        // request location updates for current speed indicator
                        locationProvider =
                            mapboxLocationService.getDeviceLocationProvider(null).value
                        locationProvider?.run {
                            addLocationObserver(locationObserver)
                            Log.i(TAG, "location provider: ${getName()}")
                        }
                    } else {
                        sharedState.mapViewportState.idle()
                        Log.d(TAG, "removeOnMoveListener")
                        mapView.gestures.removeOnMoveListener(onMoveListener)
                        locationProvider?.run {
                            removeLocationObserver(locationObserver)
                        }
                    }
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

        LaunchedEffect(locationServiceBound) {
            if (locationServiceBound) {
                locationService?.currentLocation?.collect { location ->
                    location?.let {
                        trackLocations += location
                        trackLayer.data =
                            GeoJSONData(LineString.fromLngLats(locationToPoints(trackLocations)))
                    }
                }
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
        Column(
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(windowInsets),
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

                MainMenu(
                    showMainMenu,
                    onDismissRequest = {
                        showMainMenu = false
                    },
                    onSelectMapStyle = {
                        showMainMenu = false
                        showStyleSelection = true
                    },
                    onLoadGpx = {
                        showMainMenu = false
                        launcher.launch("*/*")
                    },
                    onClearRoute = {
                        showMainMenu = false
                        showRoute = false
                        routeLayer.data = GeoJSONData("")
                    },
                    onSaveGpx = {
                        showMainMenu = false
                        saveTrack.launch("track.gpx")
                    },
                    onDeleteTrack = {
                        showMainMenu = false
                        trackLocations = listOf()
                        trackLayer.data = GeoJSONData("")
                    },
                    onAbout = {
                        showMainMenu = false
                        showAbout = true
                    },
                    onScreenshot = {
                        showMainMenu = false
                        followLocation = false
                        sharedState.mapViewportState.setCameraOptions {
                            center(Point.fromLngLat(10.897498, 48.279076))
                            zoom(14.87486)
                            pitch(0.0)
                            bearing(0.0)
                        }
                    }
                )
            }

            SmallFloatingActionButton(
                onClick = {
                    if (trackLocation) {
                        if (!followLocation) {
                            sharedState.mapViewportState.transitionToFollowPuckState(
                                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                                    .bearing(null).padding(null).pitch(null).zoom(null).build(),
                                defaultTransitionOptions = DefaultViewportTransitionOptions.Builder()
                                    .maxDurationMs(100).build()
                            )
                            followLocation = true
                        } else {
                            trackLocation = false
                            recordLocation = false
                            disableLocationService()
                        }
                    } else {
                        if (locationPermission) {
                            trackLocation = true
                        } else {
                            requestLocationTracking = true
                        }
                    }
                },
            ) {
                Icon(
                    tint = if (trackLocation) colorResource(R.color.locationActiveTint) else Color.Unspecified,
                    painter = painterResource(id = R.drawable.baseline_location_on_24),
                    contentDescription = stringResource(R.string.button_location_desc))
            }

            SmallFloatingActionButton(
                onClick = {
                },
            ) {
                Icon(Icons.Filled.Search, stringResource(R.string.button_search_desc))
            }

            // optional buttons
            ConstraintLayout {
                val (optional) = createRefs()
                Column(
                    modifier = Modifier.constrainAs(optional) {
                        top.linkTo(parent.bottom)
                    }
                ) {
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

                    if (distanceMeasurement && distanceMeasurementPoints.size >= 2) {
                        SmallFloatingActionButton(
                            onClick = {
                                saveRoute.launch("route.gpx")
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_save_alt_24),
                                contentDescription = stringResource(R.string.menu_gpx_save)
                            )
                        }
                    }
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
            SpeedDisplay(currentSpeed, windowInsets)
        }

        if (recordLocation || trackLocations.size > 1) {
            TrackStatistics(trackLocations, recordLocation, windowInsets)
        }

        if (showRoute) {
            RouteStatistics(routeDistance, waypointCount, windowInsets)
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
                waypointCount = route.size
                routeDistance = measureDistance(route)

                val routeGeometry = LineString.fromLngLats(route)
                routeLayer.data =
                    GeoJSONData(routeGeometry)
                showRoute = true
                followLocation = false
                sharedState.mapViewportState.transitionToOverviewState(
                    overviewViewportStateOptions = OverviewViewportStateOptions.Builder()
                        .geometry(routeGeometry)
                        .padding(EdgeInsets(200.0, 200.0, 200.0, 200.0))
                        .build()
                )
            }
        }
    }
}
