package com.henrythasler.cyclemap

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.henrythasler.cyclemap.ui.theme.CyclemapAppTheme
import com.mapbox.common.location.DeviceLocationProvider
import com.mapbox.common.location.LocationObserver
import com.mapbox.common.location.LocationServiceFactory
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental

class MainActivity : ComponentActivity() {
    private lateinit var sharedState: SharedState

    private val timerHandler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var customLocationService: LocationService
    private var locationServiceBound: Boolean = false

    private val locationService: com.mapbox.common.location.LocationService =
        LocationServiceFactory.getOrCreate()
    private var locationProvider: DeviceLocationProvider? = null

    /** Defines callbacks for service binding, passed to bindService()  */
    private val locationServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as LocationService.LocalBinder
            customLocationService = binder.getService()
            locationServiceBound = true
            Log.i(TAG, "onServiceConnected")

            /** start the timer to regularly update the UI with the latest track (information) */
            timerHandler.post(timerRunnable)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            locationServiceBound = false
            Log.i(TAG, "onServiceDisconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharedState = SharedState()
        restoreSettings()

        setContent {
            CyclemapAppTheme {
                CycleMapView(
                    sharedState = sharedState,
                    enableLocationService = ::enableLocationService,
                    disableLocationService = ::disableLocationService,
                )
            }
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @OptIn(MapboxExperimental::class)
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val zoom = sharedState.mapViewportState.cameraState?.zoom?.toFloat()
        val lon = sharedState.mapViewportState.cameraState?.center?.longitude()?.toFloat()
        val lat = sharedState.mapViewportState.cameraState?.center?.latitude()?.toFloat()

        Log.i(TAG, "Saving state: zoom=$zoom, lon=$lon, lat=$lat")

        // see https://developer.android.com/training/data-storage/shared-preferences
        with(getPreferences(MODE_PRIVATE).edit()) {
            if (lat != null && lon != null && zoom != null) {
                putFloat(resources.getString(R.string.latitude_name), lat)
                putFloat(resources.getString(R.string.longitude_name), lon)
                putFloat(resources.getString(R.string.zoom_name), zoom)
            }
            commit()
        }
    }

    @OptIn(MapboxExperimental::class)
    private fun restoreSettings() {
        val sharedPref = getPreferences(MODE_PRIVATE) ?: return
        val zoom = sharedPref.getFloat(
            resources.getString(R.string.zoom_name),
            resources.getString(R.string.zoom_default).toFloat()
        ).toDouble()
        val lon = sharedPref.getFloat(
            resources.getString(R.string.longitude_name),
            resources.getString(R.string.longitude_default).toFloat()
        ).toDouble()

        val lat = sharedPref.getFloat(
            resources.getString(R.string.latitude_name),
            resources.getString(R.string.latitude_default).toFloat()
        ).toDouble()

//        val lon = resources.getString(R.string.longitude_default).toDouble()
//        val lat = resources.getString(R.string.latitude_default).toDouble()

        Log.i(TAG, "Restored state: zoom=$zoom, lon=$lon, lat=$lat")
        sharedState.mapViewportState.setCameraOptions {
            center(Point.fromLngLat(lon, lat))
            zoom(zoom)
            pitch(0.0)
            bearing(0.0)
        }
    }

    /**
     * It's basically an event-loop-based timer callback that does something and triggers itself again if the
     * right conditions are met.
     */
    private val timerRunnable: Runnable = object : Runnable {
        override fun run() {
            if (customLocationService.locations.size > 0) {
                Log.i(TAG, customLocationService.locations.last().toString())
                sharedState.addTrackPoint(Point.fromLngLat(
                    customLocationService.locations.last().longitude,
                    customLocationService.locations.last().latitude,
                ))
            }

            if(locationServiceBound) {
                timerHandler.postDelayed(
                    this,
                    resources.getInteger(R.integer.location_update_interval_ms).toLong()
                )
            }
        }
    }

    @OptIn(MapboxExperimental::class)
    private val locationObserver = LocationObserver { locations ->
        Log.i(TAG, "Location update received: $locations")
        if (locations.size > 0) {
            sharedState.mapViewportState.setCameraOptions {
                center(Point.fromLngLat(locations.last().longitude, locations.last().latitude))
            }
        }
    }

    private fun enableLocationService() {
        /** start the location service to record the track */
        if (!locationServiceBound) {
            Intent(this, LocationService::class.java).also { intent ->
                Log.i(TAG, "enabling LocationService")
                bindService(intent, locationServiceConnection, Context.BIND_AUTO_CREATE)
            }
        }

//        val result = locationService.getDeviceLocationProvider(null)
//        if (result.isValue) {
//            locationProvider = result.value!!
//            locationProvider!!.addLocationObserver(locationObserver);
//        } else {
//            Log.e(TAG, "Failed to get device location provider")
//        }
    }

    private fun disableLocationService() {
        /** stop the service when track recording is stopped */
        if (locationServiceBound) {
            Log.i(TAG, "disabling LocationService")
            unbindService(locationServiceConnection)
            locationServiceBound = false
        }
    }

    companion object {
        const val TAG: String = "Cyclemap"
    }
}