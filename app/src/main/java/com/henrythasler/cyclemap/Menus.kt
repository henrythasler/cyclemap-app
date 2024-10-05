package com.henrythasler.cyclemap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.mapbox.geojson.Point
import com.mapbox.maps.ScreenCoordinate

@Composable
fun MainMenu(
    onDismissRequest: () -> Unit,
    onFavourites: () -> Unit,
    onSelectMapStyle: () -> Unit,
    onLoadGpx: () -> Unit,
    onClearRoute: () -> Unit,
    onSaveGpx: () -> Unit,
    onDeleteTrack: () -> Unit,
    onAbout: () -> Unit,
) {
    val radius = 6.dp
    Popup(
        properties = PopupProperties(focusable = true),
        onDismissRequest = onDismissRequest,
    ) {
        Surface(
            shape = RoundedCornerShape(radius),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                // FIXME: find a better way to limit the width
                modifier = Modifier.width(200.dp),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_favourites))
                    },
                    onClick = onFavourites,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_star_24),
                            stringResource(R.string.menu_favourites)
                        )
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_map_style))
                    },
                    onClick = onSelectMapStyle,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_map_24),
                            stringResource(R.string.menu_map_style)
                        )
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_gpx_load))
                    },
                    onClick = onLoadGpx,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_directions_24),
                            stringResource(R.string.menu_gpx_load)
                        )
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_delete_route))
                    },
                    onClick = onClearRoute,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_directions_off_24),
                            stringResource(R.string.menu_delete_route)
                        )
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_gpx_save))
                    },
                    onClick = onSaveGpx,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_save_alt_24),
                            stringResource(R.string.menu_gpx_save)
                        )
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_delete_track))
                    },
                    onClick = onDeleteTrack,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_delete_forever_24),
                            stringResource(R.string.menu_delete_track)
                        )
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_about))
                    },
                    onClick = onAbout,
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_info_24),
                            stringResource(R.string.menu_about)
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun LocationContextMenu(
    header: String,
    point: Point?,
    screenCoordinate: ScreenCoordinate?,
    onBookmarkLocation: (Point) -> Unit,
    onShareLocation: (Point, Int) -> Unit,
    onLocationDetails: (Point) -> Unit,
    onDismiss: () -> Unit
) {
    val radius = 8.dp
    val icon = painterResource(id = R.drawable.baseline_my_location_24)
    Icon(
        modifier = Modifier
            .offset {
                screenCoordinate?.let {
                    IntOffset(
                        (it.x - icon.intrinsicSize.width / 2).toInt(),
                        (it.y - icon.intrinsicSize.height / 2).toInt()
                    )
                } ?: IntOffset(0, 0)
            },
        painter = icon,
        contentDescription = stringResource(R.string.menu_map_context)
    )

    Popup(
        properties = PopupProperties(focusable = true),
        offset = screenCoordinate?.let {
            IntOffset(
                (it.x + icon.intrinsicSize.width / 2).toInt(),
                (it.y + icon.intrinsicSize.height / 2).toInt()
            )
        } ?: IntOffset(0, 0),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(radius),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.width(160.dp),
            ) {
                Text(
                    text = header,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = radius,
                        end = radius,
                        top = radius / 2,
                        bottom = radius / 2
                    )

                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_map_context_favourites))
                    },
                    onClick = { point?.let { onBookmarkLocation(it) } },
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_star_border_24),
                            stringResource(R.string.menu_map_context_favourites)
                        )
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(text = stringResource(R.string.menu_map_context_share))
                    },
                    onClick = { point?.let { onShareLocation(it, R.string.share_position_link_cyclemap) } },
                    leadingIcon = {
                        Icon(
                            painterResource(id = R.drawable.baseline_share_24),
                            stringResource(R.string.menu_map_context_share)
                        )
                    }
                )
//                DropdownMenuItem(
//                    text = {
//                        Text(text = stringResource(R.string.menu_map_context_share_google))
//                    },
//                    onClick = { onShareLocation(point, R.string.share_position_link_google) },
//                    leadingIcon = {
//                        Icon(
//                            painterResource(id = R.drawable.baseline_share_24),
//                            stringResource(R.string.menu_map_context_share_google)
//                        )
//                    }
//                )
//                DropdownMenuItem(
//                    text = {
//                        Text(text = stringResource(R.string.menu_map_context_details))
//                    },
//                    onClick = { onLocationDetails(point) },
//                    leadingIcon = {
//                        Icon(
//                            painterResource(id = R.drawable.baseline_data_object_24),
//                            stringResource(R.string.menu_map_context_share)
//                        )
//                    }
//                )
            }
        }
    }
}

