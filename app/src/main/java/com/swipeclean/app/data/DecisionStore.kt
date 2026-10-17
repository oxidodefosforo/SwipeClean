package com.swipeclean.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Base de datos local (SQLite) con las decisiones por foto.
 * Solo guarda el id de MediaStore, la decisión y metadatos mínimos; nunca la imagen.
 */
class DecisionStore(context: Context) :
    SQLiteOpenHelper(context, "swipeclean.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE decisions (
                media_id INTEGER PRIMARY KEY,
                decision INTEGER NOT NULL,
                size INTEGER NOT NULL,
                date INTEGER NOT NULL,
                updated INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun loadAll(): Map<Long, DecisionRecord> {
        val out = HashMap<Long, DecisionRecord>()
        readableDatabase.rawQuery(
            "SELECT media_id, decision, size, date FROM decisions", null
        ).use { c ->
            while (c.moveToNext()) {
                val decision = Decision.fromCode(c.getInt(1)) ?: continue
                out[c.getLong(0)] = DecisionRecord(decision, c.getLong(2), c.getLong(3))
            }
        }
        return out
    }

    fun put(id: Long, record: DecisionRecord) {
        writableDatabase.insertWithOnConflict(
            "decisions", null, values(id, record), SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun putAll(records: Map<Long, DecisionRecord>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            records.forEach { (id, r) ->
                db.insertWithOnConflict("decisions", null, values(id, r), SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.delete("decisions", "media_id = ?", arrayOf(it.toString())) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Reinicia el progreso pero conserva el registro de fotos en la papelera (para poder restaurarlas). */
    fun clearExceptTrashed() {
        writableDatabase.delete("decisions", "decision != ?", arrayOf(Decision.TRASHED.code.toString()))
    }

    private fun values(id: Long, r: DecisionRecord) = ContentValues().apply {
        put("media_id", id)
        put("decision", r.decision.code)
        put("size", r.sizeBytes)
        put("date", r.dateMillis)
        put("updated", System.currentTimeMillis())
    }
}
