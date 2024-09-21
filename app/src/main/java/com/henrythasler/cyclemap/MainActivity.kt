package com.henrythasler.cyclemap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.henrythasler.cyclemap.ui.theme.CyclemapAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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