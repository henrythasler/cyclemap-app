package com.henrythasler.cyclemap

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.core.Persister
import java.io.File

@Root(name = "gpx", strict = false)
class Gpx {
    @field:Element(name = "trk", required = false)
    var track: Track? = null

    @field:Element(name = "rte", required = false)
    var route: Route? = null

    constructor() // Default no-arg constructor
}

@Root(name = "trk", strict = false)
class Track {
    @field:ElementList(inline = true, name = "trkseg")
    var segments: List<Segment>? = null

    constructor() // Default no-arg constructor
}

@Root(name = "rte", strict = false)
class Route {
    @field:ElementList(inline = true, name = "rtept")
    var routePoints: List<RoutePoint>? = null

    constructor() // Default no-arg constructor
}

@Root(name = "trkseg", strict = false)
class Segment {
    @field:ElementList(inline = true, name = "trkpt")
    var trackPoints: List<TrackPoint>? = null

    constructor() // Default no-arg constructor
}

@Root(name = "rtept", strict = false)
class RoutePoint {
    @field:Attribute(name = "lat")
    var latitude: Double = 0.0

    @field:Attribute(name = "lon")
    var longitude: Double = 0.0

    constructor() // Default no-arg constructor
}

@Root(name = "trkpt", strict = false)
class TrackPoint {
    @field:Attribute(name = "lat")
    var latitude: Double = 0.0

    @field:Attribute(name = "lon")
    var longitude: Double = 0.0

    @field:Element(name = "ele", required = false)
    var elevation: Double? = null

    @field:Element(name = "time", required = false)
    var time: String? = null

    constructor() // Default no-arg constructor
}

fun readGpxFile(filePath: String): Gpx {
    val serializer = Persister()
    val file = File(filePath)
    return serializer.read(Gpx::class.java, file)
}

fun writeGpxFile(gpx: Gpx, filePath: String) {
    val serializer = Persister()
    val file = File(filePath)
    serializer.write(gpx, file)
}

/**
 * write a GPX trackSegment in a coroutine
 */
suspend fun writeGpx(context: Context, trackSegment: MutableList<TrackPoint>, uri: Uri): Boolean {
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                val gpx = Gpx().apply {
                    this.track = Track().apply {
                        this.segments = listOf(
                            Segment().apply {
                                this.trackPoints = trackSegment
                            }
                        )
                    }
                }

                val serializer = Persister()
                serializer.write(gpx, outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving GPX file $uri: $e")
            return@withContext false
        }
    }
    return true
}