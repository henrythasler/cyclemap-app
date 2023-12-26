package com.henrythasler.cyclemap2

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.text.format.DateUtils
import android.util.Log
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.textfield.TextInputEditText
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location2
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation
import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Integer.max
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


class MainMapActivity : AppCompatActivity() {
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var map: MapboxMap
    private lateinit var mapView: MapView
    private lateinit var popupMainMenu: PopupMenu

    private var followLocation: Boolean = false

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geoSearch: GeoSearch

    /** setup and interface for the location service to provide location updates when the
     * app is minimized */
    private lateinit var locationService: LocationService
    private var locationServiceBound: Boolean = false

    private var distanceMeasurement: Boolean = false
    private var lastClickedTimestamp: Long = 0
    private var distanceMeasurementPoints: MutableList<Point> = mutableListOf()

    private var routePoints: MutableList<Point> = mutableListOf()

    private var trackRecording: Boolean = false
    private var trackPoints: List<Point> = listOf()
    private var trackLocations: List<Location> = listOf()
    private val timerHandler = Handler(Looper.getMainLooper())

    private var gpxWriterSource: String? = null

    private val moveListener: OnMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            if (followLocation) Log.d(TAG, "tracking disabled")
            followLocation = false
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {
            // this is a workaround to reliably set the focalPoint for double-tap-zoom
//            mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
        }
    }

    /**
     * It's basically an event-loop-based timer callback that does something and triggers itself again if the
     * right conditions are met.
     */
    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (trackRecording && locationServiceBound) {
                trackLocations = locationService.locations.toList()
                updateTrack(trackLocations)
                updateTrackStatistics(trackLocations, findViewById(R.id.trackDetails))
                findViewById<TextView>(R.id.trackDetails).visibility = View.VISIBLE
                updateOdometer(findViewById(R.id.odometer))
                timerHandler.postDelayed(this, min(TRACK_RECORDING_INTERVAL, ODOMETER_UPDATE_INTERVAL))
            }
            else if (mapView.location2.enabled ) {
                updateOdometer(findViewById(R.id.odometer))
                timerHandler.postDelayed(this, ODOMETER_UPDATE_INTERVAL)
            }
            else {
                // make sure we always zoom into the map center
                mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
                Log.i(TAG, "timerRunnable: focalPoint=" + mapView.gestures.focalPoint.toString())
            }
        }
    }


private val cameraChangeListener = OnCameraChangeListener {
    if (distanceMeasurement) {
        distanceMeasurementPoints[distanceMeasurementPoints.lastIndex] = map.cameraState.center
        updateDistanceMeasurement()
    }
}

private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
    if (followLocation) {
        map.setCamera(CameraOptions.Builder().center(it).build())
//            mapView.gestures.focalPoint = map.pixelForCoordinate(it)
    }
}

