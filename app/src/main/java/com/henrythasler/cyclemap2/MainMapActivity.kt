package com.henrythasler.cyclemap2

import android.os.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.addLayerBelow
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures


class MainMapActivity : AppCompatActivity() {
    private lateinit var map: MapboxMap
    private lateinit var mapView: MapView

    private var followLocation: Boolean = false

    /** setup and interface for the location service to provide location updates when the
     * app is minimized */
    private var distanceMeasurementPoints: MutableList<Point> = mutableListOf()

    private var routePoints: MutableList<Point> = mutableListOf()

    private var trackPoints: List<Point> = listOf()
    private val timerHandler = Handler(Looper.getMainLooper())


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
            // make sure we always zoom into the map center
            mapView.gestures.focalPoint = map.pixelForCoordinate(map.cameraState.center)
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

        // catch map events
        mapView.gestures.addOnMoveListener(moveListener)
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


    companion object {
        const val TAG: String = "Cyclemap"
        const val TRACK_RECORDING_INTERVAL: Long = 1000
        const val ODOMETER_UPDATE_INTERVAL: Long = 1000
        const val TRACK_SIMPLIFY_TOLERANCE: Double = 0.00005
    }
}