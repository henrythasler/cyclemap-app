package com.henrythasler.cyclemap2

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.Layer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.gestures.GesturesPlugin
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location


var mapView: MapView? = null
var map: MapboxMap? = null

class MainActivity : AppCompatActivity() {

    private val moveListener: OnMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {

            // this is a workaround to reliably set the focalPoint for double-tap-zoom
            mapView?.gestures?.focalPoint = map?.pixelForCoordinate(map!!.cameraState.center)
            Log.i("App", "onMoveEnd(): focalPoint=" + mapView?.gestures?.focalPoint.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        map = mapView?.getMapboxMap()

        restoreSettings()

        map?.loadStyleUri(resources.getString(R.string.MAIN_STYLE_URL)) {
                style -> onStyleLoaded(style)
        }

        // set up user interaction
        val locateButton: View = findViewById(R.id.locate)
        locateButton.setOnClickListener { onLocateButton()}

        mapView?.gestures?.addOnMoveListener(moveListener)

        // set up drawer menu
        val mDrawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // close drawer when item is tapped
            mDrawerLayout.closeDrawers()

            when (menuItem.itemId) {
                R.id.nav_hillshading -> {
                    mapView?.getMapboxMap()?.getStyle() {
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

/*
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.statusBarColor = Color.parseColor("#80111111");
        window.navigationBarColor = Color.parseColor("#80111111");
 */
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Log.i("App", "onSaveInstanceState()")

        // see https://developer.android.com/training/data-storage/shared-preferences
        with(getPreferences(Context.MODE_PRIVATE).edit()) {
            putFloat(getString(R.string.STATE_LATITUDE), map!!.cameraState.center.latitude().toFloat())
            putFloat(getString(R.string.STATE_LONGITUDE), map!!.cameraState.center.longitude().toFloat())
            putFloat(getString(R.string.STATE_ZOOM), map!!.cameraState.zoom.toFloat())
            commit()
        }
    }

    private fun restoreSettings() {
        val sharedPref = getPreferences(Context.MODE_PRIVATE) ?: return
        val cameraPosition = CameraOptions.Builder()
            .zoom(sharedPref.getFloat(getString(R.string.STATE_ZOOM), getString(R.string.DEFAULT_ZOOM).toFloat()).toDouble())
            .center(Point.fromLngLat(
                sharedPref.getFloat(getString(R.string.STATE_LONGITUDE), getString(R.string.DEFAULT_LONGITUDE).toFloat()).toDouble(),
                sharedPref.getFloat(getString(R.string.STATE_LATITUDE), getString(R.string.DEFAULT_LATITUDE).toFloat()).toDouble()
            ))
            .build()
        Log.i("App", "restoreSettings(): center=" + cameraPosition.center?.coordinates().toString() + " zoom=" + cameraPosition.zoom.toString())
        // set camera position
        map?.setCamera(cameraPosition)
    }

    private fun onStyleLoaded(style: Style) {
        // prevent certain UI operations
        mapView?.gestures?.rotateEnabled = false
        mapView?.gestures?.pitchEnabled = false

        // make sure we always zoom into the map center
        mapView?.gestures?.focalPoint = map?.pixelForCoordinate(map!!.cameraState.center)
        Log.i("App", "onStyleLoaded(): focalPoint=" + mapView?.gestures?.focalPoint.toString())
    }

    private fun onLocateButton() {
        Toast.makeText(
            this, "hello",
            Toast.LENGTH_SHORT
        ).show();
    }
}