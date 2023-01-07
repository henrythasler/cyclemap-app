package com.henrythasler.cyclemap2

import android.app.Activity
import android.util.Log
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import com.mapbox.search.*
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.turf.TurfConstants.UNIT_KILOMETERS
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation
import java.lang.ref.WeakReference

class GeoSearch(activity: WeakReference<Activity>, recyclerView: RecyclerView) {
    private var searchEngine: SearchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
        SearchEngineSettings(activity.get()?.resources!!.getString(R.string.mapbox_access_token))
    )
    private lateinit var searchRequestTask: AsyncOperationTask
    private var geoSearchResultsAdapter: GeoSearchResultsAdapter =
        GeoSearchResultsAdapter { Log.i(MainMapActivity.TAG, it) }

    init {
        recyclerView.layoutManager = LinearLayoutManager(activity.get())
        recyclerView.adapter = geoSearchResultsAdapter
    }

    private var center: Point? = null


    private val searchCallback = object : SearchSelectionCallback {
        override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
            if (suggestions.isEmpty()) {
                Log.i(MainMapActivity.TAG, "No suggestions found")
            } else {
                Log.i(MainMapActivity.TAG, "Search suggestions: $suggestions.\nSelecting first suggestion...")
                suggestions.forEach { suggestion ->
                    Log.i(MainMapActivity.TAG, suggestion.name)
                    geoSearchResultsAdapter.dataSet.add(suggestion.name)
                }
                geoSearchResultsAdapter.notifyDataSetChanged()

//                searchRequestTask = searchEngine.select(suggestions.first(), this)
            }
        }

        override fun onResult(
            suggestion: SearchSuggestion,
            result: SearchResult,
            responseInfo: ResponseInfo
        ) {
            Log.i(MainMapActivity.TAG, "Search result: $result")
//            resultTextView?.text = "${result.name} ${TurfMeasurement.distance(result.coordinate, center!!, UNIT_KILOMETERS)}km"
        }

        override fun onCategoryResult(
            suggestion: SearchSuggestion,
            results: List<SearchResult>,
            responseInfo: ResponseInfo
        ) {
            Log.i(MainMapActivity.TAG, "Category search results: $results")
        }

        override fun onError(e: Exception) {
            Log.i(MainMapActivity.TAG, "Search error", e)
        }
    }

    fun search(query: String, center: Point) {
        this.center = center
        geoSearchResultsAdapter.dataSet.clear()

        searchRequestTask = searchEngine.search(
            query,
            SearchOptions(
                limit = 10,
                proximity = center,
            ),
            searchCallback
        )
    }


//    override fun onDestroy() {
//        searchRequestTask.cancel()
//        super.onDestroy()
//    }
}