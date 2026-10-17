package com.swipeclean.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.swipeclean.app.data.Decision
import com.swipeclean.app.ui.MainViewModel
import com.swipeclean.app.ui.components.BackCard
import com.swipeclean.app.ui.components.RoundAction
import com.swipeclean.app.ui.components.StatPill
import com.swipeclean.app.ui.components.SwipeCard
import com.swipeclean.app.ui.components.SwipeState
import com.swipeclean.app.ui.theme.DeleteRed
import com.swipeclean.app.ui.theme.KeepGreen
import com.swipeclean.app.ui.theme.SkipBlue
import com.swipeclean.app.ui.theme.Surface2
import com.swipeclean.app.ui.theme.TextDim
import kotlinx.coroutines.launch

private const val PRELOAD_AHEAD = 4

@Composable
fun SwipeScreen(vm: MainViewModel) {
    BackHandler { vm.backToSetup() }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf(IntSize.Zero) }

    val current = vm.queue.firstOrNull()
    val next = vm.queue.getOrNull(1)
    val state = remember(vm.cardVersion) { SwipeState.fromUndo(vm.takeUndoFrom(), area) }

    // Al deshacer, la tarjeta vuelve animada a su sitio.
    LaunchedEffect(state) {
        if (state.offsetX.value != 0f || state.offsetY.value != 0f) state.reset()
    }

    // Precarga de las siguientes fotos al tamaño exacto de la tarjeta (caché en memoria de Coil).
    LaunchedEffect(current?.id, area) {
        if (area.width == 0 || area.height == 0) return@LaunchedEffect
        val loader = context.imageLoader
        vm.queue.drop(1).take(PRELOAD_AHEAD).forEach { item ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .size(area.width, area.height)
                    .build()
            )
        }
    }

    fun commit(d: Decision) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.decide(d)
    }

    fun act(d: Decision) {
        if (current == null || state.busy || area.width == 0) return
        scope.launch {
            state.flingOut(d, area.width.toFloat(), area.height.toFloat())
            commit(d)
        }
    }

    val stats = vm.stats
    val progress = if (stats.total > 0) stats.reviewed.toFloat() / stats.total else 0f

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ---------- Cabecera ----------
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { vm.backToSetup() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("SwipeClean", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "Revisar (${vm.pending.count})",
                color = if (vm.pending.count > 0) DeleteRed else TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Surface2)
                    .clickable { vm.openReview() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatPill("Revisadas", "${stats.reviewed}/${stats.total}", modifier = Modifier.weight(1f))
            StatPill("Conservadas", "${stats.kept}", KeepGreen, Modifier.weight(1f))
            StatPill("A eliminar", "${vm.pending.count}", DeleteRed, Modifier.weight(1f))
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(4.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Surface2,
        )

        // ---------- Tarjetas ----------
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 14.dp)
                .onSizeChanged { area = it },
            contentAlignment = Alignment.Center,
        ) {
            if (current == null) {
                Text("No hay más fotos en esta sesión", color = TextDim)
            } else {
                if (next != null) {
                    key(next.id) { BackCard(next, state, area) }
                }
                key(vm.cardVersion) {
                    SwipeCard(current, state, area, onDecision = { commit(it) })
                }
            }
        }

        // ---------- Botones ----------
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundAction(DeleteRed, "DELETE", 72.dp, filled = true, icon = Icons.Filled.Close) { act(Decision.DELETE) }
            RoundAction(TextDim, "UNDO", 52.dp, enabled = vm.canUndo, glyph = "↶") {
                if (!state.busy) vm.undo()
            }
            RoundAction(SkipBlue, "SKIP", 52.dp, icon = Icons.Filled.KeyboardArrowUp) { act(Decision.SKIP) }
            RoundAction(KeepGreen, "KEEP", 72.dp, filled = true, icon = Icons.Filled.Check) { act(Decision.KEEP) }
        }
        Spacer(Modifier.height(4.dp))
    }
}
