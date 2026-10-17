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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeclean.app.ui.MainViewModel
import com.swipeclean.app.ui.components.Thumb
import com.swipeclean.app.ui.formatBytes
import com.swipeclean.app.ui.theme.DeleteRed
import com.swipeclean.app.ui.theme.KeepGreen
import com.swipeclean.app.ui.theme.TextDim

@Composable
fun TrashScreen(vm: MainViewModel) {
    BackHandler { vm.closeTrash() }
    var confirmDelete by remember { mutableStateOf(false) }

    val photos = vm.trashItems
    val selected = photos.filter { vm.trashSelected.containsKey(it.id) }
    val totalBytes = photos.sumOf { it.sizeBytes }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeTrash() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Papelera", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (photos.isNotEmpty()) {
                TextButton(onClick = { vm.selectAllTrash(selected.size != photos.size) }) {
                    Text(if (selected.size == photos.size) "Ninguna" else "Todas", color = TextDim)
                }
            }
        }
        Text(
            "Fotos que SwipeClean envió a la papelera del sistema (${photos.size} · ${formatBytes(totalBytes)}). " +
                "Siguen ocupando espacio hasta que Android vacía la papelera (normalmente a los 30 días) " +
                "o hasta que las elimines definitivamente.",
            color = TextDim,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )

        if (photos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("La papelera está vacía.", color = TextDim, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(photos, key = { it.id }) { item ->
                    Thumb(
                        item = item,
                        selected = vm.trashSelected.containsKey(item.id),
                        selectedColor = KeepGreen,
                        unselectedLabel = null,
                        onClick = { vm.toggleTrashSelection(item.id) },
                    )
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.restoreSelected() },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Restaurar ${selected.size}", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Eliminar definitivamente ${selected.size} (${formatBytes(selected.sumOf { it.sizeBytes })})",
                    color = DeleteRed,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar definitivamente?") },
            text = {
                Text(
                    "Se borrarán ${selected.size} fotos de forma permanente y no podrás recuperarlas. " +
                        "Android te pedirá una última confirmación."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteSelectedForever() }) {
                    Text("Eliminar", color = DeleteRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}
