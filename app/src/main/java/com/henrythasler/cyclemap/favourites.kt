package com.henrythasler.cyclemap

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.henrythasler.cyclemap.DataStoreUtils.getStringSet
import com.henrythasler.cyclemap.DataStoreUtils.setStringSet
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import com.mapbox.geojson.Point
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform

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

object DataStoreUtils {
    fun DataStore<Preferences>.getStringSet(key: String): Flow<Set<String>> =
        data.map { preferences ->
            preferences[stringSetPreferencesKey(key)] ?: emptySet()
        }

    suspend fun DataStore<Preferences>.setStringSet(key: String, value: Set<String>) {
        edit { preferences ->
            preferences[stringSetPreferencesKey(key)] = value
        }
    }
}

fun favouriteToString(favourite: Favourite): String {
    return Gson().toJson(favourite)
}

fun stringToFavourite(data: String): Favourite {
    return Gson().fromJson(data, Favourite::class.java)
}

suspend fun saveFavourites(datastore: DataStore<Preferences>, favourites: Set<Favourite>?) {
    val serialized = mutableSetOf<String>()
    favourites?.forEach { item ->
        serialized.add(favouriteToString(item))
    }
    Log.d(TAG, "saveFavourites: $serialized")
    datastore.setStringSet("favourites", serialized)
}

fun loadFavourites(datastore: DataStore<Preferences>): Flow<Set<Favourite>> {
    return datastore.getStringSet("favourites").transform { serialized ->
        val favourites: MutableSet<Favourite> = emptySet<Favourite>().toMutableSet()
        Log.d(TAG, "loadFavourites: $serialized")
        serialized.forEach {
            favourites += stringToFavourite(it)
        }
        if (favourites.size == 0) {
            favourites += Favourite("screenshot location", 10.897498, 48.279076, 14.87486)
        }
        emit(favourites)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavouritesSelectionSheet(
    favourites: Set<Favourite>?,
    onDismiss: () -> Unit,
    onRemove: (Favourite) -> Unit,
    onSelect: (Favourite) -> Unit,
) {
    ModalBottomSheet(
        modifier = Modifier
            .padding(12.dp),
        onDismissRequest = onDismiss,
//        sheetState = SheetState(true, LocalDensity.current)
    ) {
        val padding = 8.dp
        favourites?.forEach { item ->
            Row {
                Column(
                    modifier = Modifier
//                        .fillMaxWidth()
                        .clickable {
                            onSelect(item)
                        },
                ) {
                    Text(
                        modifier = Modifier
                            .padding(padding),
                        text = item.name
                    )
                    Text(
                        modifier = Modifier
                            .padding(padding),
                        text = getFormattedLocation(Point.fromLngLat(item.longitude, item.latitude))
                    )
                }
                Icon(
                    modifier = Modifier
                        .clickable { onRemove(item) },
                    painter = painterResource(id = R.drawable.baseline_delete_24),
                    contentDescription = stringResource(R.string.menu_fav_delete)
                )
            }
        }
    }
}