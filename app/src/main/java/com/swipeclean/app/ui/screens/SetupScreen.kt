package com.swipeclean.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeclean.app.Access
import com.swipeclean.app.Updates
import com.swipeclean.app.data.SortOrder
import com.swipeclean.app.ui.MainViewModel
import com.swipeclean.app.ui.formatBytes
import com.swipeclean.app.ui.components.ChoiceRow
import com.swipeclean.app.ui.components.SelectField
import com.swipeclean.app.ui.monthName
import com.swipeclean.app.ui.theme.DeleteRed
import com.swipeclean.app.ui.theme.Surface2
import com.swipeclean.app.ui.theme.TextDim

@Composable
fun SetupScreen(vm: MainViewModel, onRequestMoreAccess: () -> Unit) {
    val f = vm.filter
    val stats = vm.stats
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SwipeClean", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Elige qué fotos revisar", color = TextDim)

        if (vm.access == Access.PARTIAL) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface2)
                    .clickable(onClick = onRequestMoreAccess)
                    .padding(16.dp)
            ) {
                Text("Acceso limitado", fontWeight = FontWeight.SemiBold)
                Text(
                    "Solo ves las fotos que elegiste. Toca aquí para seleccionar más o permitir el acceso a todas.",
                    color = TextDim,
                    fontSize = 13.sp,
                )
            }
        }

        if (vm.loading && vm.albums.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp)
                Text("Leyendo galería…", color = TextDim)
            }
        }

        SelectField(
            label = "Álbum o carpeta",
            valueText = vm.albums.firstOrNull { it.id == f.albumId }?.name ?: "Todas las fotos",
            options = listOf<Pair<String?, String>>(null to "Todas las fotos") +
                vm.albums.map { it.id to "${it.name}  (${it.count})" },
            onSelect = { vm.updateFilter(f.copy(albumId = it)) },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SelectField(
                label = "Año",
                valueText = f.year?.toString() ?: "Todos",
                options = listOf<Pair<Int?, String>>(null to "Todos") + vm.years.map { it to it.toString() },
                onSelect = { vm.updateFilter(f.copy(year = it)) },
                modifier = Modifier.weight(1f),
            )
            SelectField(
                label = "Mes",
                valueText = f.month?.let { monthName(it) } ?: "Todos",
                options = listOf<Pair<Int?, String>>(null to "Todos") + (1..12).map { it to monthName(it) },
                onSelect = { vm.updateFilter(f.copy(month = it)) },
                enabled = f.year != null,
                modifier = Modifier.weight(1f),
            )
        }

        Text("Orden", color = TextDim, fontSize = 13.sp)
        ChoiceRow(
            options = listOf(
                SortOrder.NEWEST to "Recientes",
                SortOrder.OLDEST to "Antiguas",
                SortOrder.RANDOM to "Aleatorio",
            ),
            selected = f.order,
            onSelect = { vm.updateFilter(f.copy(order = it)) },
        )

        val remaining = stats.total - stats.reviewed
        Text(
            "${stats.total} fotos en este filtro · $remaining por revisar",
            color = TextDim,
            fontSize = 13.sp,
        )

        Button(
            onClick = { vm.startSession() },
            enabled = remaining > 0 && !vm.loading,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Text(
                if (stats.reviewed > 0) "Continuar" else "Comenzar",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        if (vm.pending.count > 0) {
            OutlinedButton(
                onClick = { vm.openReview() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    "Revisar eliminación · ${vm.pending.count} (${formatBytes(vm.pending.bytes)})",
                    color = DeleteRed,
                )
            }
        }

        OutlinedButton(onClick = { vm.openTrash() }, modifier = Modifier.fillMaxWidth()) {
            Text("Papelera de SwipeClean", color = MaterialTheme.colorScheme.onBackground)
        }

        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Reiniciar progreso", color = TextDim)
        }

        val context = LocalContext.current
        val version = remember { Updates.currentVersion(context) }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface2)
                .clickable { Updates.openReleases(context) }
                .padding(16.dp)
        ) {
            Text("Buscar actualizaciones", fontWeight = FontWeight.SemiBold)
            Text(
                "Versión instalada: $version. Abre GitHub para descargar la última versión; " +
                    "instálala encima y conservarás tu progreso.",
                color = TextDim,
                fontSize = 13.sp,
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("¿Reiniciar progreso?") },
            text = {
                Text(
                    "Todas las fotos volverán a aparecer como no revisadas y se vaciará la cola de eliminación. " +
                        "No se elimina ni se modifica ninguna foto."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; vm.resetProgress() }) { Text("Reiniciar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            },
        )
    }
}
