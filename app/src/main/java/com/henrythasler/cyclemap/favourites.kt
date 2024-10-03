package com.henrythasler.cyclemap
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class Favourite(
    @SerializedName("name")
    val name: String,
    @SerializedName("longitude")
    val longitude: Double,
    @SerializedName("latitude")
    val latitude: Double,
    @SerializedName("zoom")
    val zoom: Double
    )

fun favouriteToString(favourite: Favourite): String {
    return Gson().toJson(favourite)
}

fun stringToFavourite(data: String): Favourite {
    return Gson().fromJson(data, Favourite::class.java)
}