/** Defines callbacks for service binding, passed to bindService()  */
private val locationServiceConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        // We've bound to LocalService, cast the IBinder and get LocalService instance
        val binder = service as LocationService.LocalBinder
        locationService = binder.getService()
        locationServiceBound = true
        Log.i(TAG, "onServiceConnected")

        /** start the timer to regularly update the UI with the latest track (information) */
        timerHandler.post(timerRunnable)
    }

    override fun onServiceDisconnected(arg0: ComponentName) {
        locationServiceBound = false
        Log.i(TAG, "onServiceDisconnected")
    }
}

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main_map)
    mapView = findViewById(R.id.mapView)
    map = mapView.getMapboxMap()

    restoreSettings()

    map.loadStyleUri(resources.getString(R.string.CYCLEMAP_STYLE_URL)) { style ->
        onStyleLoaded(style)
    }

    // location tracking
    locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
    locationPermissionHelper.checkPermissions {
        Log.i(TAG, "permissions ok")
    }
    fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

    geoSearch = GeoSearch(WeakReference(this), findViewById(R.id.rvSearchResults), map)

    // set up user interaction
    popupMainMenu = PopupMenu(this, findViewById(R.id.menuAnchor)).apply {
        setOnMenuItemClickListener { item -> onMenuItem(item) }
        inflate(R.menu.main_menu)
    }

    findViewById<View>(R.id.menuButton).setOnClickListener { popupMainMenu.show() }
    findViewById<View>(R.id.locationButton).setOnClickListener { onLocateButton() }
    findViewById<View>(R.id.searchButton).setOnClickListener { onSearchButton() }

    findViewById<View>(R.id.crosshair).setOnClickListener { onCrosshairClick() }
    findViewById<View>(R.id.crosshair).setOnLongClickListener { onCrosshairLongClick() }

    findViewById<View?>(R.id.saveAsRoute).setOnClickListener {
        saveGPXDocument(
            "route.gpx",
            "GXP_SOURCE_ROUTE"
        )
    }
    findViewById<ImageButton>(R.id.recordTrack).setOnClickListener { onRecordTrackButton() }
    findViewById<TextView>(R.id.trackDetails).setOnClickListener {
        onTrackDetailsClick(
            trackLocations
        )
    }

    // catch map events
    mapView.gestures.addOnMoveListener(moveListener)
    map.addOnCameraChangeListener(cameraChangeListener)
    mapView.location2.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
}

private fun onStyleLoaded(style: Style) {
    // prevent certain UI operations
    mapView.gestures.rotateEnabled = false
    mapView.gestures.pitchEnabled = false
    mapView.gestures.scrollDecelerationEnabled = true

    // make sure we always zoom into the map center
//        mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
//        Log.i(TAG, "onStyleLoaded(): focalPoint=" + mapView.gestures.focalPoint.toString())
    timerHandler.postDelayed(timerRunnable, 1000)

    /** set checkbox-state based on layer availability and visibility */
    if (style.styleLayerExists("hillshading")) {
        popupMainMenu.menu.findItem(R.id.hillshading).isEnabled = true
        popupMainMenu.menu.findItem(R.id.hillshading).isChecked =
            style.getLayer("hillshading")?.visibility == Visibility.VISIBLE
    } else {
        popupMainMenu.menu.findItem(R.id.hillshading).isEnabled = false
        popupMainMenu.menu.findItem(R.id.hillshading).isChecked = false
    }

    style.addSource(geoJsonSource("DISTANCE_MEASUREMENT_SOURCE") {
        if (distanceMeasurementPoints.size > 0) {
            feature(
                Feature.fromGeometry(
                    LineString.fromLngLats(distanceMeasurementPoints)
                )
            )
        }
    })
    style.addLayer(lineLayer("DISTANCE_MEASUREMENT_CASING", "DISTANCE_MEASUREMENT_SOURCE") {
        lineCap(LineCap.ROUND)
        lineJoin(LineJoin.ROUND)
        lineOpacity(0.4)
        lineWidth(7.0)
        lineColor(getColor(R.color.white))
    })
    style.addLayer(lineLayer("DISTANCE_MEASUREMENT", "DISTANCE_MEASUREMENT_SOURCE") {
        lineCap(LineCap.ROUND)
//            lineDasharray(listOf(0.0, 1.8))
        lineJoin(LineJoin.ROUND)
        lineOpacity(1.0)
        lineWidth(5.0)
        lineColor(getColor(R.color.colorDistanceMeasurement))
    })

    style.addSource(geoJsonSource("ROUTE_SOURCE") {
        if (routePoints.isNotEmpty()) {
            feature(
                Feature.fromGeometry(
                    LineString.fromLngLats(routePoints)
                )
            )
        }
    })
    style.addLayer(lineLayer("ROUTE", "ROUTE_SOURCE") {
        lineCap(LineCap.ROUND)
        lineJoin(LineJoin.ROUND)
        lineOpacity(0.75)
        lineWidth(9.0)
        lineColor(getColor(R.color.colorRoute))
    })

    style.addSource(geoJsonSource("TRACK_SOURCE") {
        if (trackPoints.isNotEmpty()) {
            feature(
                Feature.fromGeometry(
                    LineString.fromLngLats(trackPoints)
                )
            )
        }
    })

    if (trackPoints.isNotEmpty())
        addTrackLayer(style)
}

