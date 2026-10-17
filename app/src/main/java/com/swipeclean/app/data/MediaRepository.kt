package com.swipeclean.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.BaseColumns
import android.provider.MediaStore
import java.util.Calendar

/**
 * Acceso directo a las fotos ORIGINALES mediante MediaStore.
 * La app nunca copia, comprime ni modifica archivos.
 */
class MediaRepository(context: Context) {

    private val resolver: ContentResolver = context.contentResolver

    /** Colección "external": une todos los volúmenes (memoria interna y tarjeta SD). */
    private val collection: Uri = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun uriOf(id: Long): Uri = ContentUris.withAppendedId(collection, id)

    /**
     * Carga solo metadatos (id, fecha, tamaño, álbum). Con 10.000+ fotos ocupa pocos MB.
     * Las fotos ya en la papelera o pendientes no se devuelven (comportamiento por defecto).
     */
    fun loadAll(): List<MediaItem> {
        val projection = arrayOf(
            BaseColumns._ID,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
        )
        val result = ArrayList<MediaItem>(4096)
        val cal = Calendar.getInstance()
        resolver.query(collection, projection, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(BaseColumns._ID)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val taken = if (c.isNull(iTaken)) 0L else c.getLong(iTaken)
                val added = if (c.isNull(iAdded)) 0L else c.getLong(iAdded) * 1000L
                val modified = if (c.isNull(iMod)) 0L else c.getLong(iMod) * 1000L
                val date = when {
                    taken > 0L -> taken
                    added > 0L -> added
                    else -> modified
                }
                cal.timeInMillis = date
                result.add(
                    MediaItem(
                        id = id,
                        uri = uriOf(id),
                        dateMillis = date,
                        sizeBytes = if (c.isNull(iSize)) 0L else c.getLong(iSize),
                        bucketId = c.getString(iBucket) ?: "",
                        bucketName = c.getString(iBucketName) ?: "Sin álbum",
                        year = cal.get(Calendar.YEAR),
                        month = cal.get(Calendar.MONTH) + 1,
                    )
                )
            }
        }
        return result
    }

    /**
     * Estado real de cada foto en MediaStore, incluyendo las que están en la papelera.
     * Devuelve id -> estaEnPapelera. Los ids ausentes del mapa ya no existen.
     * Se usa para VERIFICAR cada operación antes de informar al usuario.
     */
    fun queryTrashState(ids: Collection<Long>): Map<Long, Boolean> {
        val out = HashMap<Long, Boolean>(ids.size)
        ids.chunked(400).forEach { chunk ->
            val args = Bundle().apply {
                putString(
                    ContentResolver.QUERY_ARG_SQL_SELECTION,
                    "${BaseColumns._ID} IN (${chunk.joinToString(",")})"
                )
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(
                collection,
                arrayOf(BaseColumns._ID, MediaStore.MediaColumns.IS_TRASHED),
                args,
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    out[c.getLong(0)] = c.getInt(1) == 1
                }
            }
        }
        return out
    }

    /** Diálogo oficial del sistema para enviar a la papelera (trash=true) o restaurar (trash=false). */
    fun trashRequest(ids: List<Long>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, ids.map(::uriOf), trash).intentSender

    /** Diálogo oficial del sistema para eliminar definitivamente. Solo bajo petición explícita. */
    fun deleteRequest(ids: List<Long>): IntentSender =
        MediaStore.createDeleteRequest(resolver, ids.map(::uriOf)).intentSender
}
