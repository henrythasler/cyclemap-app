package com.henrythasler.cyclemap

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.google.gson.Gson
import com.henrythasler.cyclemap.DataStoreUtils.getStringSet
import com.henrythasler.cyclemap.DataStoreUtils.setStringSet
import com.henrythasler.cyclemap.MainActivity.Companion.TAG
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import java.io.ByteArrayOutputStream

data class Favourite(
    val name: String,
    val description: String,
    val longitude: Double,
    val latitude: Double,
    val zoom: Double,
    var imageBase64: String? = null,
    @Transient var image: ImageBitmap? = null,
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
        if(item.imageBase64 == null) {
            item.image?.let { image ->
                val byteArrayOutputStream = ByteArrayOutputStream()
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                item.imageBase64 = Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            }
        }
        serialized.add(favouriteToString(item))
    }
    Log.d(TAG, "saveFavourites: $serialized")
    datastore.setStringSet("favourites", serialized)
}

fun loadFavourites(datastore: DataStore<Preferences>): Flow<Set<Favourite>> {
    return datastore.getStringSet("favourites").transform { serialized ->
        val favourites: MutableSet<Favourite> = emptySet<Favourite>().toMutableSet()
        Log.d(TAG, "loadFavourites: $serialized")
        serialized.forEach { item ->
            val newItem = stringToFavourite(item)
            newItem.imageBase64?.let { imageBase64 ->
                val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                newItem.image = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size).asImageBitmap()
            }
            favourites += newItem
        }
        if (favourites.size == 0) {
            favourites += Favourite(
                "screenshot location",
                getFormattedDateTime(),
                10.897498,
                48.279076,
                14.87486
            )
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
        scrimColor = Color.Transparent,
    ) {
        val padding = 8.dp
        favourites?.forEach { item ->
            Row (
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            onSelect(item)
                        },
                ) {
                    item.image?.let { image ->
                        Image(
                            modifier = Modifier
                                .width(100.dp)
                                .height(100.dp)
                                .padding(padding),
                            bitmap = image,
                            contentDescription = ""
                        )
                    }
                    Column {
                        Text(text = item.name)
                        Text(text = item.description)
                    }
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