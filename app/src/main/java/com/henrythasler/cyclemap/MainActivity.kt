package com.henrythasler.cyclemap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.mapbox.android.core.location.*
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener
import com.mapbox.mapboxsdk.location.OnLocationClickListener
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.offline.OfflineManager
import com.mapbox.mapboxsdk.style.layers.Layer
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.visibility
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin
import com.mapbox.search.*
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchResultType
import com.mapbox.search.ui.view.SearchBottomSheetView
import com.mapbox.search.ui.view.category.Category
import com.mapbox.search.ui.view.category.SearchCategoriesBottomSheetView
import com.mapbox.search.ui.view.place.SearchPlace
import com.mapbox.search.ui.view.place.SearchPlaceBottomSheetView
import com.mapbox.turf.TurfConstants.UNIT_METERS
import com.mapbox.turf.TurfMeasurement
import com.nbsp.materialfilepicker.MaterialFilePicker
import com.nbsp.materialfilepicker.ui.FilePickerActivity
import io.jenetics.jpx.GPX
import java.lang.ref.WeakReference
import java.text.DecimalFormat
import java.util.regex.Pattern
import kotlin.math.abs

class MainActivity : AppCompatActivity(), OnMapReadyCallback, Style.OnStyleLoaded,
    PermissionsListener, OnCameraTrackingChangedListener, OnLocationClickListener {
    private var mapView: MapView? = null
    private lateinit var map: MapboxMap
    private lateinit var style: Style
    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var reverseGeocoding: ReverseGeocodingSearchEngine
    private lateinit var searchRequestTask: SearchRequestTask

    private lateinit var searchBottomSheetView: SearchBottomSheetView
    private lateinit var placeBottomSheetView: SearchPlaceBottomSheetView
    private lateinit var categoriesBottomSheetView: SearchCategoriesBottomSheetView
    private lateinit var cardsMediator: SearchViewBottomSheetsMediator
    private lateinit var mapCrosshair: ImageView
    private lateinit var mapDistance: TextView
    private lateinit var trackRecordButton: Button
    private var locationEngine: LocationEngine? = null

    private var measureDistance = false
    private var distanceLine = ArrayList<Point>(2)

    private var isInTrackingMode: Boolean = false

    private var enableTrackLogging = false
    private var trackPoints = ArrayList<Point>()
    private var customLocationEngineCallback = CustomLocationEngineCallback(this)

    private var recentPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString()
    private var routePoints = ArrayList<Point>()


    @RequiresApi(Build.VERSION_CODES.M)
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

                R.id.menu_show_hillshading -> {
                    Toast.makeText(this, "menu_show_hillshading", Toast.LENGTH_LONG).show()
                    val layer: Layer? = style.getLayer("hillshading")
                    if (Property.VISIBLE == layer?.visibility?.getValue()) {
                        layer?.setProperties(visibility(Property.NONE))
                    } else {
                        layer?.setProperties(visibility(Property.VISIBLE))
                    }
                }
                R.id.menu_share_position -> {
                    startActivity(
                        IntentUtils.shareIntent(
                            map.cameraPosition.target,
                            map.cameraPosition.zoom
                        )
                    )
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
                R.id.menu_track_record -> {
                    toogleRecordTrack()
                    trackRecordButton.performClick()
                }
                R.id.menu_track_clear -> {
                    trackPoints.clear()
                    style.getSourceAs<GeoJsonSource>("DRAW_TRACK_LAYER_SOURCE_ID")?.setGeoJson(LineString.fromLngLats(trackPoints))
                }
                R.id.menu_route_load -> {
                    checkPermissionsAndOpenFilePicker()
                }
                R.id.menu_route_clear -> {
                    routePoints.clear()
                    style.getSourceAs<GeoJsonSource>("ROUTE_LAYER_SOURCE_ID")?.setGeoJson(LineString.fromLngLats(routePoints))
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

        reverseGeocoding = MapboxSearchSdk.createReverseGeocodingSearchEngine()

        // Search UI
        searchBottomSheetView = findViewById(R.id.search_view)
        placeBottomSheetView = findViewById(R.id.search_place_view)
        categoriesBottomSheetView =
            findViewById(R.id.search_categories_view)

        val configuration = SearchBottomSheetView.Configuration(
            hotCategories = listOf(
                Category.RESTAURANTS,
                Category.PARKING,
                Category.HOTEL,
                Category.GAS_STATION
            )
        )
        searchBottomSheetView.initializeSearch(savedInstanceState, configuration)
        searchBottomSheetView.isHideableByDrag = true
        searchBottomSheetView.hide()

        cardsMediator = SearchViewBottomSheetsMediator(
            searchBottomSheetView,
            placeBottomSheetView,
            categoriesBottomSheetView
        )

        savedInstanceState?.let {
            cardsMediator.onRestoreInstanceState(it)
        }

        // Process bottom sheets events
        cardsMediator.addSearchBottomSheetsEventsListener(object :
            SearchViewBottomSheetsMediator.SearchBottomSheetsEventsListener {
            override fun onOpenPlaceBottomSheet(place: SearchPlace) {
                Toast.makeText(
                    applicationContext,
                    "onOpenPlaceBottomSheet()",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onOpenCategoriesBottomSheet(category: Category) {
                Toast.makeText(
                    applicationContext,
                    "onOpenCategoriesBottomSheet()",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onBackToMainBottomSheet() {
                Toast.makeText(
                    applicationContext,
                    "onBackToMainBottomSheet()",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        placeBottomSheetView.addOnNavigateClickListener(object :
            SearchPlaceBottomSheetView.OnNavigateClickListener {
            override fun onNavigateClick(searchPlace: SearchPlace) {
                val navigateZoom =
                    if (listOf(SearchResultType.POI).contains(searchPlace.resultType)) {
                        17.0
                    } else {
                        14.0
                    }

                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(
                                LatLng(
                                    searchPlace.coordinate.latitude(),
                                    searchPlace.coordinate.longitude()
                                )
                            )
                            .zoom(navigateZoom)
                            .build()
                    ), 4000
                )
//                startActivity(IntentUtils.geoIntent(searchPlace.coordinate))
            }
        })

        placeBottomSheetView.addOnShareClickListener(object :
            SearchPlaceBottomSheetView.OnShareClickListener {
            override fun onShareClick(searchPlace: SearchPlace) {
                startActivity(IntentUtils.shareIntent(searchPlace))
            }
        })

        categoriesBottomSheetView.addCategoryLoadingStateListener(object :
            SearchCategoriesBottomSheetView.CategoryLoadingStateListener {
            override fun onLoadingStart(category: Category) {}

            override fun onCategoryResultsLoaded(
                category: Category,
                searchResults: List<SearchResult>
            ) {
                Toast.makeText(
                    applicationContext,
                    "Loaded ${searchResults.size} results for $category",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onLoadingError(category: Category) {}
        })

        mapCrosshair = findViewById(R.id.mapCrosshair)
        mapCrosshair.setOnClickListener {
            if (measureDistance) {
                measureDistance = false
                mapDistance.visibility = View.INVISIBLE
                distanceLine.clear()
                val drawLineSource = style.getSourceAs<GeoJsonSource>("DRAW_LINE_LAYER_SOURCE_ID")
                drawLineSource?.setGeoJson(LineString.fromLngLats(distanceLine))
            } else {
                measureDistance = true
                mapDistance.text = "0 m"
                mapDistance.visibility = View.VISIBLE
                distanceLine.clear()
                distanceLine.add(
                    Point.fromLngLat(
                        map.cameraPosition.target.longitude,
                        map.cameraPosition.target.latitude
                    )
                )
                distanceLine.add(distanceLine[0])

            }
        }

        mapDistance = findViewById(R.id.mapDistance)

        // Track logging interactions
        trackRecordButton = findViewById(R.id.recordTrack)
        trackRecordButton.setOnLongClickListener {
            toogleRecordTrack();
            true
        }
        trackRecordButton.setOnClickListener {
            showTrackStatistics();
            true
        }
    }

    private fun showTrackStatistics() {
        if(trackPoints.size > 1) {
        val distance = TurfMeasurement.distance(trackPoints.first(), trackPoints.last(), UNIT_METERS)
        var distanceText = "0 km"
        if (distance > 5000) {
            distanceText = DecimalFormat("#.0 km").format(distance / 1000)
        } else {
            distanceText = DecimalFormat("# m").format(distance)
        }

        Toast.makeText(
            this@MainActivity,
            distanceText,
            Toast.LENGTH_LONG
        ).show()
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

    @SuppressLint("ApplySharedPref")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)

        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            val latitude = map.cameraPosition.target.latitude
            val longitude = map.cameraPosition.target.longitude
            val zoom = map.cameraPosition.zoom

            // do NOT save Null-Island position
            if (abs(latitude) > 0.1 && abs(longitude) > 0.1) {
                putFloat(getString(R.string.saved_latitude), latitude.toFloat())
                putFloat(getString(R.string.saved_longitude), longitude.toFloat())
                putFloat(getString(R.string.saved_zoom), zoom.toFloat())
                commit()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        locationEngine?.removeLocationUpdates(customLocationEngineCallback);
        searchRequestTask.cancel()
        mapView?.onDestroy()
    }

    override fun onBackPressed() {
        if (!cardsMediator.handleOnBackPressed()) {
            super.onBackPressed()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onMapReady(mapboxMap: MapboxMap) {
        map = mapboxMap

        map.setStyle(
            Style.Builder().fromUri("https://www.cyclemap.link/cyclemap-style.json")
        ) { style -> onStyleLoaded(style) }

        map.addOnMapLongClickListener { point: LatLng ->
            val options = ReverseGeoOptions(
                center = Point.fromLngLat(point.longitude, point.latitude),
                limit = 1,
                reverseMode = ReverseMode.DISTANCE,
                types = listOf(QueryType.POI, QueryType.LOCALITY, QueryType.PLACE)
            )
            searchRequestTask = reverseGeocoding.search(options, searchCallback)
            true
        }

        map.addOnMapClickListener {
            if (searchBottomSheetView.isHidden()) {
                searchBottomSheetView.open()
            } else {
                searchBottomSheetView.hide()
            }
            true
        }

        mapboxMap.addOnCameraMoveListener {
            if (measureDistance) {
                updateDistanceMeasurement()
            }
        }
    }

    private fun toogleRecordTrack() {
        if (enableTrackLogging) {
            enableTrackLogging = false
            trackRecordButton.setBackgroundResource(R.drawable.record_button_inactive);
            style.getSourceAs<GeoJsonSource>("DRAW_TRACK_LAYER_SOURCE_ID")?.setGeoJson(LineString.fromLngLats(trackPoints))
            Toast.makeText(
                this@MainActivity,
                getString(R.string.toastTrackLoggingPaused),
                Toast.LENGTH_LONG
            ).show()
        } else {
            enableTrackLogging = true
            trackRecordButton.setBackgroundResource(R.drawable.record_button_active);
            Toast.makeText(
                this@MainActivity,
                getString(R.string.trackLoggingActive),
                Toast.LENGTH_LONG
            ).show()
        }
    }


    private fun checkPermissionsAndOpenFilePicker() {

//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
//            addCategory(Intent.CATEGORY_OPENABLE)
//            type = "*/*"
//
//            // Optionally, specify a URI for the file that should appear in the
//            // system file picker when it loads.
//            putExtra(DocumentsContract.EXTRA_INITIAL_URI, "/storage")
//        }
//        startActivityForResult(intent, FILE_PICKER_REQUEST_CODE)


        val permissionGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        if (permissionGranted) {
            MaterialFilePicker()
                // Pass a source of context. Can be:
                //    .withActivity(Activity activity)
                //    .withFragment(Fragment fragment)
                //    .withSupportFragment(androidx.fragment.app.Fragment fragment)
                .withActivity(this)
                // With cross icon on the right side of toolbar for closing picker straight away
                .withCloseMenu(true)
                // Entry point path (user will start from it)
                .withPath(recentPath)
                // Root path (user won't be able to come higher than it)
                .withRootPath("/storage")
                // Showing hidden files
                .withHiddenFiles(true)
                // Want to choose only jpg images
                .withFilter(Pattern.compile(".*\\.(gpx|kml)$"))
                // Don't apply filter to directories names
                .withFilterDirectories(false)
                .withTitle("Sample title")
                .withRequestCode(FILE_PICKER_REQUEST_CODE)
                .start()
        } else {
            if (shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "Allow external storage reading", Toast.LENGTH_SHORT).show()
            } else {
                requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    FILE_PERMISSIONS_REQUEST_CODE
                )
            }
        }

//        when {
//            ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.READ_EXTERNAL_STORAGE
//            ) == PackageManager.PERMISSION_GRANTED -> {
//                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
//            }
//            shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) -> {
//            // In an educational UI, explain to the user why your app requires this
//            // permission for a specific feature to behave as expected. In this UI,
//            // include a "cancel" or "no thanks" button that allows the user to
//            // continue using your app without granting the permission.
//            Toast.makeText(this, "Allow external storage reading", Toast.LENGTH_SHORT).show()
//        }
//            else -> {
//                // You can directly ask for the permission.
//                requestPermissions(this,
//                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
//                    FILE_PERMISSIONS_REQUEST_CODE)
//            }
//        }


//        if (permissionGranted) {
//        } else {
//            // You can directly ask for the permission.
//            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), FILE_PERMISSIONS_REQUEST_CODE)
//            Toast.makeText(this, "Allow external storage reading", Toast.LENGTH_SHORT).show()
//        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == FILE_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_GRANTED) {
                checkPermissionsAndOpenFilePicker()
            } else {
                Toast.makeText(this, "Please allow external storage reading", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDistanceMeasurement() {
        distanceLine[1] = Point.fromLngLat(
            map.cameraPosition.target.longitude,
            map.cameraPosition.target.latitude
        )

        val drawLineSource = style.getSourceAs<GeoJsonSource>("DRAW_LINE_LAYER_SOURCE_ID")
        drawLineSource?.setGeoJson(LineString.fromLngLats(distanceLine))
        val distance = TurfMeasurement.distance(distanceLine[0], distanceLine[1], UNIT_METERS)
        if (distance > 5000) {
            mapDistance.text = DecimalFormat("#.0 km").format(distance / 1000)
        } else {
            mapDistance.text = DecimalFormat("# m").format(distance)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onStyleLoaded(mapboxStyle: Style) {
        style = mapboxStyle

        val scaleBarPlugin = ScaleBarPlugin(mapView!!, map)
        val scaleBarOptions = ScaleBarOptions(this)
        scaleBarOptions
            .setMetricUnit(true)
            .setBarHeight(15f)
            .setTextSize(40f)
        scaleBarPlugin.create(scaleBarOptions)

        map.uiSettings.isRotateGesturesEnabled = false

        // restore previous map position
        val sharedPref = getPreferences(Context.MODE_PRIVATE)
        val defaultLng = resources.getString(R.string.DEFAULT_LOCATION_LNG).toFloat()
        val defaultLat = resources.getString(R.string.DEFAULT_LOCATION_LAT).toFloat()
        val defaultZoom = resources.getString(R.string.DEFAULT_ZOOM).toFloat()

        val position = CameraPosition.Builder()
            .target(
                LatLng(
                    sharedPref.getFloat(getString(R.string.saved_latitude), defaultLng).toDouble(),
                    sharedPref.getFloat(getString(R.string.saved_longitude), defaultLat).toDouble()
                )
            )
            .zoom(sharedPref.getFloat(getString(R.string.saved_zoom), defaultZoom).toDouble())
            .build()
        map.moveCamera(CameraUpdateFactory.newCameraPosition(position));

        enableLocationComponent()

        style.addSource(GeoJsonSource("DRAW_LINE_LAYER_SOURCE_ID"));
        style.addLayer(
            LineLayer("DRAW_LINE_LAYER", "DRAW_LINE_LAYER_SOURCE_ID").withProperties(
                PropertyFactory.lineDasharray(arrayOf(.001f, 2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineColor(getColor(R.color.colorMeasureDistance))
            )
        )

        style.addSource(GeoJsonSource("DRAW_TRACK_LAYER_SOURCE_ID"));
        style.addLayer(
            LineLayer("DRAW_TRACK_LAYER", "DRAW_TRACK_LAYER_SOURCE_ID").withProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineColor(getColor(R.color.trackLine))
            )
        )

        style.addSource(GeoJsonSource("ROUTE_LAYER_SOURCE_ID"));
        style.addLayer(
            LineLayer("ROUTE_LAYER", "ROUTE_LAYER_SOURCE_ID").withProperties(
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineWidth(10f),
                PropertyFactory.lineColor(getColor(R.color.routeLine))
            )
        )
    }

    @SuppressWarnings("MissingPermission")
    private fun enableLocationComponent() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Get an instance of the component
            val locationComponent = map.locationComponent

            val locationComponentOptions = LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .trackingGesturesManagement(true)
                .pulseColor(Color.parseColor("#00c000"))
                .pulseMaxRadius(32.0F)
//                .pulseAlpha(.4f)
//                .pulseInterpolator(BounceInterpolator())
                .build()

            val locationComponentActivationOptions = LocationComponentActivationOptions
                .builder(this, style)
                .locationComponentOptions(locationComponentOptions)
                .useDefaultLocationEngine(true)
                .build()

            // Activate with a built LocationComponentActivationOptions object
            locationComponent.activateLocationComponent(locationComponentActivationOptions)

            // Enable to make component visible
            locationComponent.isLocationComponentEnabled = true

            // Set the component's camera mode
            locationComponent.cameraMode = CameraMode.TRACKING

            // Set the component's render mode
            locationComponent.renderMode = RenderMode.COMPASS

            // Add the location icon click listener
            locationComponent.addOnLocationClickListener(this);

            // Add the camera tracking listener. Fires if the map camera is manually moved.
            locationComponent.addOnCameraTrackingChangedListener(this);

            // enable location tracking to show track on map
            locationEngine = LocationEngineProvider.getBestLocationEngine(this)
            val request: LocationEngineRequest = LocationEngineRequest.Builder(1000L)
                .setPriority(LocationEngineRequest.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxWaitTime(5000L).build()

            locationEngine?.requestLocationUpdates(request, customLocationEngineCallback, mainLooper)
            locationEngine?.getLastLocation(customLocationEngineCallback)

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
            permissionsManager.requestLocationPermissions(this)
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

        if (granted) {
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
        if (locationComponent.lastKnownLocation != null) {
            Toast.makeText(
                this,
                String.format(
                    getString(R.string.current_location),
                    locationComponent.lastKnownLocation?.latitude,
                    locationComponent.lastKnownLocation?.longitude
                ),
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private val searchCallback = object : SearchCallback {
        override fun onResults(results: List<SearchResult>) {
            if (results.isEmpty()) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.empty_result),
                    Toast.LENGTH_LONG
                ).show();

            } else {
                Toast.makeText(
                    this@MainActivity,
                    String.format(
                        getString(R.string.reverse_geocoding_result),
                        results.first().type,
                        results.first().name,
                        results.first().address
                    ),
                    Toast.LENGTH_LONG
                ).show();
            }
        }

        override fun onError(e: Exception) {
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)

        // used for Android-internal intent
//        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
//            resultData?.data?.also { uri ->
//                Toast.makeText(this, "Picked file: ${uri.path}.", Toast.LENGTH_LONG).show()
//                // Perform operations on the document using its URI.
//            }
//        }

        // used with MaterialFilePicker
        if (requestCode == FILE_PICKER_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData ?: throw IllegalArgumentException("data must not be null")

            val path = resultData.getStringExtra(FilePickerActivity.RESULT_FILE_PATH)
            recentPath = path.toString().substringBeforeLast("/")

            if (path != null) {
                Toast.makeText(this, "Picked file: ${path}.", Toast.LENGTH_LONG).show()
                try {
                    val points = GPX.read(path).tracks
                        .flatMap { it.segments }
                        .flatMap { it.points }

                    routePoints.clear()
                    points.forEach { point ->
                        routePoints.add(Point.fromLngLat(point.longitude.toDegrees(), point.latitude.toDegrees()))
                    }
                    style.getSourceAs<GeoJsonSource>("ROUTE_LAYER_SOURCE_ID")?.setGeoJson(LineString.fromLngLats(routePoints))
                }
                catch (e: RuntimeException) {
                    Toast.makeText(this, "Error loading file ${path.substringAfterLast("/")}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    inner class CustomLocationEngineCallback(activity: Activity) : LocationEngineCallback<LocationEngineResult> {
        private val activityRef = WeakReference(activity)

        override fun onSuccess(result: LocationEngineResult?) {
            val activity: Activity? = activityRef.get()

            if(enableTrackLogging) {
                trackPoints.add(Point.fromLngLat(result?.lastLocation?.longitude!!, result?.lastLocation?.latitude!!))
                style.getSourceAs<GeoJsonSource>("DRAW_TRACK_LAYER_SOURCE_ID")?.setGeoJson(LineString.fromLngLats(trackPoints))
                trackRecordButton.text = trackPoints.size.toString()
            }
        }
        override fun onFailure(exception: Exception) {
        }
    }

    companion object {
        private const val FILE_PERMISSIONS_REQUEST_CODE = 0
        private const val FILE_PICKER_REQUEST_CODE = 1
    }
}