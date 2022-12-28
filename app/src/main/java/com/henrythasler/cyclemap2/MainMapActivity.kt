package com.henrythasler.cyclemap2

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
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
import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Integer.max
import java.lang.ref.WeakReference
import java.text.DecimalFormat


class MainMapActivity : AppCompatActivity() {
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var map: MapboxMap
    private lateinit var mapView: MapView

    private var followLocation: Boolean = false
    private var lastLocationUpdateTimestamp: Long = 0

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
    private val trackRecordingTimerHandler = Handler(Looper.getMainLooper())

    private var screenAlwaysOn: Boolean = false

    private val moveListener: OnMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            Log.d(TAG, "tracking disabled")
            followLocation = false
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {
            // this is a workaround to reliably set the focalPoint for double-tap-zoom
            mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
            Log.i(TAG, "onMoveEnd(): focalPoint=" + mapView.gestures.focalPoint.toString())
        }
    }

    /**
     * It's basically an event-loop-based timer callback that does something and triggers itself again if the
     * right conditions are met.
     */
    private val trackTimerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (trackRecording && locationServiceBound) {
                updateTrack()
                trackRecordingTimerHandler.postDelayed(this, TRACK_RECORDING_INTERVAL)
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
            mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
            mapView.gestures.focalPoint = map.pixelForCoordinate(it)
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
            trackRecordingTimerHandler.post(trackTimerRunnable)
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

        // set up user interaction
        val menuButton: View = findViewById(R.id.menuButton)
        menuButton.setOnClickListener { view -> onMenuButton(view) }

        val locateButton: View = findViewById(R.id.locationButton)
        locateButton.setOnClickListener { view -> onLocateButton(view) }

        val crosshairButton: View = findViewById(R.id.crosshair)
        crosshairButton.setOnClickListener { view -> onCrosshairClick(view) }
        crosshairButton.setOnLongClickListener { view ->
            onCrosshairLongClick(view)
            true
        }

        findViewById<View?>(R.id.saveAsRoute).setOnClickListener { saveGPXDocument() }
        findViewById<ImageButton>(R.id.recordTrack).setOnClickListener { onRecordTrackButton() }

        // catch map events
        mapView.gestures.addOnMoveListener(moveListener)
        map.addOnCameraChangeListener(cameraChangeListener)
        mapView.location2.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
    }

    private fun onStyleLoaded(style: Style) {
        // prevent certain UI operations
        mapView.gestures.rotateEnabled = false
        mapView.gestures.pitchEnabled = false

        // make sure we always zoom into the map center
        mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
        Log.i(TAG, "onStyleLoaded(): focalPoint=" + mapView.gestures.focalPoint.toString())

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
            if (routePoints.size > 0) {
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
        style.addLayer(lineLayer("TRACK", "TRACK_SOURCE") {
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            lineOpacity(0.75)
            lineWidth(9.0)
            lineColor(getColor(R.color.colorTrack))
        })

    }

    private fun onCrosshairClick(view: View?) {
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

    private fun onCrosshairLongClick(view: View?) {
        if (distanceMeasurement && (distanceMeasurementPoints.size > 2)) {
            distanceMeasurementPoints.removeAt(max(0, distanceMeasurementPoints.lastIndex - 1))
            Log.i(TAG, "distanceMeasurementPoints.size=${distanceMeasurementPoints.size}")
            updateDistanceMeasurement()
        }
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
            if (distanceMeasurementPoints.size > 2) View.VISIBLE else View.INVISIBLE

        val distance =
            TurfMeasurement.length(LineString.fromLngLats(distanceMeasurementPoints), UNIT_METERS)
        val distanceText: TextView = findViewById(R.id.distanceText)

        if (distance > 5000) {
            distanceText.text = DecimalFormat("#.0 km").format(distance / 1000)
        } else {
            distanceText.text = DecimalFormat("# m").format(distance)
        }
    }

    private fun onLocateButton(view: View) {
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
        }
        findViewById<ImageButton>(R.id.recordTrack).visibility =
            if (mapView.location2.enabled) View.VISIBLE else View.INVISIBLE
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
    }

    private fun disableLocationService() {
        /** stop the service when track recording is stopped */
        if(locationServiceBound) {
            unbindService(locationServiceConnection)
            locationServiceBound = false
            trackRecording = false
        }
    }

    private fun updateTrack() {
        if (locationService.trackPoints.size >= 2) {
            /** create an immutable copy to avoid GeoJson-parser crashes.
             * see https://www.baeldung.com/kotlin/mutable-collection-to-immutable */
            trackPoints = locationService.trackPoints.toList()

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

    private fun onMenuButton(view: View?) {
        PopupMenu(this, view).apply {
            // MainActivity implements OnMenuItemClickListener
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.style_cyclemap -> {
                        map.loadStyleUri(resources.getString(R.string.CYCLEMAP_STYLE_URL)) { style ->
                            onStyleLoaded(style)
                        }
                    }
                    R.id.style_shadow -> {
                        map.loadStyleUri(resources.getString(R.string.SHADOW_STYLE_URL)) { style ->
                            onStyleLoaded(style)
                        }
                    }
                    R.id.style_xray -> {
                        map.loadStyleUri(resources.getString(R.string.XRAY_STYLE_URL)) { style ->
                            onStyleLoaded(style)
                        }
                    }
                    R.id.route_load_gpx -> {
                        loadGPXDocument()
                    }
                    R.id.route_clear -> {
                        map.getStyle { style ->
                            style.getLayer("ROUTE")?.visibility(Visibility.NONE)
                        }
                        routePoints.clear()
                        findViewById<TextView>(R.id.routeDetails).visibility = View.INVISIBLE
                    }
                    R.id.hillshading -> {
                        mapView.getMapboxMap().getStyle { style ->
                            style.getLayer("hillshading")?.let { layer ->
                                layer.visibility(if (layer.visibility == Visibility.NONE) Visibility.VISIBLE else Visibility.NONE)
                            }
                        }
                    }
                    R.id.keepScreenOn -> {
                        screenAlwaysOn = !screenAlwaysOn
                        if (screenAlwaysOn)
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        else
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    R.id.saveTrackGpx -> {
                        Log.i(TAG, "TODO: saveTrackGpx()")
                    }
                }
                true
            }
            inflate(R.menu.main_menu)
            show()
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
                            val distance = TurfMeasurement.length(geometry, UNIT_METERS)
                            val routeDetails: TextView = findViewById(R.id.routeDetails)

                            if (distance > 5000) {
                                routeDetails.text = "${routePoints.size} Wpts\n${
                                    DecimalFormat("#.0 km").format(distance / 1000)
                                }"
                            } else {
                                routeDetails.text = "${routePoints.size} Wpts\n${
                                    DecimalFormat("# m").format(distance)
                                }"
                            }
                            routeDetails.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

    private fun saveGPXDocument() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, "route.gpx")
        }
        saveGPXLauncher.launch(intent)
    }

    private var saveGPXLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri: Uri? = result.data?.data
                Log.i(TAG, "saving '${uri?.path}'...")
                if (uri != null) {
                    try {
                        contentResolver.openFileDescriptor(uri, "w")?.use {
                            FileOutputStream(it.fileDescriptor).use { outputStream ->
                                val track = GPX.builder().addTrack { track ->
                                    distanceMeasurementPoints.forEach { point ->
                                        track.addSegment { segment ->
                                            segment.addPoint(
                                                WayPoint.of(
                                                    point.latitude(),
                                                    point.longitude()
                                                )
                                            )
                                        }
                                    }
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
    }
}