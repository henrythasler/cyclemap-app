package com.henrythasler.cyclemap2

import android.app.Activity
import android.util.Log
import com.mapbox.search.*
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import java.lang.ref.WeakReference

class GeoSearch(activity: WeakReference<Activity>) {
    private var searchEngine: SearchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
        SearchEngineSettings(activity.get()?.resources!!.getString(R.string.mapbox_access_token))
    )
    private lateinit var searchRequestTask: AsyncOperationTask

    private val searchCallback = object : SearchSelectionCallback {
        override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
            if (suggestions.isEmpty()) {
                Log.i(MainMapActivity.TAG, "No suggestions found")
            } else {
                Log.i(MainMapActivity.TAG, "Search suggestions: $suggestions.\nSelecting first suggestion...")
                searchRequestTask = searchEngine.select(suggestions.first(), this)
            }
        }

        override fun onResult(
            suggestion: SearchSuggestion,
            result: SearchResult,
            responseInfo: ResponseInfo
        ) {
            Log.i(MainMapActivity.TAG, "Search result: $result")
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

    fun search(query: String) {
        searchRequestTask = searchEngine.search(
            query,
            SearchOptions(limit = 5),
            searchCallback
        )
    }


//    override fun onDestroy() {
//        searchRequestTask.cancel()
//        super.onDestroy()
//    }
}