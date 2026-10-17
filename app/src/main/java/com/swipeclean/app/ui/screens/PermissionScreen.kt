package com.swipeclean.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeclean.app.ui.theme.TextDim

@Composable
fun PermissionScreen(onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SwipeClean", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Revisa tus fotos deslizando y libera espacio.",
            color = TextDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Text(
            "Para mostrarte tus fotos, SwipeClean necesita acceso a tu galería. " +
                "Todo ocurre en tu teléfono: no se sube nada a internet y nada se elimina sin tu confirmación.",
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Permitir acceso a fotos", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onOpenSettings) {
            Text("Abrir ajustes de la app", color = TextDim)
        }
        Text(
            "Si Android ya no muestra el diálogo, concede el permiso «Fotos y vídeos» desde los ajustes.",
            color = TextDim,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}
