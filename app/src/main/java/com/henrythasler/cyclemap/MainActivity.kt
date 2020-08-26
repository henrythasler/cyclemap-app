package com.henrythasler.cyclemap

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.*
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin


class MainActivity : AppCompatActivity() {
    private var mapView: MapView? = null
    private var map: MapboxMap? = null
    private var symbolManager: SymbolManager? = null
    private var symbol: Symbol? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)

        mapView?.getMapAsync { mapboxMap ->
            map = mapboxMap

            map?.setStyle(
                Style.Builder().fromUri("asset://cyclemap-style.json")
            ) { style ->

// create symbol manager object
                symbolManager = SymbolManager(mapView!!, mapboxMap, style)
                symbolManager?.iconAllowOverlap = true
                symbolManager?.iconIgnorePlacement = true

                symbolManager!!.addClickListener { symbol ->
                    Toast.makeText(
                        this,
                        String.format("latlng=(%.2f, %.2f)", symbol.latLng.latitude, symbol.latLng.longitude),
                        Toast.LENGTH_LONG
                    ).show()
                    true
                }

                symbolManager!!.addLongClickListener {symbol ->
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

                val symbolOptions = SymbolOptions()
                    .withLatLng(point)
                    .withIconImage("star.white")
                    .withIconSize(2.0f)
                    .withSymbolSortKey(10.0f)
                    .withDraggable(true)
                symbol = symbolManager!!.create(symbolOptions)

                true
            }

//            mapboxMap.addOnMapClickListener { point ->
//                Toast.makeText(this, String.format("User clicked at: %s", point.toString()), Toast.LENGTH_LONG).show()
//
//                true
//            }

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
}