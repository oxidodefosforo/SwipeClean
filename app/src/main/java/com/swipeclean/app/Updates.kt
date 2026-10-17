package com.swipeclean.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager

/**
 * Actualizaciones sin permiso de Internet: la app solo abre la página de Releases en el navegador.
 * Allí se descarga el APK nuevo y se instala encima (misma clave de firma = se conservan los datos).
 */
object Updates {
    const val RELEASES_URL = "https://github.com/oxidodefosforo/SwipeClean/releases/latest"

    fun currentVersion(context: Context): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun openReleases(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            // Sin navegador instalado: no hay nada más que hacer.
        }
    }
}
