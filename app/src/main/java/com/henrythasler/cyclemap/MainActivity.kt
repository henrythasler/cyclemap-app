package com.henrythasler.cyclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.henrythasler.cyclemap.ui.theme.CyclemapAppTheme
import com.mapbox.common.MapboxOptions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        /**
         *  add public access token to local.properties
         *  MAPBOX_ACCESS_TOKEN=pk.eyJXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
         */
        MapboxOptions.accessToken = BuildConfig.MAPBOX_ACCESS_TOKEN

        setContent {
            CyclemapAppTheme {
                CycleMapView()
            }
        }
    }

    companion object {
        const val TAG: String = "Cyclemap"
    }
}