private fun addTrackLayer(style: Style) {
    if (!style.styleLayerExists("TRACK")) {
        if (style.styleLayerExists("mapbox-location-indicator-layer")) {
            style.addLayerBelow(lineLayer("TRACK", "TRACK_SOURCE") {
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.75)
                lineWidth(9.0)
                lineColor(getColor(R.color.colorTrack))
            }, "mapbox-location-indicator-layer")
        } else {
            Log.i(TAG, "Layer 'mapbox-location-indicator-layer' not found.")
            style.addLayer(lineLayer("TRACK", "TRACK_SOURCE") {
                lineCap(LineCap.ROUND)
                lineJoin(LineJoin.ROUND)
                lineOpacity(0.75)
                lineWidth(9.0)
                lineColor(getColor(R.color.colorTrack))
            })
        }
    }
}

private fun onCrosshairClick() {
    var isDoubleClick = false
    if (SystemClock.elapsedRealtime() - lastClickedTimestamp < 500) {
        isDoubleClick = true
    }
    lastClickedTimestamp = SystemClock.elapsedRealtime()

    var clearPoints = false
    val distanceText: TextView = findViewById(R.id.distanceText)
    if (isDoubleClick) {
        if (distanceMeasurement) {
            distanceMeasurement = false
            clearPoints = true
        }
    } else {
        if (distanceMeasurement) {
            // add only if current point differs from last point to prevent accidentally adding the same point
            if (distanceMeasurementPoints[max(
                    0,
                    distanceMeasurementPoints.lastIndex - 1
                )] != map.cameraState.center
            ) {
                distanceMeasurementPoints.add(map.cameraState.center)
            }
        } else {
            distanceMeasurement = true
            clearPoints = true
        }
    }

    if (clearPoints) {
        distanceMeasurementPoints.clear()
        distanceMeasurementPoints.add(map.cameraState.center)
        distanceMeasurementPoints.add(map.cameraState.center)
        distanceText.text = "0 m"
    }

    distanceText.visibility = if (distanceMeasurement) View.VISIBLE else View.INVISIBLE

    map.getStyle {
        it.getLayer("DISTANCE_MEASUREMENT")
            ?.visibility(if (distanceMeasurement) Visibility.VISIBLE else Visibility.NONE)
    }

    Log.i(TAG, "distanceMeasurementPoints.size=${distanceMeasurementPoints.size}")
    updateDistanceMeasurement()
}

private fun onCrosshairLongClick(): Boolean {
    if (distanceMeasurement) {
        if (distanceMeasurementPoints.size > 2) {
            distanceMeasurementPoints.removeAt(max(0, distanceMeasurementPoints.lastIndex - 1))
            Log.i(TAG, "distanceMeasurementPoints.size=${distanceMeasurementPoints.size}")
            updateDistanceMeasurement()
        }
    } else {
        showCrosshairDetails()
    }
    return true
}

private fun updateDistanceMeasurement() {
    val drawLineSource =
        map.getStyle()?.getSourceAs<GeoJsonSource>("DISTANCE_MEASUREMENT_SOURCE")
    drawLineSource?.feature(
        Feature.fromGeometry(
            LineString.fromLngLats(
                distanceMeasurementPoints
            )
        )
    )

    findViewById<View?>(R.id.saveAsRoute).visibility =
        if (distanceMeasurementPoints.size > 2) View.VISIBLE else View.GONE

    val distance =
        TurfMeasurement.length(LineString.fromLngLats(distanceMeasurementPoints), UNIT_METERS)
    val distanceText: TextView = findViewById(R.id.distanceText)

    distanceText.text = getFormattedDistance(distance)
}

