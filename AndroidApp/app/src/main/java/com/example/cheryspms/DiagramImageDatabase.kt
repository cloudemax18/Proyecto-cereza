package com.example.cheryspms

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Guarda las ilustraciones convertidas a WebP al abrirlas por primera vez.
 * El límite evita que la caché de diagramas crezca sin control.
 */
class DiagramImageDatabase(context: Context) :
    SQLiteOpenHelper(context, "chery_diagrams.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE diagram_images (
                cache_key TEXT PRIMARY KEY NOT NULL,
                image_webp BLOB NOT NULL,
                source_url TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX diagram_images_updated_at ON diagram_images(updated_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 has no previous schema to migrate.
    }

    fun getImage(key: String): ByteArray? {
        readableDatabase.query(
            "diagram_images",
            arrayOf("image_webp"),
            "cache_key = ?",
            arrayOf(key),
            null, null, null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getBlob(0) else null
        }
    }

    @Synchronized
    fun putImage(key: String, webp: ByteArray, sourceUrl: String) {
        if (webp.isEmpty()) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put("cache_key", key)
            put("image_webp", webp)
            put("source_url", sourceUrl)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("diagram_images", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        trimCache(db, key)
    }

    private fun trimCache(db: SQLiteDatabase, keepKey: String) {
        val maxBytes = 100L * 1024L * 1024L
        var total = 0L
        db.rawQuery("SELECT COALESCE(SUM(length(image_webp)), 0) FROM diagram_images", null).use {
            if (it.moveToFirst()) total = it.getLong(0)
        }
        if (total <= maxBytes) return
        db.rawQuery(
            "SELECT cache_key, length(image_webp) FROM diagram_images WHERE cache_key != ? ORDER BY updated_at ASC",
            arrayOf(keepKey)
        ).use { cursor ->
            val remove = mutableListOf<String>()
            while (total > maxBytes && cursor.moveToNext()) {
                remove += cursor.getString(0)
                total -= cursor.getLong(1)
            }
            remove.forEach { key ->
                db.delete("diagram_images", "cache_key = ?", arrayOf(key))
            }
        }
    }
}
