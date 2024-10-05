package com.henrythasler.cyclemap

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Geometry
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxExperimental
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
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
import kotlinx.coroutines.launch
import org.simpleframework.xml.core.Persister
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Create a top-level property for DataStore
private val Context.dataStore by preferencesDataStore(name = "settings")

@OptIn(MapboxExperimental::class)
@Composable
@Preview
fun CycleMapView() {
    val mapState = rememberMapState {
        gesturesSettings = gesturesSettings.toBuilder()
            .setRotateEnabled(false)
            .setPitchEnabled(false)
            .build()
    }
    val mapViewportState = rememberMapViewportState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInsets = WindowInsets.systemBars.asPaddingValues()
    var crosshairPosition by remember { mutableStateOf<Rect?>(null) }

    var currentStyleId by remember { mutableStateOf<String?>(null) }

    var requestLocationTracking by remember { mutableStateOf(false) }
    var locationPermission by remember { mutableStateOf(false) }

    var distanceMeasurementPoints by remember { mutableStateOf(listOf<Point>()) }
    var distanceMeasurement by remember { mutableStateOf(false) }
    var distance by remember { mutableDoubleStateOf(0.0) }

    var highlightedBuilding by remember {
        mutableStateOf(emptyList<List<Point>>())
    }

    var isVisible by remember { mutableStateOf(false) }
    var trackLocations by remember { mutableStateOf<List<Location>>(emptyList()) }
    var showRoute by remember { mutableStateOf(false) }
    var trackLocation by remember { mutableStateOf(false) }
    var followLocation by remember { mutableStateOf(false) }
    var recordLocation by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showLocationDetails by remember { mutableStateOf(false) }
    var showStyleSelection by remember { mutableStateOf(false) }
    var permissionRequestCount by remember { mutableIntStateOf(1) }
    var showRequestPermissionButton by remember { mutableStateOf(false) }
    var lastClick by remember { mutableLongStateOf(0L) }
    var clickedPoint by remember { mutableStateOf<Point?>(null) }
    var clickedScreenCoordinate by remember { mutableStateOf<ScreenCoordinate?>(null) }

    /** Favourites handling */
    val context = LocalContext.current
    val dataStore: DataStore<Preferences> = context.dataStore
    var favourites by remember { mutableStateOf<Set<Favourite>?>(null) }
    var showFavouritesSelection by remember { mutableStateOf(false) }

    var locationService by remember { mutableStateOf<LocationService?>(null) }
    var locationServiceBound by remember { mutableStateOf(false) }
    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as LocationService.LocalBinder
                locationService = binder.getService()
                /** continue current track */
                locationService?.setLocations(trackLocations)
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

    val distanceMeasurementLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val routeLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val trackLayer: GeoJsonSourceState = rememberGeoJsonSourceState {}
    val styleDefinitions: List<StyleDefinition> = parseStyleDefinitions(context)

    // File handling
//    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            Log.i(TAG, "selected file: ${it.path}")
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val serializer = Persister()
                        val gpx = serializer.read(Gpx::class.java, inputStream)
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
                            Log.d(TAG, "Successfully loaded route with ${route.size} points")

                            // FIXME: zoom to route extent
                            mapViewportState.setCameraOptions { center(route.first()) }
//                            mapViewportState.transitionToOverviewState(
//                                overviewViewportStateOptions = OverviewViewportStateOptions.Builder()
//                                    .geometry(routeGeometry)
//                                    .padding(EdgeInsets(200.0, 200.0, 200.0, 200.0))
//                                    .build()
//                            )

                            showRoute = true
                            followLocation = false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading GPX file $uri: $e")
                }
            }
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
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { last -> currentSpeed = last.speed * 3.6 }
            }
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

    /** Load Favourites */
    if (favourites == null) {
        LaunchedEffect(Unit) {
            loadFavourites(dataStore).collect { favourites = it }
        }
    }

    // Load initial values
    val lifecycleOwner = LocalLifecycleOwner.current
    if (currentStyleId == null) {
        LaunchedEffect(Unit) {
            context.dataStore.data.collect { preferences ->
                mapViewportState.setCameraOptions {
                    val lon =
                        preferences[stringPreferencesKey(context.getString(R.string.longitude_name))]
                            ?: context.getString(R.string.longitude_default)
                    val lat =
                        preferences[stringPreferencesKey(context.getString(R.string.latitude_name))]
                            ?: context.getString(R.string.latitude_default)
                    val zoom =
                        preferences[stringPreferencesKey(context.getString(R.string.zoom_name))]
                            ?: context.getString(R.string.zoom_default)
                    currentStyleId =
                        preferences[stringPreferencesKey(context.getString(R.string.style_name))]
                            ?: context.getString(R.string.style_default)

                    Log.i(
                        TAG,
                        "Restored state: zoom=$zoom, lon=$lon, lat=$lat, styleId=$currentStyleId"
                    )
                    center(Point.fromLngLat(lon.toDouble(), lat.toDouble()))
                    zoom(zoom.toDouble())
                    pitch(0.0)
                    bearing(0.0)
                }
            }
        }
    }

    // Set up auto-save on suspend
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            isVisible = when (event) {
                Lifecycle.Event.ON_START -> true // Composable is visible
                Lifecycle.Event.ON_STOP -> false // Composable is not visible
                else -> isVisible
            }

            if (event == Lifecycle.Event.ON_STOP) {
                coroutineScope.launch {
                    context.dataStore.edit { preferences ->
                        Log.i(
                            TAG,
                            "saving current values: zoom=${mapViewportState.cameraState?.zoom}, lon=${mapViewportState.cameraState?.center?.longitude()}, lat=${mapViewportState.cameraState?.center?.latitude()}, styleId=$currentStyleId"
                        )
                        preferences[stringPreferencesKey(context.getString(R.string.longitude_name))] =
                            mapViewportState.cameraState?.center?.longitude().toString()
                        preferences[stringPreferencesKey(context.getString(R.string.latitude_name))] =
                            mapViewportState.cameraState?.center?.latitude().toString()
                        preferences[stringPreferencesKey(context.getString(R.string.zoom_name))] =
                            mapViewportState.cameraState?.zoom.toString()
                        currentStyleId?.let {
                            preferences[stringPreferencesKey(context.getString(R.string.style_name))] =
                                it
                        }
                    }
                    saveFavourites(dataStore, favourites)
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Log.d(TAG, "Composing (${trackLocations.size})")
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val styleUrl = resolveStyleId(styleDefinitions, currentStyleId)
        styleUrl?.let { style ->
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                mapState = mapState,
                style = {
                    MapStyle(style = style)
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
                onMapLongClickListener = {
                    clickedPoint = it
                    showLocationDetails = true
                    coroutineScope.launch {
                        clickedPoint?.let { point ->
                            clickedScreenCoordinate = mapState.pixelForCoordinate(point)
                        }
                    }
//                        coroutineScope.launch {
//                            val selectedFeatures = mapState.queryRenderedFeatures(
//                                geometry = RenderedQueryGeometry(mapState.pixelForCoordinate(clickedPoint)),
//                                options = RenderedQueryOptions(null, null)
//                            )
//                            selectedFeatures.value?.forEach { feature ->
//                                Log.i(TAG, feature.queriedFeature.feature.geometry().toString())
//                                highlightedBuilding = (feature.queriedFeature.feature.geometry() as? Polygon)?.coordinates()?.toList() ?: emptyList()
//                            }
//                        }
                    false
                }
            ) {
                MapEffect(trackLocation, isVisible) { mapView ->
                    mapView.location.updateSettings {
                        locationPuck = createDefault2DPuck(withBearing = true)
                        enabled = trackLocation
                        showAccuracyRing = true
                        puckBearingEnabled = true
                        puckBearing = PuckBearing.HEADING
                    }

                    if (trackLocation && isVisible) {
                        mapViewportState.transitionToFollowPuckState(
                            followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                                .bearing(null).padding(null).pitch(null).zoom(null).build()
                        )
                        followLocation = true

                        Log.d(TAG, "addOnMoveListener")
                        mapView.gestures.addOnMoveListener(onMoveListener)
                    } else {
                        mapViewportState.idle()
                        Log.d(TAG, "removeOnMoveListener")
                        mapView.gestures.removeOnMoveListener(onMoveListener)
                    }
                }

                if (highlightedBuilding.isNotEmpty()) {
                    PolygonAnnotation(
                        points = highlightedBuilding,
                        fillOpacity = 0.5,
                        onClick = {
                            highlightedBuilding = emptyList()
                            false
                        }
                    )
                }
//                    clickedPoint?.let {
//                        PointAnnotation(
//                            point = it,
////                            iconImage = painterResource(id = R.drawable.baseline_location_pin_96).drawToBitmap().asAndroidBitmap()
////                            iconImage = BitmapFactory.decodeResource(context.resources, R.drawable.baseline_location_pin_96),
//                            textField = "Here!"
//                        )
//                    }

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
                    lineOpacity = DoubleValue(0.80),
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

        if (showLocationDetails) {
            LocationContextMenu(
                getFormattedLocation(clickedPoint, 3),
                clickedPoint,
                clickedScreenCoordinate,
                onDismiss = {
                    showLocationDetails = false
                },
                onLocationDetails = {
                    showLocationDetails = false
                    coroutineScope.launch {
                        val selectedFeatures = mapState.queryRenderedFeatures(
                            geometry = RenderedQueryGeometry(clickedScreenCoordinate!!),
                            options = RenderedQueryOptions(null, null)
                        )
                        selectedFeatures.value?.forEach { feature ->
                            highlightedBuilding =
                                (feature.queriedFeature.feature.geometry() as? Polygon)?.coordinates()
                                    ?.toList() ?: emptyList()
                        }
                    }
                },
                onBookmarkLocation = { point ->
                    showLocationDetails = false
                    mapViewportState.cameraState?.let {
                        favourites = favourites?.plus(
                            Favourite(
                                "My Location ${favourites?.size?.plus(1)}",
                                point.longitude(),
                                point.latitude(),
                                it.zoom
                            )
                        )
                    }
                },
                onShareLocation = { point, shareTemplateId ->
                    showLocationDetails = false
                    val link = String.format(
                        context.getString(shareTemplateId),
                        DecimalFormat("#.##", DecimalFormatSymbols(Locale.US)).format(
                            mapViewportState.cameraState?.zoom
                        ),
                        DecimalFormat(
                            "#.####",
                            DecimalFormatSymbols(Locale.US)
                        ).format(point.latitude()),
                        DecimalFormat(
                            "#.####",
                            DecimalFormatSymbols(Locale.US)
                        ).format(point.longitude()),
                    )

                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, link)
                    }
                    val chooser = Intent.createChooser(intent, "Share location via")
                    context.startActivity(chooser)
                }
            )
        }

        // Listen for camera movements when tracking is enabled
        LaunchedEffect(distanceMeasurement) {
            if (distanceMeasurement) {
                snapshotFlow { mapViewportState.cameraState!!.center }
                    .collect { center ->
                        // add current position temporarily to calculate the distance while dragging
                        val points = distanceMeasurementPoints.toMutableList()
                        points.add(center)
                        distance = measureDistance(points)
                        distanceMeasurementLayer.data = GeoJSONData(LineString.fromLngLats(points))
                    }
            }
        }

        LaunchedEffect(locationServiceBound, isVisible) {
            if (locationServiceBound && isVisible) {
                locationService?.locations?.collect { locations ->
                    Log.d(TAG, locations.size.toString())
                    trackLocations = locations
                    trackLayer.data =
                        GeoJSONData(LineString.fromLngLats(locationToPoints(trackLocations)))
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
                            mapViewportState.cameraState?.let {
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
                        crosshairPosition = coordinates.boundsInWindow()
                        Log.i(TAG, "new Crosshair Center: ${crosshairPosition?.center}")
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
            SmallFloatingActionButton(
                onClick = {
                    showMainMenu = !showMainMenu
                },
            ) {
                Icon(Icons.Filled.Menu, stringResource(R.string.button_menu_desc))
                if(showMainMenu) {
                    MainMenu(
                        onDismissRequest = {
                            showMainMenu = false
                        },
                        onFavourites = {
                            showMainMenu = false
                            showFavouritesSelection = true
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
                            trackLocations = emptyList()
                            trackLayer.data = GeoJSONData("")
                        },
                        onAbout = {
                            showMainMenu = false
                            showAbout = true
                        },
                    )
                }
            }

            SmallFloatingActionButton(
                onClick = {
                    if (trackLocation) {
                        if (!followLocation) {
                            mapViewportState.transitionToFollowPuckState(
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
                    contentDescription = stringResource(R.string.button_location_desc)
                )
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
            currentStyleId?.let {
                StyleSelectionSheet(
                    onDismiss = {
                        showStyleSelection = false
                    },
                    styleDefinitions = styleDefinitions,
                    currentStyle = it
                )
                { styleDefinition ->
                    Log.i(TAG, "selected $styleDefinition")
                    currentStyleId = styleDefinition.styleId
                    showStyleSelection = false
                }
            }
        }

        if (showFavouritesSelection) {
            FavouritesSelectionSheet(
                favourites = favourites,
                onDismiss = {
                    showFavouritesSelection = false
                },
                onRemove = { item ->
                    favourites = favourites?.minus(item)
                }
            ) { favourite ->
                showFavouritesSelection = false
                followLocation = false
                mapViewportState.setCameraOptions {
                    center(Point.fromLngLat(favourite.longitude, favourite.latitude))
                    zoom(favourite.zoom)
                    pitch(0.0)
                    bearing(0.0)
                }
            }
        }

        if (trackLocation) {
            SpeedDisplay(currentSpeed, windowInsets)
        }

        if (recordLocation || trackLocations.isNotEmpty()) {
            TrackStatistics(trackLocations, recordLocation, windowInsets)
        }

        if (showRoute) {
            RouteStatistics(routeDistance, waypointCount, windowInsets)
        }
    }

    /** observe location to show current speed */
    DisposableEffect(trackLocation, isVisible) {
        val locationRequest =
            LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 1000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdateDelayMillis(2000)
                .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && trackLocation && isVisible
        ) {
            Log.d(TAG, "requestLocationUpdates")
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            Log.d(TAG, "removeLocationUpdates")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        onDispose {
            Log.d(TAG, "onDispose removeLocationUpdates")
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    LaunchedEffect(key1 = crosshairPosition) {
        Log.d(TAG, "crosshairPosition=${crosshairPosition?.center}")
        crosshairPosition?.let {
            mapState.gesturesSettings = mapState.gesturesSettings.toBuilder()
                .setFocalPoint(ScreenCoordinate(it.center.x.toDouble(), it.center.y.toDouble()))
                .build()
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
}
