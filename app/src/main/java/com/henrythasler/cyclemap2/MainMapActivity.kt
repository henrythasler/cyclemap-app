package com.henrythasler.cyclemap2

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
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
import com.mapbox.maps.plugin.locationcomponent.location2
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement
import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.ref.WeakReference
import java.text.DecimalFormat


class MainMapActivity : AppCompatActivity() {
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var map: MapboxMap
    private lateinit var mapView: MapView
    private var followLocation: Boolean = false

    private var distanceMeasurement: Boolean = false
    private var lastClickedTimestamp: Long = 0
    private var distanceMeasurementPoints: MutableList<Point> = mutableListOf()

    private var routePoints: MutableList<Point> = mutableListOf()

    private var trackRecording: Boolean = false
    private var trackPoints: MutableList<Point> = mutableListOf()

    private val moveListener: OnMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            Log.d("App", "tracking disabled")
            followLocation = false
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {
            // this is a workaround to reliably set the focalPoint for double-tap-zoom
            mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
            Log.i("App", "onMoveEnd(): focalPoint=" + mapView.gestures.focalPoint.toString())
        }
    }

    private val cameraChangeListener = OnCameraChangeListener {
        if (distanceMeasurement) {
            updateDistanceMeasurement()
        }
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        if (followLocation)
            mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
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
            Log.i("App", "permissions ok")
        }

        // set up user interaction
        val menuButton: View = findViewById(R.id.menuButton)
        menuButton.setOnClickListener { view -> onMenuButton(view) }

        val locateButton: View = findViewById(R.id.locationButton)
        locateButton.setOnClickListener { view -> onLocateButton(view) }

        val crosshairButton: View = findViewById(R.id.crosshair)
        crosshairButton.setOnClickListener { view -> onCrosshairClick(view) }

        findViewById<View?>(R.id.saveAsRoute).setOnClickListener { saveGPXDocument() }

        // catch map events
        mapView.gestures.addOnMoveListener(moveListener)
        map.addOnCameraChangeListener(cameraChangeListener)
        mapView.location2.addOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)

        // set up drawer menu