private fun onLocateButton() {
    if (mapView.location2.enabled) {
        // jump back to current location if the user has manually scrolled away
        if (!followLocation) {
            Log.i(TAG, "jumping back to location...")
            followLocation = true
        }
        // disable tracking if the user is currently tracking the position
        else {
            Log.i(TAG, "disabling location tracking...")
            mapView.location2.enabled = false
            disableLocationService()
        }
    } else {
        // enable location tracking
        Log.i(TAG, "enabling location tracking...")
        mapView.location2.apply {
            enabled = true
//                pulsingEnabled = true
//                pulsingColor = Color.parseColor("#00c000")
//                pulsingMaxRadius = 32.0F
//                puckBearingEnabled = true
//                puckBearingSource = PuckBearingSource.HEADING
            showAccuracyRing = true
            locationPuck = createDefault2DPuck(this@MainMapActivity, withBearing = true)
        }
        followLocation = true
        timerHandler.post(timerRunnable)
    }
    findViewById<ImageButton>(R.id.recordTrack).visibility =
        if (mapView.location2.enabled) View.VISIBLE else View.GONE

    // show speed
    findViewById<TextView>(R.id.odometer).visibility =
        if (mapView.location2.enabled) View.VISIBLE else View.GONE
}

private fun onRecordTrackButton() {
    trackRecording = !trackRecording

    /** start the location service to record the track */
    if (trackRecording && !locationServiceBound) {
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
        }
    } else {
        disableLocationService()
    }

    map.getStyle { style ->
        addTrackLayer(style)
    }
    findViewById<TextView>(R.id.trackDetails).visibility = View.VISIBLE
}

private fun disableLocationService() {
    /** stop the service when track recording is stopped */
    if (locationServiceBound) {
        unbindService(locationServiceConnection)
        locationServiceBound = false
        trackRecording = false
    }
}

private fun updateTrack(track: List<Location>) {
    if (track.size >= 2) {
        /** create an immutable copy to avoid GeoJson-parser crashes.
         * see https://www.baeldung.com/kotlin/mutable-collection-to-immutable */
        val tempList: MutableList<Point> = mutableListOf()
        track.forEach { location ->
            tempList.add(
                Point.fromLngLat(
                    location.longitude,
                    location.latitude,
                )
            )
        }
        trackPoints = tempList.toList()

        /** update track layer with the latest track data*/
        map.getStyle()?.getSourceAs<GeoJsonSource>("TRACK_SOURCE")?.feature(
            Feature.fromGeometry(
                LineString.fromLngLats(trackPoints)
            )
        )
    }
}

override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    grantResults: IntArray
) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
}

override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    Log.i(TAG, "onSaveInstanceState()")

    // see https://developer.android.com/training/data-storage/shared-preferences
    with(getPreferences(MODE_PRIVATE).edit()) {
        putFloat(
            getString(R.string.STATE_LATITUDE),
            map.cameraState.center.latitude().toFloat()
        )
        putFloat(
            getString(R.string.STATE_LONGITUDE),
            map.cameraState.center.longitude().toFloat()
        )
        putFloat(getString(R.string.STATE_ZOOM), map.cameraState.zoom.toFloat())
        commit()
    }
}

private fun restoreSettings() {
    val sharedPref = getPreferences(MODE_PRIVATE) ?: return
    val cameraPosition = CameraOptions.Builder()
        .zoom(
            sharedPref.getFloat(
                getString(R.string.STATE_ZOOM),
                getString(R.string.DEFAULT_ZOOM).toFloat()
            ).toDouble()
        )
        .center(
            Point.fromLngLat(
                sharedPref.getFloat(
                    getString(R.string.STATE_LONGITUDE),
                    getString(R.string.DEFAULT_LONGITUDE).toFloat()
                ).toDouble(),
                sharedPref.getFloat(
                    getString(R.string.STATE_LATITUDE),
                    getString(R.string.DEFAULT_LATITUDE).toFloat()
                ).toDouble()
            )
        )
        .build()
    Log.i(
        TAG,
        "restoreSettings(): center=" + cameraPosition.center?.coordinates()
            .toString() + " zoom=" + cameraPosition.zoom.toString()
    )
    // set camera position
    map.setCamera(cameraPosition)
}

