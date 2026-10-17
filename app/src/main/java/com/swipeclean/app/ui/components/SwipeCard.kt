package com.swipeclean.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swipeclean.app.data.Decision
import com.swipeclean.app.data.MediaItem
import com.swipeclean.app.ui.formatBytes
import com.swipeclean.app.ui.formatDate
import com.swipeclean.app.ui.theme.DeleteRed
import com.swipeclean.app.ui.theme.KeepGreen
import com.swipeclean.app.ui.theme.SkipBlue
import com.swipeclean.app.ui.theme.Surface1
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SWIPE_X = 0.25f   // fracción del ancho para confirmar KEEP/DELETE
private const val SWIPE_Y = 0.16f   // fracción del alto para confirmar SKIP

/** Estado de la tarjeta superior: desplazamiento animable compartido por gestos y botones. */
@Stable
class SwipeState(initialX: Float = 0f, initialY: Float = 0f) {
    val offsetX = Animatable(initialX)
    val offsetY = Animatable(initialY)
    var busy by mutableStateOf(false)
        private set

    suspend fun flingOut(decision: Decision, width: Float, height: Float) {
        busy = true
        coroutineScope {
            val spec = tween<Float>(durationMillis = 230, easing = FastOutLinearInEasing)
            when (decision) {
                Decision.KEEP -> {
                    launch { offsetX.animateTo(width * 1.6f, spec) }
                    launch { offsetY.animateTo(offsetY.value - height * 0.05f, spec) }
                }
                Decision.DELETE -> {
                    launch { offsetX.animateTo(-width * 1.6f, spec) }
                    launch { offsetY.animateTo(offsetY.value - height * 0.05f, spec) }
                }
                Decision.SKIP -> launch { offsetY.animateTo(-height * 1.4f, spec) }
                Decision.TRASHED -> Unit
            }
        }
    }

    suspend fun reset() {
        coroutineScope {
            val spec = spring<Float>(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
            launch { offsetX.animateTo(0f, spec) }
            launch { offsetY.animateTo(0f, spec) }
        }
        busy = false
    }

    companion object {
        /** Al deshacer, la tarjeta entra desde el lado por el que salió. */
        fun fromUndo(decision: Decision?, area: IntSize): SwipeState = when (decision) {
            Decision.KEEP -> SwipeState(initialX = area.width * 1.4f)
            Decision.DELETE -> SwipeState(initialX = -area.width * 1.4f)
            Decision.SKIP -> SwipeState(initialY = -area.height * 1.2f)
            else -> SwipeState()
        }
    }
}

/** Foto completa, sin recortes (ContentScale.Fit), con fecha y tamaño discretos. */
@Composable
fun PhotoCard(item: MediaItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var failed by remember(item.id) { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Surface1)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(item.uri).build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { failed = true },
            onSuccess = { failed = false },
            modifier = Modifier.fillMaxSize(),
        )
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se puede abrir esta foto", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Puede que se haya movido o eliminado. Desliza hacia arriba para saltarla.",
                    color = Color(0xFF9A9EA8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        Text(
            "${formatDate(item.dateMillis)}  ·  ${formatBytes(item.sizeBytes)}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/** Tarjeta superior arrastrable estilo Tinder. */
@Composable
fun SwipeCard(
    item: MediaItem,
    state: SwipeState,
    area: IntSize,
    onDecision: (Decision) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val w = area.width.toFloat().coerceAtLeast(1f)
    val h = area.height.toFloat().coerceAtLeast(1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = state.offsetX.value
                translationY = state.offsetY.value
                rotationZ = (state.offsetX.value / w) * 14f
            }
            .pointerInput(item.id, area) {
                detectDragGestures(
                    onDrag = { change, amount ->
                        change.consume()
                        if (!state.busy) {
                            scope.launch {
                                state.offsetX.snapTo(state.offsetX.value + amount.x)
                                state.offsetY.snapTo(state.offsetY.value + amount.y)
                            }
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (state.busy) return@launch
                            val x = state.offsetX.value
                            val y = state.offsetY.value
                            val decision = when {
                                x > w * SWIPE_X -> Decision.KEEP
                                x < -w * SWIPE_X -> Decision.DELETE
                                y < -h * SWIPE_Y && abs(y) > abs(x) -> Decision.SKIP
                                else -> null
                            }
                            if (decision == null) {
                                state.reset()
                            } else {
                                state.flingOut(decision, w, h)
                                onDecision(decision)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { if (!state.busy) state.reset() } },
                )
            }
    ) {
        PhotoCard(item, Modifier.fillMaxSize())

        // Tinte de color según la dirección
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .graphicsLayer { alpha = (state.offsetX.value / (w * SWIPE_X)).coerceIn(0f, 1f) * 0.22f }
                .background(KeepGreen)
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .graphicsLayer { alpha = (-state.offsetX.value / (w * SWIPE_X)).coerceIn(0f, 1f) * 0.22f }
                .background(DeleteRed)
        )

        // Etiquetas
        StampLabel(
            text = "KEEP",
            color = KeepGreen,
            rotation = -14f,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(28.dp)
                .graphicsLayer { alpha = (state.offsetX.value / (w * SWIPE_X)).coerceIn(0f, 1f) },
        )
        StampLabel(
            text = "DELETE",
            color = DeleteRed,
            rotation = 14f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp)
                .graphicsLayer { alpha = (-state.offsetX.value / (w * SWIPE_X)).coerceIn(0f, 1f) },
        )
        StampLabel(
            text = "SKIP",
            color = SkipBlue,
            rotation = 0f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .graphicsLayer {
                    val up = (-state.offsetY.value / (h * SWIPE_Y)).coerceIn(0f, 1f)
                    val side = (abs(state.offsetX.value) / (w * SWIPE_X)).coerceIn(0f, 1f)
                    alpha = (up - side).coerceIn(0f, 1f)
                },
        )
    }
}

/** Tarjeta siguiente, detrás; crece mientras se arrastra la superior. */
@Composable
fun BackCard(item: MediaItem, state: SwipeState, area: IntSize) {
    val w = area.width.toFloat().coerceAtLeast(1f)
    PhotoCard(
        item,
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = (maxOf(abs(state.offsetX.value), abs(state.offsetY.value)) / w).coerceIn(0f, 1f)
                val s = 0.92f + 0.08f * p
                scaleX = s
                scaleY = s
                alpha = 0.55f + 0.45f * p
            }
    )
}

@Composable
private fun StampLabel(text: String, color: Color, rotation: Float, modifier: Modifier) {
    Text(
        text,
        color = color,
        fontSize = 30.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation }
            .border(3.dp, color, RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
