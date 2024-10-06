package com.henrythasler.cyclemap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
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
        if (item.imageBase64 == null) {
            item.image?.let { image ->
                val byteArrayOutputStream = ByteArrayOutputStream()
                image.asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
                item.imageBase64 =
                    Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            }
        }
        serialized.add(favouriteToString(item))
    }
    Log.d(TAG, "saveFavourites: $serialized")
    datastore.setStringSet("favourites", serialized)
}

fun loadAsImageBitmap(context: Context, id: Int, tint: Color = Color.Black): ImageBitmap? {
    val drawable =
        ContextCompat.getDrawable(context, id)
    drawable?.let {
        // Ensure the drawable is mutable
        val wrappedDrawable = DrawableCompat.wrap(it).mutate()
        // Create a bitmap with the same dimensions as the drawable
        val bitmap = Bitmap.createBitmap(
            wrappedDrawable.intrinsicWidth,
            wrappedDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        // Create a canvas to draw the drawable onto the bitmap
        val canvas = Canvas(bitmap)
        // Set the bounds of the drawable
        wrappedDrawable.setBounds(0, 0, canvas.width, canvas.height)
        // To change the color
        DrawableCompat.setTint(wrappedDrawable, tint.toArgb())
        // Draw the drawable onto the canvas
        wrappedDrawable.draw(canvas)
        // Convert the bitmap to an ImageBitmap and store
        Log.d(TAG, "loadAsImageBitmap: ${bitmap.width}x${bitmap.height}")
        return bitmap.asImageBitmap()
    }
    return null
}

/**
 * Load favourite locations from datastore
 * @param context Context-Object for the Application to load resources from
 * @param datastore DataStore object to load the Favourites from
 * @return a Set of Favourites wrapped in a Flow
 */
fun loadFavourites(context: Context, datastore: DataStore<Preferences>): Flow<Set<Favourite>> {
    return datastore.getStringSet("favourites").transform { serialized ->
        val favourites: MutableSet<Favourite> = emptySet<Favourite>().toMutableSet()
        Log.d(TAG, "loadFavourites: $serialized")
        serialized.forEach { item ->
            val newItem = stringToFavourite(item)

            // check if a serialized image exists and deserialize for further use
            newItem.imageBase64?.let { imageBase64 ->
                val decodedBytes = Base64.decode(imageBase64, Base64.DEFAULT)
                newItem.image = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                    .asImageBitmap()
            } ?: run {
                //  otherwise use a placeholder image from the resources
                newItem.image = loadAsImageBitmap(
                    context,
                    R.drawable.baseline_image_not_supported_100,
                    Color.Gray
                )
            }
            favourites += newItem
        }
        // insert one default location if the Set is empty
        if (favourites.size == 0) {
            Log.i(TAG, "adding default Favourite")
            favourites += Favourite(
                "screenshot location",
                getFormattedDateTime(),
                10.897498,
                48.279076,
                14.87486,
                image = loadAsImageBitmap(
                    context,
                    R.drawable.baseline_image_not_supported_100,
                    Color.Gray
                )
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
        LazyColumn {
            favourites?.forEach { favourite ->
                item {
                    Row(
//                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(padding, 0.dp, padding, padding)
                                .clickable {
                                    onSelect(favourite)
                                },
//                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                modifier = Modifier
                                    .width(100.dp)
                                    .height(100.dp)
                                    .border(Dp.Hairline, Color.Black),
                                bitmap = favourite.image ?: ImageBitmap(1, 1),
                                contentDescription = null
                            )
                            Column(
                                modifier = Modifier
                                    .padding(padding)
                            ) {
                                Text(fontWeight = FontWeight.Bold, text = favourite.name)
                                Text(text = favourite.description)
                            }
                        }
                        Icon(
                            modifier = Modifier
                                .clickable { onRemove(favourite) },
                            painter = painterResource(id = R.drawable.baseline_delete_24),
                            contentDescription = stringResource(R.string.menu_fav_delete)
                        )
                    }
                }
            }
        }
    }
}