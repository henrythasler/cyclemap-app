package com.henrythasler.cyclemap2

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.mapbox.maps.*
import com.mapbox.maps.extension.style.layers.Layer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.layers.properties.generated.Visibility
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location


var mapView: MapView? = null
var map: MapboxMap? = null

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        map = mapView?.getMapboxMap()

        map?.loadStyleUri(resources.getString(R.string.MAIN_STYLE_URL)) {
                style -> onStyleLoaded(style)
        }

        val locateButton: View = findViewById(R.id.locate)
        locateButton.setOnClickListener { onLocateButton()}

        val mDrawerLayout: DrawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // close drawer when item is tapped
            mDrawerLayout.closeDrawers()

            when (menuItem.itemId) {
                R.id.nav_hillshading -> {
                    val layer: Layer? = mapView?.getMapboxMap()?.getStyle()?.getLayer("hillshading");
                    layer?.visibility( if (layer?.visibility == Visibility.NONE) Visibility.VISIBLE else Visibility.NONE );
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

    private fun onStyleLoaded(style: Style) {
        val cameraPosition = CameraOptions.Builder()
            .zoom(12.0)
            .build()
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(1000).build()
        mapView?.getMapboxMap()?.easeTo(cameraPosition, mapAnimationOptions)
        // mapView?.getMapboxMap()?.setCamera(cameraPosition)

        // prevent certain UI operations
        mapView?.gestures?.rotateEnabled = false
        mapView?.gestures?.pitchEnabled = false

        Toast.makeText(this, map?.pixelForCoordinate(map!!.cameraState.center).toString(), Toast.LENGTH_LONG).show()

        // make sure we always zoom into the map center
        mapView?.gestures?.focalPoint = map?.pixelForCoordinate(map!!.cameraState.center)
    }

    private fun onLocateButton() {
        Toast.makeText(
            this, "hello",
            Toast.LENGTH_SHORT
        ).show();
    }
}