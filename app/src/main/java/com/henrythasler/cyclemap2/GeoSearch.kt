package com.henrythasler.cyclemap2

import android.app.Activity
import android.util.Log
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.easeTo
import com.mapbox.search.*
import com.mapbox.search.common.AsyncOperationTask
import com.mapbox.search.common.IsoCountryCode
import com.mapbox.search.common.IsoLanguageCode
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.turf.TurfConstants.UNIT_KILOMETERS
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfTransformation
import java.lang.ref.WeakReference

class GeoSearch(activity: WeakReference<Activity>, recyclerView: RecyclerView, map: MapboxMap) {
    private var searchEngine = SearchEngine.createSearchEngineWithBuiltInDataProviders(
        apiType = ApiType.GEOCODING,
        settings = SearchEngineSettings(activity.get()?.resources!!.getString(R.string.mapbox_access_token))
    )
    private lateinit var searchRequestTask: AsyncOperationTask
    private var geoSearchResultsAdapter: GeoSearchResultsAdapter =
        GeoSearchResultsAdapter { onSuggestionClick(it) }

    init {
        recyclerView.layoutManager = LinearLayoutManager(activity.get())
        recyclerView.adapter = geoSearchResultsAdapter
    }

    private var center: Point? = null

    private fun onSuggestionClick(suggestion: SearchSuggestion) {
        searchRequestTask = searchEngine.select(suggestion, searchCallback)
    }
    /*
        [SearchSuggestion(id='place.44140602', name='Landsberg am Lech', matchingName=null, descriptionText=null, address=SearchAddress(houseNumber=null, street=null, neighborhood=null, locality=null, postcode=null, place=null, district=null, region=Bavaria, country=Germany), requestOptions=RequestOptions(query='Landsberg', options=SearchOptions(proximity=Point{type=Point, bbox=null, coordinates=[11.130277633666994, 48.2444725036621]}, boundingBox=null, countries=null, fuzzyMatch=null, languages=[Language(code='en')], limit=10, types=null, requestDebounce=null, origin=null, navigationOptions=null, routeOptions=null, unsafeParameters=null, ignoreIndexableRecords=false, indexableRecordsDistanceThresholdMeters=null), proximityRewritten=false, originRewritten=false, endpoint='suggest', sessionID='a2dc179a-7e27-449a-a22d-6785e31e1513', requestContext=SearchRequestContext(apiType=GEOCODING, keyboardLocale=null, screenOrientation=PORTRAIT, responseUuid=)), distanceMeters=null, categories=null, makiIcon=null, etaMinutes=null, metadata=SearchResultMetadata(extraData={iso_3166_1=de, iso_3166_2=DE-BY}, reviewCount=null, phone=null, website=null, averageRating=null, description=null, primaryPhotos=null, otherPhotos=null, openHours=null, parking=null, cpsJson=null, countryIso1=de, countryIso2=DE-BY), externalIDs={}, isBatchResolveSupported=true, serverIndex=0, type=IndexableRecordItem(dataProviderName='com.mapbox.search.localProvider.history', type=PLACE)), SearchSuggestion(id='poi.1047972030365', name='Augsburger Allgemeine', matchingName=null, descriptionText=null, address=SearchAddress(houseNumber=null, street=Curt-Frenzel-Strasse 2, neighborhood=null, locality=Lechhausen-Ost, postcode=86167, place=Augsburg, district=null, region=Bavaria, country=Germany), requestOptions=RequestOptions(query='Landsberg', options=SearchOptions(proximity=Point{type=Point, bbox=null, coordinates=[11.130277633666994, 48.2444725036621]}, boundingBox=null, countries=null, fuzzyMatch=null, languages=[Language(code='en')], limit=10, types=null, requestDebounce=null, origin=null, navigationOptions=null, routeOptions=null, unsafeParameters=null, ignoreIndexableRecords=false, indexableRecordsDistanceThresholdMeters=null), proximityRewritten=false, originRewritten=false, endpoint='suggest', sessionID='a2dc179a-7e27-449a-a22d-6785e31e1513', requestContext=SearchRequestContext(apiType=GEOCODING, keyboardLocale=null, screenOrientation=PORTRAIT, responseUuid=)), distanceMeters=null, categories=[business, service, office], makiIcon=null, etaMinutes=null, metadata=SearchResultMetadata(extraData={iso_3166_1=de, iso_3166_2=DE-BY}, reviewCount=null, phone=null, website=null, averageRating=null, description=null, primaryPhotos=null, otherPhotos=null, openHours=null, parking=null, cpsJson=null, countryIso1=de, countryIso2=DE-BY), externalIDs={}, isBatchResolveSupported=true, serverIndex=1, type=SearchResultSuggestion(types=[POI])), SearchSuggestion(id='poi.635655230717', name='Landsberg Prison', matchingName=null, descriptionText=null, address=SearchAddress(houseNumber=null, street=Hindenburgring 12, neighborhood=null, locality=null, postcode=86899, place=Landsberg am Lech, district=null, region=Bavaria, country=Germany), requestOptions=RequestOptions(query='Landsberg', options=SearchOptions(proximity=Point{type=Point, bbox=null, coordinates=[11.130277633666994, 48.2444725036621]}, boundingBox=null, countries=null, fuzzyMatch=null, languages=[Language(code='en')], limit=10, types=null, requestDebounce=null, origin=null, navigationOptions=null, routeOptions=null, unsafeParameters=null, ignoreIndexableRecords=false, indexableRecordsDistanceThresholdMeters=null), proximityRewritten=false, originRewritten=false, endpoint='suggest', sessionID='a2dc179a-7e27-449a-a22d-6785e31e1513', requestContext=SearchRequestContext(apiType=GEOCODING, keyboardLocale=null, screenOrientation=PORTRAIT, responseUuid=)), distanceMeters=null, categories=[prison, jail], makiIcon=null, etaMinutes=null, metadata=SearchResultMetadata(extraData={iso_3
    */

    private val searchCallback = object : SearchSelectionCallback {
        override fun onSuggestions(
            suggestions: List<SearchSuggestion>,
            responseInfo: ResponseInfo
        ) {
            if (suggestions.isEmpty()) {
                Log.i(MainMapActivity.TAG, "No suggestions found")
            } else {
                Log.i(
                    MainMapActivity.TAG,
                    "Search suggestions: $suggestions.\nSelecting first suggestion..."
                )
                suggestions.forEach { suggestion ->
                    Log.i(MainMapActivity.TAG, suggestion.name)
                    geoSearchResultsAdapter.dataSet.add(suggestion)
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
            map.setCamera(CameraOptions.Builder().center(result.coordinate).build())
//            resultTextView?.text = "${result.name} ${TurfMeasurement.distance(result.coordinate, center!!, UNIT_KILOMETERS)}km"
        }

        override fun onResults(
            suggestion: SearchSuggestion,
            results: List<SearchResult>,
            responseInfo: ResponseInfo
        ) {
            TODO("Not yet implemented")
        }

        fun onCategoryResult(
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
                limit = 5,
                proximity = center,
                countries = listOf(
                    IsoCountryCode.GERMANY,
                    IsoCountryCode.AUSTRIA,
                    IsoCountryCode.SWITZERLAND
                ),
                languages = listOf(IsoLanguageCode.GERMAN)
            ),
            searchCallback
        )
    }


//    override fun onDestroy() {
//        searchRequestTask.cancel()
//        super.onDestroy()
//    }
}