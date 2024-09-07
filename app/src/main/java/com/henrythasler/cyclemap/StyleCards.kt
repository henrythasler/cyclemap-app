package com.henrythasler.cyclemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSelectionSheet(
    currentStyle: String,
    styleDefinitions: List<StyleDefinition>,
    onDismiss: () -> Unit,
    onSelect: (StyleDefinition) -> Unit
) {
    ModalBottomSheet(
        modifier = Modifier
            .padding(12.dp),
        onDismissRequest = onDismiss,
    ) {
        val padding = 8.dp
        val context = LocalContext.current
        for (styleDefinition in styleDefinitions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(styleDefinition)
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val resourceId = context.resources.getIdentifier(styleDefinition.drawable, "drawable", context.packageName)
                Image(
                    modifier = Modifier
                        .padding(padding),
                    painter = painterResource(id = resourceId),
                    contentDescription = styleDefinition.styleName
                )
                Text(
                    modifier = Modifier
                        .padding(padding),
//                    fontSize = 24.sp,
                    fontWeight = if (styleDefinition.styleId == currentStyle) FontWeight.Bold else FontWeight.Normal,
                    text = styleDefinition.styleName

                )
            }
        }
    }
}