private fun onMenuItem(item: MenuItem): Boolean {
    return when (item.itemId) {
        R.id.style_cyclemap -> {
            item.isChecked = true
            map.loadStyleUri(resources.getString(R.string.CYCLEMAP_STYLE_URL)) { style ->
                onStyleLoaded(style)
            }
            true
        }

        R.id.style_shadow -> {
            item.isChecked = true
            map.loadStyleUri(resources.getString(R.string.SHADOW_STYLE_URL)) { style ->
                onStyleLoaded(style)
            }
            true
        }

        R.id.style_xray -> {
            item.isChecked = true
            map.loadStyleUri(resources.getString(R.string.XRAY_STYLE_URL)) { style ->
                onStyleLoaded(style)
            }
            true
        }

        R.id.style_outdoors -> {
            item.isChecked = true
            map.loadStyleUri(Style.OUTDOORS) { style -> onStyleLoaded(style) }
            true
        }

        R.id.style_sat -> {
            item.isChecked = true
            map.loadStyleUri(Style.SATELLITE) { style -> onStyleLoaded(style) }
            true
        }

        R.id.style_traffic -> {
            item.isChecked = true
            map.loadStyleUri(Style.TRAFFIC_DAY) { style -> onStyleLoaded(style) }
            true
        }

        R.id.route_load_gpx -> {
            loadGPXDocument()
            true
        }

        R.id.route_clear -> {
            map.getStyle { style ->
                style.getLayer("ROUTE")?.visibility(Visibility.NONE)
            }
            routePoints.clear()
            findViewById<TextView>(R.id.routeDetails).visibility = View.INVISIBLE
            true
        }

        R.id.about -> {
            val sdk = Build.VERSION.SDK_INT // SDK version
            val versionName = BuildConfig.VERSION_NAME // App version name from BuildConfig
            val packageName = BuildConfig.APPLICATION_ID // App package name from BuildConfig
            val build = BuildConfig.VERSION_CODE // Build type (debug/release) from BuildConfig

            AlertDialog.Builder(this)
                .setTitle("CycleMap App")
                .setMessage("$packageName\nv$versionName\nBuild ${build}\nAPI $sdk")
                .setNeutralButton("ok") { _, _ -> }
                .show()
            true
        }

        R.id.hillshading -> {
            item.isChecked = !item.isChecked
            mapView.getMapboxMap().getStyle { style ->
                style.getLayer("hillshading")?.let { layer ->
                    layer.visibility(if (layer.visibility == Visibility.NONE) Visibility.VISIBLE else Visibility.NONE)
                }
            }
            true
        }

        R.id.keepScreenOn -> {
            item.isChecked = !item.isChecked
            if (item.isChecked)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            true
        }

        R.id.saveTrackGpx -> {
            saveGPXDocument("track.gpx", "GXP_SOURCE_TRACK")
            true
        }

        R.id.clearTrack -> {
            trackRecording = false
            map.getStyle { style ->
                if (style.styleLayerExists("TRACK")) {
                    style.removeStyleLayer("TRACK")
                }
            }
            disableLocationService()
            findViewById<TextView>(R.id.trackDetails).visibility = View.GONE
            true
        }

        R.id.location_details -> {
            showLocationDetails()
            true
        }

        else -> false
    }
}

private fun loadGPXDocument() {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
    }
    loadGPXLauncher.launch(intent)
}

