package com.henrythasler.cyclemap

import android.content.Intent
import android.net.Uri
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.search.ui.view.place.SearchPlace
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

object IntentUtils {

    private val decimalFormatSymbols = DecimalFormatSymbols()

    fun geoIntent(point: Point): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${point.latitude()}, ${point.longitude()}"))
    }

    fun shareIntent(searchPlace: SearchPlace, zoom: Number=14): Intent {
        decimalFormatSymbols.decimalSeparator = '.'
        val decimalFormat =  DecimalFormat("#.####", decimalFormatSymbols)

        val text = "https://cyclemap.link/#${decimalFormat.format(zoom)}/${decimalFormat.format(searchPlace.coordinate.latitude())}/${decimalFormat.format(searchPlace.coordinate.longitude())}"

        return Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    fun shareIntent(point: LatLng, zoom: Number=14): Intent {
        decimalFormatSymbols.decimalSeparator = '.'
        val decimalFormat =  DecimalFormat("#.####", decimalFormatSymbols)

        val text = "https://cyclemap.link/#${decimalFormat.format(zoom)}/${decimalFormat.format(point.latitude)}/${decimalFormat.format(point.longitude)}"

        return Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }
}
