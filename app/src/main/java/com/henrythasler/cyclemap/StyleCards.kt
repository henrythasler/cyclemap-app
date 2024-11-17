package com.henrythasler.cyclemap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        scrimColor = Color.Transparent,
    ) {
        val padding = 8.dp
        val context = LocalContext.current
        for (styleDefinition in styleDefinitions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (styleDefinition.styleId == currentStyle) Color.LightGray else Color.Transparent)
                    .clickable {
                        onSelect(styleDefinition)
                    },
//                verticalAlignment = Alignment.CenterVertically,
            ) {
                val resourceId = context.resources.getIdentifier(styleDefinition.drawable, "drawable", context.packageName)
                Image(
                    modifier = Modifier
                        .width(100.dp)
                        .height(100.dp)
                        .padding(padding)
                        .clip(RoundedCornerShape(padding))
                        .border(1.dp, Color.Black, shape = RoundedCornerShape(padding)),
                    painter = painterResource(id = resourceId),
                    contentDescription = styleDefinition.styleName
                )
                Column(
                    modifier = Modifier
                        .padding(padding)
                ) {
                    Text(fontWeight = FontWeight.Bold, text = styleDefinition.styleName)
                    Text(text = styleDefinition.styleDescription ?: "")
                }
            }
        }
    }
}
