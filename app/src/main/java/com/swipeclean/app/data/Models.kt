package com.swipeclean.app.data

import android.net.Uri

/** Una foto de la galería (solo metadatos ligeros; la imagen se carga bajo demanda). */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val dateMillis: Long,
    val sizeBytes: Long,
    val bucketId: String,
    val bucketName: String,
    val year: Int,
    val month: Int, // 1..12
)

enum class Decision(val code: Int) {
    KEEP(1),
    DELETE(2),   // en cola para enviar a la papelera (aún NO eliminada)
    SKIP(3),
    TRASHED(4);  // confirmada en la papelera del sistema

    companion object {
        fun fromCode(code: Int): Decision? = entries.firstOrNull { it.code == code }
    }
}

data class DecisionRecord(
    val decision: Decision,
    val sizeBytes: Long,
    val dateMillis: Long,
)

enum class SortOrder { NEWEST, OLDEST, RANDOM }

data class Filter(
    val albumId: String? = null,
    val year: Int? = null,
    val month: Int? = null,
    val order: SortOrder = SortOrder.NEWEST,
)

data class Album(val id: String, val name: String, val count: Int)