private var loadGPXLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            Log.i(TAG, "loading '${uri?.path}'...")
            if (uri != null) {
                // from https://developer.android.com/training/data-storage/shared/documents-files#kotlin
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    routePoints.clear()
                    try {
                        // see https://github.com/jenetics/jpx
                        GPX.read(inputStream).tracks
                            .flatMap { it.segments }
                            .flatMap { it.points }
                            .forEach {
                                routePoints.add(
                                    Point.fromLngLat(
                                        it.longitude.toDegrees(),
                                        it.latitude.toDegrees()
                                    )
                                )
                            }
                    } catch (e: RuntimeException) {
                        AlertDialog.Builder(this)
                            .setMessage(e.message)
                            .setNeutralButton("Whatever...") { dialog, which -> }
                            .show()
                    }

                    if (routePoints.size > 0) {
                        val geometry = LineString.fromLngLats(routePoints)
                        map.getStyle { style ->
                            style.getSourceAs<GeoJsonSource>("ROUTE_SOURCE")?.feature(
                                Feature.fromGeometry(geometry)
                            )
                            style.getLayer("ROUTE")?.visibility(Visibility.VISIBLE)
                            mapView.getMapboxMap().setCamera(
                                map.cameraForGeometry(
                                    geometry,
                                    EdgeInsets(100.0, 100.0, 100.0, 100.0)
                                )
                            )
                        }

                        val routeDetails: TextView = findViewById(R.id.routeDetails)
                        updateRouteStatistics(geometry, routeDetails)
                        routeDetails.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

private fun updateRouteStatistics(geometry: LineString, view: TextView) {
    view.text = getString(
        R.string.route_statistics,
        routePoints.size.toString(),
        getFormattedDistance(TurfMeasurement.length(geometry, UNIT_METERS))
    )
}

private fun updateOdometer(view: TextView) {
    val permission = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    if (permission == PackageManager.PERMISSION_GRANTED) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location? ->
                if (location != null) {
                    view.text = getString(
                        R.string.track_statistics_detail_avg_speed,
                        DecimalFormat("0.0").format(location.speed),
                    )
                }
            }
    }
    else {
        Log.e(LocationService.TAG, "Permission for ACCESS_FINE_LOCATION not granted.")
    }
}


private fun updateTrackStatistics(track: List<Location>, view: TextView) {
    if (track.size < 2) return

    val points: MutableList<Point> = mutableListOf()
    track.forEach { location ->
        points.add(
            Point.fromLngLat(
                location.longitude,
                location.latitude,
            )
        )
    }

    val geometry = LineString.fromLngLats(points)
    val distance = TurfMeasurement.length(geometry, UNIT_METERS)
    val tripDuration: Duration =
        ((if (trackRecording) System.currentTimeMillis() else track.last().time) - track.first().time).milliseconds
    view.text = getString(
        R.string.track_statistics,
        getFormattedDistance(distance),
        DateUtils.formatElapsedTime(tripDuration.inWholeSeconds)
    )

}

private fun onTrackDetailsClick(track: List<Location>) {
    if (track.size < 2) return

    val points: MutableList<Point> = mutableListOf()
    track.forEach { location ->
        points.add(
            Point.fromLngLat(
                location.longitude,
                location.latitude,
            )
        )
    }
    val geometry = LineString.fromLngLats(points)
    val distance = TurfMeasurement.length(geometry, UNIT_METERS)
    val tripDuration: Duration =
        ((if (trackRecording) System.currentTimeMillis() else track.last().time) - track.first().time).milliseconds
    val avgSpeed: Double = distance / 1000 / tripDuration.toDouble(DurationUnit.HOURS)

    val frameView = FrameLayout(this)
    val alertDialog = AlertDialog.Builder(this)
        .setTitle("Track Statistics")
        .setNeutralButton("ok", null)
        .setView(frameView)
        .create()

    val inflater = alertDialog.layoutInflater
    inflater.inflate(R.layout.track_statistics_detail, frameView)

    Log.i(TAG, Date(tripDuration.inWholeMilliseconds).toString())

    /** update actual data */
    frameView.findViewById<TextView>(R.id.track_statistics_detail_distance).text =
        getFormattedDistance(distance)
    frameView.findViewById<TextView>(R.id.track_statistics_detail_time).text =
        DateUtils.formatElapsedTime(tripDuration.inWholeSeconds)
    frameView.findViewById<TextView>(R.id.track_statistics_detail_avg_speed).text =
        getString(
            R.string.track_statistics_detail_avg_speed,
            DecimalFormat("#.0").format(avgSpeed)
        )
    frameView.findViewById<TextView>(R.id.track_statistics_detail_time_start).text =
        SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(track.first().time))

    alertDialog.show()
}

