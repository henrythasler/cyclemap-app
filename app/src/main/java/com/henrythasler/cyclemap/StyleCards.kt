package com.henrythasler.cyclemap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StyleCard(padding: Dp, style: StyleDefinition, onSelect: (Boolean) -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .padding(padding)
            .fillMaxWidth()
            .clickable {
                onSelect(true);
            }
    ) {
        Row {
//            Image(
//                modifier = Modifier,
//                painter = painterResource(id = R.drawable.style_cyclemap),
//                contentDescription = "",
//            )
            Column {
                Text(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    text = style.styleName
                )
                style.styleUrl?.let {
                    Text(
                        fontStyle = FontStyle.Italic,
                        color = Color.Gray,
                        text = it
                    )
                }
                style.styleId?.let {
                    Text(
                        fontStyle = FontStyle.Italic,
                        color = Color.Gray,
                        text = it
                    )
                }
            }
        }
    }
}

@Composable
fun StyleCards(styleDefinitions: List<StyleDefinition>, onSelect: (Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(60.dp, 60.dp, 20.dp, 20.dp)
    ) {
        styleDefinitions.forEach {
            item {
                StyleCard(4.dp, it, onSelect)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleSelectionSheet(
    styleDefinitions: List<StyleDefinition>,
    onDismiss: () -> Unit,
    onSelect: (StyleDefinition) -> Unit
) {
    ModalBottomSheet(
        modifier = Modifier
            .padding(12.dp),
        onDismissRequest = onDismiss,
    ) {
        for (styleDefinition in styleDefinitions) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                ,
                onClick = {
                    onSelect(styleDefinition)
                }
            ) {
                Text(
                    text = styleDefinition.styleName
                )
            }
        }
    }
}
