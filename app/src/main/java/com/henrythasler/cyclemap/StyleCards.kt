package com.henrythasler.cyclemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun StyleCard(padding: Dp) {
    OutlinedCard(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .clickable {
                
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = Modifier,
                painter = painterResource(id = R.drawable.style_cyclemap),
                contentDescription = "",
            )
            Spacer(Modifier.size(padding))
            Text(text = "CycleMap")
        }
    }
}

@Composable
fun StyleCards() {
    val padding = 24.dp;
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
    ) {
        item {
            StyleCard(padding)
        }
        item {
            StyleCard(padding)
        }
    }
}