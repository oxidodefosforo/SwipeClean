package com.swipeclean.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swipeclean.app.ui.MainViewModel
import com.swipeclean.app.ui.Screen
import com.swipeclean.app.ui.screens.PermissionScreen
import com.swipeclean.app.ui.screens.ReviewScreen
import com.swipeclean.app.ui.screens.SetupScreen
import com.swipeclean.app.ui.screens.SwipeScreen
import com.swipeclean.app.ui.screens.TrashScreen
import com.swipeclean.app.ui.theme.SwipeCleanTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwipeCleanTheme { AppRoot(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-evaluar permisos: el usuario pudo cambiarlos en Ajustes o ampliar su selección.
        vm.onAccessChecked(Permissions.check(this))
    }
}

@Composable
private fun AppRoot(vm: MainViewModel) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val access = Permissions.check(context)
        vm.onAccessChecked(access)
        if (access != Access.NONE) vm.reload()
    }

    // Diálogos oficiales del sistema (papelera / restaurar / eliminar definitivamente).
    val systemLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        vm.onSystemResult(result.resultCode == Activity.RESULT_OK)
    }

    val pendingRequest = vm.request
    LaunchedEffect(pendingRequest) {
        if (pendingRequest != null) {
            vm.onRequestLaunched()
            systemLauncher.launch(IntentSenderRequest.Builder(pendingRequest.intentSender).build())
        }
    }

    val snackbar = remember { SnackbarHostState() }
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = msg.text,
            actionLabel = msg.actionLabel,
            withDismissAction = msg.actionLabel == null,
            duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        vm.messageShown(msg)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (vm.screen) {
                Screen.PERMISSION -> PermissionScreen(
                    onRequest = { permissionLauncher.launch(Permissions.required()) },
                    onOpenSettings = { Permissions.openAppSettings(context) },
                )
                Screen.SETUP -> SetupScreen(
                    vm,
                    onRequestMoreAccess = { permissionLauncher.launch(Permissions.required()) },
                )
                Screen.SWIPE -> SwipeScreen(vm)
                Screen.REVIEW -> ReviewScreen(vm)
                Screen.TRASH -> TrashScreen(vm)
            }

            val busy = vm.busyMessage
            if (busy != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* bloquea toques mientras se procesa */ },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(busy, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}