private fun getFormattedDistance(distance: Double): String {
    return if (distance > 5000) {
        DecimalFormat("#.0 km").format(distance / 1000)
    } else {
        DecimalFormat("# m").format(distance)
    }
}

private fun onSearchButton() {
    val sheet = BottomSheetBehavior.from(findViewById(R.id.geosearch))
    if (sheet.state == BottomSheetBehavior.STATE_HALF_EXPANDED)
        sheet.state = BottomSheetBehavior.STATE_HIDDEN
    else sheet.state = BottomSheetBehavior.STATE_HALF_EXPANDED

//        geoSearch.resultTextView = findViewById(R.id.geosearchResults)

    findViewById<TextInputEditText>(R.id.geosearchInput).addTextChangedListener {
        val text = findViewById<TextInputEditText>(R.id.geosearchInput).text.toString()
        if (text.length >= 3) {
            geoSearch.search(
                text,
                map.cameraState.center
            )
        }
    }

//        val bottomSheetDialog = BottomSheetDialog(this)
//        bottomSheetDialog.setContentView(R.layout.geosearch_bottomsheet)
//        bottomSheetDialog.show()
//
//        geoSearch.resultTextView = bottomSheetDialog.findViewById<TextView>(R.id.textView)
}

private fun showLocationDetails() {
//        val permission = ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )
//
//        if (permission == PackageManager.PERMISSION_GRANTED) {
//            fusedLocationClient.lastLocation
//                .addOnSuccessListener { location : Location? ->
//
//                    val frameView = FrameLayout(this)
//                    val alertDialog = AlertDialog.Builder(this)
//                        .setTitle("Location Details")
//                        .setNeutralButton("ok", null)
//                        .setView(frameView)
//                        .create()
//
//                    val inflater = alertDialog.layoutInflater
//                    inflater.inflate(R.layout.location_detail, frameView)
//
//                    Log.i(TAG, location.toString())
//
//                    if(location != null) {
//                        /** update actual data */
//                        frameView.findViewById<TextView>(R.id.location_detail_latitude).text =
//                            DecimalFormat("#.0000°").format(location.latitude)
//                        frameView.findViewById<TextView>(R.id.location_detail_longitude).text =
//                            DecimalFormat("#.0000°").format(location.longitude)
//                        frameView.findViewById<TextView>(R.id.location_detail_altitude).text =
//                            DecimalFormat("# m").format(location.altitude)
//                    }
//                    alertDialog.show()
//                }
//        }
//        else {
//            Log.e(LocationService.TAG, "Permission for ACCESS_FINE_LOCATION not granted.")
//        }
}

private fun showCrosshairDetails() {
    // https://medium.com/@eloisance/android-alertdialog-with-custom-layout-and-transparency-d95ca8b5e712
    val inflater: LayoutInflater = this.layoutInflater
    val frameView: View = inflater.inflate(R.layout.position_detail, null)
    val alertDialog = AlertDialog.Builder(this, R.style.MyDialogStyle).apply {
        setView(frameView)
    }.create()

    val point = map.cameraState.center
    frameView.findViewById<TextView>(R.id.location_detail_latitude).text =
        DecimalFormat(
            "#.0000°",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
        ).format(point.latitude())
    frameView.findViewById<TextView>(R.id.location_detail_longitude).text =
        DecimalFormat(
            "#.0000°",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
        ).format(point.longitude())

    frameView.findViewById<Button>(R.id.dialog_button)
        ?.setOnClickListener { alertDialog.cancel() }
    frameView.findViewById<Button>(R.id.share_button)?.setOnClickListener {
        sharePosition(map.cameraState.zoom, point)
        alertDialog.cancel()
    }

    alertDialog.show()
}

