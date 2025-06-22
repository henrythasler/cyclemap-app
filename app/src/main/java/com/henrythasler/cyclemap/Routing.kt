package com.henrythasler.cyclemap

import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import retrofit2.Callback
import retrofit2.Response

fun getRoute(
    mapboxAccessToken: String,
    waypoints:  List<Point>,
    onSuccess: (DirectionsResponse) -> Unit,
    onError: (Throwable) -> Unit
) {
    val routeOptions = RouteOptions.builder()
        .coordinatesList(waypoints)
        .overview(DirectionsCriteria.OVERVIEW_SIMPLIFIED)
        .profile(DirectionsCriteria.PROFILE_CYCLING)
        .steps(false)
        .alternatives(false)
        .language("en")
        .build()

    val client = MapboxDirections.builder()
        .routeOptions(routeOptions)
        .accessToken(mapboxAccessToken)
        .build()

    client.enqueueCall(object : Callback<DirectionsResponse> {
        override fun onResponse(
            call: retrofit2.Call<DirectionsResponse>,
            response: Response<DirectionsResponse>
        ) {
            response.body()?.let { directionsResponse ->
                if (directionsResponse.routes().isNotEmpty()) {
                    onSuccess(directionsResponse)
                } else {
                    onError(Exception("No routes found"))
                }
            } ?: onError(Exception("Empty response"))
        }

        override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
            onError(t)
        }
    })
}