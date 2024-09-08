package com.henrythasler.cyclemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import java.text.DecimalFormat

@Composable
fun SpeedDisplay(currentSpeed: Double, padding: PaddingValues) {
    val radius = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(padding),
            shape = RoundedCornerShape(radius),
            color = colorResource(R.color.distanceMeasurementBadgeBackground),
        ) {
            Text(
                text = "${DecimalFormat("0.0").format(currentSpeed)} km/h",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(
                    start = radius,
                    end = radius,
                    top = radius / 2,
                    bottom = radius / 2
                )
            )
        }
    }
}

@Composable
fun DistanceBadge(distance: Double) {
    Text(
        modifier = Modifier.padding(3.dp),
        fontSize = 18.sp,
        fontStyle = FontStyle.Italic,
        text = getFormattedDistance(distance)
    )
}

@Composable
fun MainMenu(
    showMainMenu: Boolean,
    onDismissRequest: () -> Unit,
    onSelectMapStyle: () -> Unit,
    onLoadGpx: () -> Unit,
    onSaveGpx: () -> Unit,
    onDeleteTrack: () -> Unit,
    onAbout: () -> Unit,
    onScreenshot: () -> Unit,
) {
    DropdownMenu(
        expanded = showMainMenu,
        offset = DpOffset(48.dp, 0.dp), // FIXME: avoid hard-coded values
        onDismissRequest = onDismissRequest,
    ) {
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
        DropdownMenuItem(
            text = {
                Text(text = stringResource(R.string.menu_screenshot))
            },
            onClick = onScreenshot,
            leadingIcon = {
                Icon(
                    painterResource(id = R.drawable.baseline_my_location_24),
                    stringResource(R.string.menu_screenshot)
                )
            }
        )
    }
}