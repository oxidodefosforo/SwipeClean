package com.swipeclean.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class Access { NONE, PARTIAL, FULL }

object Permissions {

    /** Solo se solicita lectura de imágenes. Nada de vídeo, audio, ubicación ni internet. */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun check(context: Context): Access {
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= 33 -> when {
                granted(Manifest.permission.READ_MEDIA_IMAGES) -> Access.FULL
                Build.VERSION.SDK_INT >= 34 &&
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.PARTIAL
                else -> Access.NONE
            }
            granted(Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.FULL
            else -> Access.NONE
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