private fun sharePosition(zoom: Double, point: Point) {
    val text = getString(
        R.string.share_map_position,
        DecimalFormat("#.####", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(zoom),
        DecimalFormat(
            "#.####",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
        ).format(point.latitude()),
        DecimalFormat(
            "#.####",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
        ).format(point.longitude()),
    )

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
    } else {
        Log.e(TAG, "startActivity error")
    }
}

private fun saveGPXDocument(defaultFilename: String, sourceType: String) {
    // https://developer.android.com/training/data-storage/shared/documents-files#create-file
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        putExtra(Intent.EXTRA_TITLE, defaultFilename)
//            putExtra(Intent.EXTRA_TEXT, "sourceType") // FIXME: Find a way how this works
        gpxWriterSource = sourceType
    }
    saveGPXLauncher.launch(intent)
}

private var saveGPXLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data

            // FIXME: Find a way how this works
//                val sourceType = result.data?.getStringExtra(Intent.EXTRA_TITLE)
            Log.i(TAG, "SOURCE_TYPE=${gpxWriterSource}")

            Log.i(TAG, "saving '${uri?.path}'...")
            if (uri != null) {
                try {
                    contentResolver.openFileDescriptor(uri, "w")?.use {
                        FileOutputStream(it.fileDescriptor).use { outputStream ->

                            val track = GPX.builder().addTrack { track ->
                                track.addSegment { segment ->
                                    when (gpxWriterSource) {
                                        "GXP_SOURCE_ROUTE" -> {
                                            distanceMeasurementPoints.forEach { point ->
                                                segment.addPoint(
                                                    WayPoint.of(
                                                        point.latitude(),
                                                        point.longitude(),
                                                    )
                                                )
                                            }
                                        }

                                        "GXP_SOURCE_TRACK" -> {
                                            trackLocations.forEach { point ->
                                                segment.addPoint(
                                                    WayPoint.of(
                                                        point.latitude,
                                                        point.longitude,
                                                        point.altitude,
                                                        point.time
                                                    )
                                                )
                                            }
                                        }

                                        "GPX_SIMPLIFIED_TRACK" -> {
                                            val gpxData: MutableList<Point> = mutableListOf()
                                            trackLocations.forEach { location ->
                                                gpxData.add(
                                                    Point.fromLngLat(
                                                        location.longitude,
                                                        location.latitude,
                                                    )
                                                )
                                            }
                                            // tolerance is in degrees.
                                            val simplifiedData = TurfTransformation.simplify(
                                                gpxData,
                                                TRACK_SIMPLIFY_TOLERANCE
                                            )
                                            Log.i(
                                                TAG,
                                                "original: ${gpxData.size}  simplified: ${simplifiedData.size}"
                                            )

                                            Log.i(TAG, "TODO: write GPX_SIMPLIFIED_TRACK")
                                        }
                                    }
                                }
                                gpxWriterSource = null
                            }.build()
                            GPX.write(track, outputStream)
                        }
                    }
                } catch (e: FileNotFoundException) {
                    AlertDialog.Builder(this)
                        .setMessage(e.message)
                        .setNeutralButton("Right...") { dialog, which -> }
                        .show()
                } catch (e: IOException) {
                    AlertDialog.Builder(this)
                        .setMessage(e.message)
                        .setNeutralButton("Uh...") { dialog, which -> }
                        .show()
                }
            }
        }
    }

companion object {
    const val TAG: String = "Cyclemap"
    const val TRACK_RECORDING_INTERVAL: Long = 1000
    const val ODOMETER_UPDATE_INTERVAL: Long = 1000
    const val TRACK_SIMPLIFY_TOLERANCE: Double = 0.00005
}
}