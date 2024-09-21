package com.henrythasler.cyclemap

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.henrythasler.cyclemap.ui.theme.CyclemapAppTheme
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxExperimental

class MainActivity : ComponentActivity() {
    private lateinit var sharedState: SharedState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharedState = SharedState()
        restoreSettings()

        setContent {
            CyclemapAppTheme {
                CycleMapView(
                    sharedState = sharedState,
                )
            }
        }
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

        Log.i(TAG, "Restored state: zoom=$zoom, lon=$lon, lat=$lat")
        sharedState.mapViewportState.setCameraOptions {
            center(Point.fromLngLat(lon, lat))
            zoom(zoom)
            pitch(0.0)
            bearing(0.0)
        }
    }

    companion object {
        const val TAG: String = "Cyclemap"
    }
}