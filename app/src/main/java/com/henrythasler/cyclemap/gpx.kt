package com.henrythasler.cyclemap

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import org.simpleframework.xml.*
import org.simpleframework.xml.core.Persister
import java.io.File

@Root(name = "gpx", strict = false)
class Gpx {
    @field:Element(name = "trk")
    var track: Track? = null

    constructor() // Default no-arg constructor
}

@Root(name = "trk", strict = false)
class Track {
    @field:ElementList(inline = true, name = "trkseg")
    var segments: List<Segment>? = null

    constructor() // Default no-arg constructor
}

@Root(name = "trkseg", strict = false)
class Segment {
    @field:ElementList(inline = true, name = "trkpt")
    var trackPoints: List<TrackPoint>? = null

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

@Composable
fun ReadSelectedGpx(uri: Uri, onLoaded: (Gpx) -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(uri) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val serializer = Persister()
            val gpx = serializer.read(Gpx::class.java, inputStream)
            onLoaded(gpx)
        }
    }
}