package com.henrythasler.cyclemap2

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
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
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location2
import com.mapbox.turf.TurfConstants.UNIT_DEFAULT
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement
import java.lang.ref.WeakReference
import java.text.DecimalFormat

class MainMapActivity : AppCompatActivity() {
    private lateinit var locationPermissionHelper: LocationPermissionHelper
    private lateinit var map: MapboxMap
    private lateinit var mapView: MapView
    private var trackingEnabled: Boolean = false

    private var distanceMeasurement: Boolean = false
    private var distanceMeasurementPoints = MutableList<Point>(2) { Point.fromLngLat(0.0, 0.0)}

    private val moveListener: OnMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            Log.d("App", "tracking disabled")
            trackingEnabled = false
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            if (distanceMeasurement) {
                updateDistanceMeasurement()
            }
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {
            // this is a workaround to reliably set the focalPoint for double-tap-zoom
            mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
            Log.i("App", "onMoveEnd(): focalPoint=" + mapView.gestures.focalPoint.toString())
        }
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        if (trackingEnabled)
            mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_map)
        mapView = findViewById(R.id.mapView)
        map = mapView.getMapboxMap()

        restoreSettings()

        map.loadStyleUri(resources.getString(R.string.MAIN_STYLE_URL)) { style ->
            onStyleLoaded(style)
        }

        // location tracking
        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
        locationPermissionHelper.checkPermissions {
            Log.i("App", "permissions ok")
        }

        // set up user interaction
        val locateButton: View = findViewById(R.id.imageButton3)
        locateButton.setOnClickListener { view -> onLocateButton(view) }

        val crosshairButton: View = findViewById(R.id.crosshair)
        crosshairButton.setOnClickListener { view -> onCrosshairClick(view) }


        mapView.gestures.addOnMoveListener(moveListener)
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

        style.addSource(
            geoJsonSource("DISTANCE_MEASUREMENT_SOURCE") {
                feature(Feature.fromGeometry(LineString.fromLngLats(distanceMeasurementPoints)))
            }
        )

        style.addLayer(lineLayer("DISTANCE_MEASUREMENT", "DISTANCE_MEASUREMENT_SOURCE") {
            lineCap(LineCap.ROUND)
            lineDasharray(listOf(0.0, 2.0))
            lineJoin(LineJoin.ROUND)
            lineOpacity(1.0)
            lineWidth(5.0)
            lineColor(getColor(R.color.colorDistanceMeasurement))
        })
    }

    private fun onCrosshairClick(view: View?) {
        distanceMeasurement = !distanceMeasurement

        val distanceText: TextView = findViewById(R.id.distanceText)
        distanceText.text = "0 m"
        distanceText.visibility = if(distanceMeasurement) View.VISIBLE else View.INVISIBLE

        distanceMeasurementPoints[0] = map.cameraState.center
        distanceMeasurementPoints[1] = distanceMeasurementPoints[0]

        val drawLineSource = map.getStyle()?.getSourceAs<GeoJsonSource>("DISTANCE_MEASUREMENT_SOURCE")
        drawLineSource?.feature(Feature.fromGeometry(LineString.fromLngLats(distanceMeasurementPoints)))

        map.getStyle() {
            it.getLayer("DISTANCE_MEASUREMENT")?.visibility(if(distanceMeasurement) Visibility.VISIBLE else Visibility.NONE)
        }
    }

    private fun updateDistanceMeasurement() {
        distanceMeasurementPoints[1] = map.cameraState.center
        val drawLineSource = map.getStyle()?.getSourceAs<GeoJsonSource>("DISTANCE_MEASUREMENT_SOURCE")
        drawLineSource?.feature(Feature.fromGeometry(LineString.fromLngLats(distanceMeasurementPoints)))

        val distance = TurfMeasurement.length(LineString.fromLngLats(distanceMeasurementPoints), UNIT_METERS)
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
            if (!trackingEnabled) {
                Log.i("App", "jumping back to location...")
                trackingEnabled = true
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
            trackingEnabled = true
        }
    }
}