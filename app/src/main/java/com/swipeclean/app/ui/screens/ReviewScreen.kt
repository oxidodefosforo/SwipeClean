package com.swipeclean.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeclean.app.ui.MainViewModel
import com.swipeclean.app.ui.components.StatPill
import com.swipeclean.app.ui.components.Thumb
import com.swipeclean.app.ui.formatBytes
import com.swipeclean.app.ui.theme.DeleteRed
import com.swipeclean.app.ui.theme.KeepGreen
import com.swipeclean.app.ui.theme.TextDim

@Composable
fun ReviewScreen(vm: MainViewModel) {
    BackHandler { vm.closeReview() }
    var confirmCancel by remember { mutableStateOf(false) }

    val photos = vm.pendingItems
    val selected = photos.filter { !vm.deselected.containsKey(it.id) }
    val selectedBytes = selected.sumOf { it.sizeBytes }
    val stats = vm.stats

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeReview() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Resumen", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("Revisadas", "${stats.reviewed}", modifier = Modifier.weight(1f))
            StatPill("Conservadas", "${stats.kept}", KeepGreen, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatPill("Para eliminar", "${selected.size}", DeleteRed, Modifier.weight(1f))
            StatPill("Espacio a liberar", formatBytes(selectedBytes), modifier = Modifier.weight(1f))
        }

        if (photos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No hay fotos seleccionadas para eliminar.",
                    color = TextDim,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Text(
                "Toca una foto para quitarla de la selección (se conservará).",
                color = TextDim,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(photos, key = { it.id }) { item ->
                    Thumb(
                        item = item,
                        selected = !vm.deselected.containsKey(item.id),
                        selectedColor = DeleteRed,
                        unselectedLabel = "Conservar",
                        onClick = { vm.toggleDeselect(item.id) },
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photos.isNotEmpty()) {
                Button(
                    onClick = { vm.confirmTrash() },
                    enabled = selected.isNotEmpty() || vm.deselected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = DeleteRed, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Text(
                        if (selected.isEmpty()) "Conservar todas" else "Enviar ${selected.size} a la papelera",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                Text(
                    "Android te pedirá confirmación. Podrás restaurarlas desde la papelera.",
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photos.isNotEmpty()) {
                    OutlinedButton(onClick = { confirmCancel = true }, modifier = Modifier.weight(1f)) {
                        Text("Cancelar eliminación", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                    }
                }
                OutlinedButton(onClick = { vm.newSession() }, modifier = Modifier.weight(1f)) {
                    Text("Nueva sesión", color = MaterialTheme.colorScheme.onBackground, fontSize = 13.sp)
                }
            }
            if (vm.queue.isNotEmpty()) {
                TextButton(onClick = { vm.closeReview() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Seguir revisando (${vm.queue.size} restantes)", color = TextDim)
                }
            }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("¿Cancelar la eliminación?") },
            text = { Text("Se vacía la cola de eliminación. Estas fotos volverán a aparecer para revisarlas. No se elimina nada.") },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; vm.cancelDeletion() }) { Text("Vaciar cola") }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text("Volver") }
            },
        )
    }
}
