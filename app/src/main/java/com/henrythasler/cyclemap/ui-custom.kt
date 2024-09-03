package com.henrythasler.cyclemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

@Composable
fun SpeedDisplay(currentSpeed: Double) {
    val radius = 8.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 36.dp),
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