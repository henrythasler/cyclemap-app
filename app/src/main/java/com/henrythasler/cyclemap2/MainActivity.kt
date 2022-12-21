package com.henrythasler.cyclemap2

import android.os.Bundle
import android.view.Menu
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.henrythasler.cyclemap2.databinding.ActivityMainBinding
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.easeTo

var mapView: MapView? = null

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri("https://www.cyclemap.link/cyclemap-style.json") {
                style -> onStyleLoaded(style)
        }
    }

    private fun onStyleLoaded(style: Style) {
        val cameraPosition = CameraOptions.Builder()
            .zoom(12.0)
            .build()
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(5000).build()
        mapView?.getMapboxMap()?.easeTo(cameraPosition, mapAnimationOptions)
    }
}