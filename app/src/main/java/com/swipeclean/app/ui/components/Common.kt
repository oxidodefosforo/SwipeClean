package com.swipeclean.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swipeclean.app.data.MediaItem
import com.swipeclean.app.ui.theme.Surface2
import com.swipeclean.app.ui.theme.TextDim

/** Botón circular de acción (DELETE / UNDO / SKIP / KEEP). */
@Composable
fun RoundAction(
    color: Color,
    label: String,
    size: Dp,
    enabled: Boolean = true,
    filled: Boolean = false,
    icon: ImageVector? = null,
    glyph: String? = null,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .alpha(if (enabled) 1f else 0.35f)
                .clip(CircleShape)
                .background(if (filled) color.copy(alpha = 0.16f) else Surface2)
                .border(BorderStroke(1.5.dp, color.copy(alpha = if (filled) 0.9f else 0.35f)), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(size * 0.42f))
            } else if (glyph != null) {
                Text(glyph, color = color, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            label,
            color = TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun StatPill(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = TextDim, fontSize = 11.sp)
    }
}

/** Selector desplegable sencillo (sin APIs experimentales). */
@Composable
fun <T> SelectField(
    label: String,
    valueText: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = TextDim, fontSize = 12.sp)
                Text(valueText, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextDim)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        open = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

/** Grupo de opciones tipo "segmented". */
@Composable
fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, text) ->
            val isSel = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    fontSize = 13.sp,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Miniatura cuadrada seleccionable. */
@Composable
fun Thumb(
    item: MediaItem,
    selected: Boolean,
    selectedColor: Color,
    unselectedLabel: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).size(320).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (!selected) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
                contentAlignment = Alignment.Center,
            ) {
                if (unselectedLabel != null) {
                    Text(unselectedLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(selectedColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))
