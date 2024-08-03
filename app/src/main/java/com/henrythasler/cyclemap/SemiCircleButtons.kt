package com.henrythasler.cyclemap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SemiCircleButtons(
    buttons: List<String>,
    radius: Float,
    startAngle: Float = -90f,
    endAngle: Float = 90f
) {
    Box(
//        modifier = Modifier.fillMaxSize()
    ) {
        buttons.forEachIndexed { index, text ->
            val angle = startAngle + (endAngle - startAngle) * index / (buttons.size - 1)
            val x = cos(Math.toRadians(angle.toDouble())).toFloat() * radius
            val y = sin(Math.toRadians(angle.toDouble())).toFloat() * radius

            SmallFloatingActionButton(
                onClick = { /* Handle button click */ },
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x.dp, y.dp)
//                    .rotate(angle + 90f)
            ) {
                Icon(Icons.Filled.AccountCircle, "Small floating action button.")
            }
        }
    }
}