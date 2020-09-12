package com.henrythasler.cyclemap

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener;
import com.mapbox.mapboxsdk.location.OnLocationClickListener;
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.offline.OfflineManager
import com.mapbox.mapboxsdk.plugins.places.autocomplete.PlaceAutocomplete
import com.mapbox.mapboxsdk.plugins.places.autocomplete.model.PlaceOptions
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin


class MainActivity : AppCompatActivity(), OnMapReadyCallback, Style.OnStyleLoaded, PermissionsListener, OnCameraTrackingChangedListener, OnLocationClickListener {
    private var mapView: MapView? = null
    private lateinit var map: MapboxMap
    private lateinit var style: Style
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var permissionsManager: PermissionsManager
    private var isInTrackingMode: Boolean = false

    private val REQUEST_CODE_AUTOCOMPLETE: Int = 1

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

        mapView?.getMapAsync(this)
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
            map.animateCamera(
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


    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap

        map.setStyle(
            Style.Builder().fromUri("https://www.cyclemap.link/cyclemap-style.json")
        ) { style -> onStyleLoaded(style)}

//        map.addOnMapClickListener { point ->
//            val placeOptions: PlaceOptions = PlaceOptions.builder()
//                .backgroundColor(Color.parseColor("#e0ffffff"))
//                .proximity(Point.fromLngLat(point.latitude, point.longitude))
//                .build()
//            val intent = PlaceAutocomplete.IntentBuilder()
//                .accessToken(getString(R.string.mapbox_access_token))
//                .placeOptions(placeOptions)
//                .build(this)
//            startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE)
//
//            true
//        }
    }

    override fun onStyleLoaded(mapboxStyle: Style) {
        style = mapboxStyle

        val scaleBarPlugin = ScaleBarPlugin(mapView!!, map)
        val scaleBarOptions = ScaleBarOptions(this)
        scaleBarOptions
            .setMetricUnit(true)
            .setBarHeight(15f)
            .setTextSize(40f)
        scaleBarPlugin.create(scaleBarOptions)
        val uiSettings = map.uiSettings
        uiSettings.isRotateGesturesEnabled = false

        Toast.makeText(
            this,
            "onStyleLoaded()",
            Toast.LENGTH_LONG
        ).show()

        enableLocationComponent()

    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            Toast.makeText(
                this,
                "Location permissions granted",
                Toast.LENGTH_LONG
            ).show()

            // Get an instance of the component
            val locationComponent = map.locationComponent

            val locationComponentOptions = LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .trackingGesturesManagement(true)
                .pulseColor(Color.parseColor("#00c000"))
//                .pulseAlpha(.4f)
//                .pulseInterpolator(BounceInterpolator())
                .build()

            val locationComponentActivationOptions = LocationComponentActivationOptions
                .builder(this, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .build()

            // Activate with a built LocationComponentActivationOptions object
            locationComponent?.activateLocationComponent(locationComponentActivationOptions)

            // Enable to make component visible
            locationComponent?.isLocationComponentEnabled = true

            // Set the component's camera mode
            locationComponent?.cameraMode = CameraMode.TRACKING

            // Set the component's render mode
            locationComponent?.renderMode = RenderMode.COMPASS

            // Add the location icon click listener
            locationComponent.addOnLocationClickListener(this);

            // Add the camera tracking listener. Fires if the map camera is manually moved.
            locationComponent.addOnCameraTrackingChangedListener(this);

            findViewById<FloatingActionButton>(R.id.back_to_camera_tracking_mode).setOnClickListener {
                if (!isInTrackingMode) {
                    isInTrackingMode = true;
                    locationComponent.cameraMode = CameraMode.TRACKING;
                    Toast.makeText(
                        this, getString(R.string.tracking_enabled),
                        Toast.LENGTH_SHORT
                    ).show();
                } else {
                    Toast.makeText(
                        this, getString(R.string.tracking_already_enabled),
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }

        } else {
            Toast.makeText(
                this,
                "Requesting Location permissions",
                Toast.LENGTH_LONG
            ).show()
            permissionsManager = PermissionsManager(this)
            permissionsManager?.requestLocationPermissions(this)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: MutableList<String>?) {
        Toast.makeText(
            this,
            "onExplanationNeeded()",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        Toast.makeText(
            this,
            "onPermissionResult()",
            Toast.LENGTH_LONG
        ).show()

        if(granted) {
            enableLocationComponent()
        }
    }

    override fun onCameraTrackingChanged(currentMode: Int) {
    }

    override fun onCameraTrackingDismissed() {
        isInTrackingMode = false;
    }

    override fun onLocationComponentClick() {
        // Get an instance of the component
        val locationComponent = map.locationComponent
        if(locationComponent.lastKnownLocation != null) {
            Toast.makeText(
                this,
                String.format(getString(R.string.current_location), locationComponent.lastKnownLocation?.latitude, locationComponent.lastKnownLocation?.longitude),
                Toast.LENGTH_LONG
            ).show();
        }
    }
}