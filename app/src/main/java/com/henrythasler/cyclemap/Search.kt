package com.henrythasler.cyclemap

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.geojson.Point
import com.mapbox.search.ResponseInfo
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.result.SearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getFormattedName(result: SearchResult): String {
    return result.name
}

@Composable
fun ReverseGeocodingExample(
    searchEngine: SearchEngine,
    point: Point,
) {
    var address by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<SearchResult?>(null) }

    LaunchedEffect(point) {
        withContext(Dispatchers.IO) {
            val options = ReverseGeoOptions.Builder(point)
                .limit(1)
                .build()

            searchEngine.search(
                options,
                object : SearchCallback {
                    override fun onResults(
                        results: List<SearchResult>,
                        responseInfo: ResponseInfo
                    ) {
                        if (results.isNotEmpty()) {
                            result = results[0]
//                            address = result.address?.formattedAddress()
                        } else {
                            error = "No results found"
                        }
                    }

                    override fun onError(e: Exception) {
                        error = e.message ?: "An error occurred"
                    }
                }
            )
        }
    }

    // Display the result
    if (result != null) {
        Text("${result?.name}")
        Text("${result?.descriptionText}")
        Text("${result?.address?.street} ${result?.address?.houseNumber}")
        Text("${result?.address?.postcode} ${result?.address?.place}")
        Text("${result?.address?.country}")
    } else if (error != null) {
        Text("Error: $error")
    } else {
        Text("Searching...")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoSearchSheet(
    searchEngine: SearchEngine,
    clickedPoint: Point,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier
            .padding(12.dp),
        onDismissRequest = onDismiss,
        scrimColor = Color.Transparent,
    ) {
        Text("search results:")
        Column(
            modifier = Modifier.height(300.dp)
        ) {
            ReverseGeocodingExample(searchEngine, clickedPoint)

        }
    }
}

/**
 * takes a geo-location (Point) and returns a name and description for that location
 * @param point geolocation
 * @param searchEngine search engine to handle the reverse geocoding
 * @param coroutineScope coroutine to handle the asynchronous task
 * @param onComplete is called when the asynchronous task is finished
 * @return a (name, description) tuple that is build from the response
 */
fun lookupGeolocation(
    point: Point,
    searchEngine: SearchEngine,
    coroutineScope: CoroutineScope,
    onComplete: (name: String, description: String) -> Unit
) {
    coroutineScope.launch(Dispatchers.IO) {
        searchEngine.search(
            ReverseGeoOptions.Builder(point).limit(1).build(),
            object : SearchCallback {
                var name = getFormattedLocation(point, 3)
                var description = getFormattedDateTime()

                override fun onResults(
                    results: List<SearchResult>,
                    responseInfo: ResponseInfo
                ) {
                    if (results.isEmpty()) {
                        Log.e(TAG, "No results found")
                    } else {
                        Log.d(TAG, results[0].address.toString())
                        name = getFormattedName(results[0])
                        description =
                            "${results[0].address?.place}, ${results[0].address?.region}"
                    }
                    onComplete(name, description)
                }

                override fun onError(e: Exception) {
                    Log.e(TAG, e.message.toString())
                    onComplete(name, description)
                }
            }
        )
    }
}