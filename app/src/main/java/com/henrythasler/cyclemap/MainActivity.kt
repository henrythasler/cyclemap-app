package com.henrythasler.cyclemap

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.offline.OfflineManager
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin


class MainActivity : AppCompatActivity() {
    private var mapView: MapView? = null
    private var map: MapboxMap? = null
    private var symbolManager: SymbolManager? = null
    private var symbol: Symbol? = null
    private val REQUEST_CODE_AUTOCOMPLETE: Int = 1
    private lateinit var mDrawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)

        val fileSource = OfflineManager.getInstance(this)

        mDrawerLayout = findViewById(R.id.drawer_layout)

        // ref: https://tutorial.eyehunts.com/android/android-navigation-drawer-example-kotlin/
        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            // close drawer when item is tapped
            mDrawerLayout.closeDrawers()

            // Handle navigation view item clicks here.
            when (menuItem.itemId) {

                R.id.menu_share_position -> {
                    Toast.makeText(this, "menu_share_position", Toast.LENGTH_LONG).show()
                }
                R.id.menu_global_search -> {
                    Toast.makeText(this, "menu_global_search", Toast.LENGTH_LONG).show()
                }
                R.id.menu_my_places -> {
                    Toast.makeText(this, "menu_my_places", Toast.LENGTH_LONG).show()
                }
                R.id.menu_cache_ambient_invalidate -> {
                    fileSource.invalidateAmbientCache(object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() {
                            Toast.makeText(
                                this@MainActivity,
                                "invalidateAmbientCache() ok",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        override fun onError(message: String) {
                            Toast.makeText(
                                this@MainActivity,
                                "invalidateAmbientCache() error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }
                R.id.menu_cache_ambient_clear -> {
                    fileSource.clearAmbientCache(object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() {
                            Toast.makeText(
                                this@MainActivity,
                                "clearAmbientCache() ok",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        override fun onError(message: String) {
                            Toast.makeText(
                                this@MainActivity,
                                "clearAmbientCache() error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    })
                }
            }
            // Add code here to update the UI based on the item selected
            // For example, swap UI fragments here

            true
        }

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        fileSource.setMaximumAmbientCacheSize(
            resources.getInteger(R.integer.DESIRED_AMBIENT_CACHE_SIZE).toLong(),
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() {
                }

                override fun onError(message: String) {
                }
            })


        mapView?.getMapAsync { mapboxMap ->
            map = mapboxMap
            map?.setStyle(
                Style.Builder().fromUri("https://www.cyclemap.link/cyclemap-style.json")
            ) { style ->

                // create symbol manager object
                symbolManager = SymbolManager(mapView!!, mapboxMap, style)
                symbolManager?.iconAllowOverlap = true
                symbolManager?.iconIgnorePlacement = true

                symbolManager!!.addClickListener { symbol ->
                    Toast.makeText(
                        this,
                        String.format(
                            "latlng=(%.2f, %.2f)",
                            symbol.latLng.latitude,
                            symbol.latLng.longitude
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }

                symbolManager!!.addLongClickListener { symbol ->
                    symbolManager?.delete(symbol)
                    Toast.makeText(
                        this,
                        "delete marker",
                        Toast.LENGTH_LONG
                    ).show()

                    true
                }


                val scaleBarPlugin = ScaleBarPlugin(mapView!!, mapboxMap)
                val scaleBarOptions = ScaleBarOptions(this)
                scaleBarOptions
                    .setMetricUnit(true)
                    .setBarHeight(15f)
                    .setTextSize(40f)
                scaleBarPlugin.create(scaleBarOptions)
                val uiSettings = mapboxMap.uiSettings
                uiSettings.isRotateGesturesEnabled = false
            }


            mapboxMap.addOnMapLongClickListener { point ->
//                Toast.makeText(
//                    this,
//                    String.format("User clicked long at: %s", point.toString()),
//                    Toast.LENGTH_LONG
//                ).show()

                symbolManager!!.deleteAll()

//                val symbolOptions = SymbolOptions()
//                    .withLatLng(point)
//                    .withIconImage("star.white")
//                    .withIconSize(2.0f)
//                    .withSymbolSortKey(10.0f)
//                    .withDraggable(true)
//                symbol = symbolManager!!.create(symbolOptions)

                true
            }

            mapboxMap.addOnMapClickListener { point ->
//                Toast.makeText(this, String.format("User clicked at: %s", point.toString()), Toast.LENGTH_LONG).show()

                val placeOptions: PlaceOptions = PlaceOptions.builder()
                    .backgroundColor(Color.parseColor("#e0ffffff"))
                    .proximity(Point.fromLngLat(point.latitude, point.longitude))
                    .build()
                val intent = PlaceAutocomplete.IntentBuilder()
                    .accessToken(getString(R.string.mapbox_access_token))
                    .placeOptions(placeOptions)
                    .build(this)
                startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE)

                true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            val feature = PlaceAutocomplete.getPlace(data)
            map?.animateCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(
                            LatLng(
                                (feature.geometry() as Point).latitude(),
                                (feature.geometry() as Point).longitude()
                            )
                        )
                        .zoom(14.0)
                        .build()
                ), 4000
            )
        }
    }
}