/*
        val mDrawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // close drawer when item is tapped
            mDrawerLayout.closeDrawers()

            when (menuItem.itemId) {
                R.id.nav_hillshading -> {
                    mapView.getMapboxMap().getStyle {
                        it.getLayer("hillshading")?.let { layer ->
                            layer.visibility( if(layer.visibility == Visibility.NONE) Visibility.VISIBLE else Visibility.NONE)
                        }
                    }
                }
                R.id.nav_shareposition -> {
                    Toast.makeText(this, "nav_shareposition", Toast.LENGTH_LONG).show()
                }
            }
            true
        }
*/
/*
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = Color.parseColor("#80111111");
        window.navigationBarColor = Color.parseColor("#80111111");
 */
    }

    private fun onStyleLoaded(style: Style) {
        // prevent certain UI operations
        mapView.gestures.rotateEnabled = false
        mapView.gestures.pitchEnabled = false

        // make sure we always zoom into the map center
        mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
        Log.i("App", "onStyleLoaded(): focalPoint=" + mapView.gestures.focalPoint.toString())

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
            lineWidth(10.0)
            lineColor(getColor(R.color.colorRoute))
        })

    }

    private fun onCrosshairClick(view: View?) {
        var isDoubleClick = false
        if (SystemClock.elapsedRealtime() - lastClickedTimestamp < 500) {
            isDoubleClick = true;
        }
        lastClickedTimestamp = SystemClock.elapsedRealtime();

        var clearPoints = false
        val distanceText: TextView = findViewById(R.id.distanceText)
        if (isDoubleClick) {
            if (distanceMeasurement) {
                distanceMeasurement = false
                clearPoints = true
            }
        } else {
            if (distanceMeasurement) {
                distanceMeasurementPoints.add(map.cameraState.center)
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

        findViewById<View?>(R.id.saveAsRoute).visibility =
            if (distanceMeasurementPoints.size > 2) View.VISIBLE else View.INVISIBLE

        val drawLineSource =
            map.getStyle()?.getSourceAs<GeoJsonSource>("DISTANCE_MEASUREMENT_SOURCE")
        drawLineSource?.feature(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    distanceMeasurementPoints
                )
            )
        )

        map.getStyle() {
            it.getLayer("DISTANCE_MEASUREMENT")
                ?.visibility(if (distanceMeasurement) Visibility.VISIBLE else Visibility.NONE)
        }
    }

    private fun updateDistanceMeasurement() {
        distanceMeasurementPoints[distanceMeasurementPoints.lastIndex] = map.cameraState.center
        val drawLineSource =
            map.getStyle()?.getSourceAs<GeoJsonSource>("DISTANCE_MEASUREMENT_SOURCE")
        drawLineSource?.feature(
            Feature.fromGeometry(
                LineString.fromLngLats(
                    distanceMeasurementPoints
                )
            )
        )

        val distance =
            TurfMeasurement.length(LineString.fromLngLats(distanceMeasurementPoints), UNIT_METERS)
        val distanceText: TextView = findViewById(R.id.distanceText)

        if (distance > 5000) {
            distanceText.text = DecimalFormat("#.0 km").format(distance / 1000)
        } else {
            distanceText.text = DecimalFormat("# m").format(distance)
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
        Log.i("App", "onSaveInstanceState()")

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
            "App",
            "restoreSettings(): center=" + cameraPosition.center?.coordinates()
                .toString() + " zoom=" + cameraPosition.zoom.toString()
        )
        // set camera position
        map.setCamera(cameraPosition)
    }

    private fun onLocateButton(view: View) {
        if (mapView.location2.enabled) {
            // jump back to current location if the user has manually scrolled away
            if (!followLocation) {
                Log.i("App", "jumping back to location...")
                followLocation = true
            }
            // disable tracking if the user is currently tracking the position
            else {
                Log.i("App", "disabling location tracking...")
                mapView.location2.enabled = false
            }
        } else {
            // enable location tracking
            Log.i("App", "enabling location tracking...")
            mapView.location2.apply {
                enabled = true
                pulsingEnabled = true
                puckBearingEnabled = true
            }
            followLocation = true
        }
    }

    private fun onMenuButton(view: View?) {
        PopupMenu(this, view).apply {
            // MainActivity implements OnMenuItemClickListener
            setOnMenuItemClickListener { it ->
                when (it.itemId) {
                    R.id.style_cyclemap -> {
                        map.loadStyleUri(resources.getString(R.string.CYCLEMAP_STYLE_URL)) { style ->
                            onStyleLoaded(
                                style
                            )
                        }
                    }
                    R.id.style_shadow -> {
                        map.loadStyleUri(resources.getString(R.string.SHADOW_STYLE_URL)) { style ->
                            onStyleLoaded(
                                style
                            )
                        }
                    }
                    R.id.style_xray -> {
                        map.loadStyleUri(resources.getString(R.string.XRAY_STYLE_URL)) { style ->
                            onStyleLoaded(
                                style
                            )
                        }
                    }
                    R.id.route_load_gpx -> {
                        loadGPXDocument()
                    }
                    R.id.route_clear -> {
                        map.getStyle { style ->
                            style.getLayer("ROUTE")?.let { layer ->
                                layer.visibility(Visibility.NONE)
                            }
                        }
                        findViewById<TextView>(R.id.routeDetails).visibility = View.INVISIBLE
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
                Log.i("App", "loading '${uri?.path}'...")
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
                                routeDetails.text = "${routePoints.size.toString()} Wpts\n${DecimalFormat("#.0 km").format(distance / 1000)}"
                            } else {
                                routeDetails.text = "${routePoints.size.toString()} Wpts\n${DecimalFormat("# m").format(distance)}"
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
                Log.i("App", "saving '${uri?.path}'...")
                if (uri != null) {
                    try {
                        contentResolver.openFileDescriptor(uri, "w")?.use {
                            FileOutputStream(it.fileDescriptor).use { outputStream ->
                                val track = GPX.builder().addTrack { track ->
                                    distanceMeasurementPoints.forEach { point ->
                                        track.addSegment { segment ->
                                            segment.addPoint(WayPoint.of(point.latitude(), point.longitude()))
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
}