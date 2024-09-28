package com.henrythasler.cyclemap

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// https://developer.android.com/guide/components/bound-services.html
class LocationService : Service() {
    /** Binder given to clients */
    private val binder = LocalBinder()

    /** methods and properties for clients  */
//    private val _currentLocation = MutableStateFlow<Location?>(null)
//    val currentLocation: StateFlow<Location?> = _currentLocation
    var locations: MutableList<Location> = mutableListOf()

    private var fusedLocationClient: FusedLocationProviderClient? = null

    private var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { locations.add(it) }
//            _currentLocation.value = locationResult.lastLocation
        }
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        /** Return this instance of LocalService so clients can access public methods/properties */
        fun getService(): LocationService = this@LocationService
    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "onBind()")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")
        createNotificationChannel()
        requestLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand()")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy()")
        fusedLocationClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        // https://developer.android.com/develop/ui/views/notifications/channels
        val chan = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Location Service",
            NotificationManager.IMPORTANCE_LOW
        )
        chan.description = "Provides location while the App is in the background"
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE

        val notificationManager =
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        notificationManager.createNotificationChannel(chan)

        // https://developer.android.com/guide/components/foreground-services#start
        val notificationBuilder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        val notification: Notification = notificationBuilder.setOngoing(true)
            .setContentTitle("Cyclemap")
            .setContentText("Track recording active")
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
        startForeground(ONGOING_NOTIFICATION_ID, notification)
        Log.i(TAG, "Notification channel created.")
    }

    private fun requestLocationUpdates() {
        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
                .build()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val permission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permission == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient?.requestLocationUpdates(request, locationCallback, null)
        }
        else {
            Log.e(TAG, "Permission for ACCESS_FINE_LOCATION not granted.")
        }
    }

    companion object {
        const val TAG = "LocationService"
        const val LOCATION_UPDATE_INTERVAL: Long = 1000
        const val MIN_UPDATE_DISTANCE_METERS: Float = 5.0F
        const val NOTIFICATION_CHANNEL_ID = TAG
        const val ONGOING_NOTIFICATION_ID = 2
    }
}