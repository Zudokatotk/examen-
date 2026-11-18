package com.example.examen

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "examen_sensores.db"
        const val DATABASE_VERSION = 1

        const val TABLE_MEDICIONES = "mediciones"
        const val COL_ID = "id"
        const val COL_FIRESTORE_ID = "firestore_id"
        const val COL_TIPO_SENSOR = "tipo_sensor"
        const val COL_VALOR = "valor"
        const val COL_FECHA_HORA = "fecha_hora"
        const val COL_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val query = """
            CREATE TABLE $TABLE_MEDICIONES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FIRESTORE_ID TEXT,
                $COL_TIPO_SENSOR TEXT NOT NULL,
                $COL_VALOR REAL NOT NULL,
                $COL_FECHA_HORA TEXT NOT NULL,
                $COL_IS_SYNCED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEDICIONES")
        onCreate(db)
    }

    fun insertarMedicion(medicion: Medicion): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FIRESTORE_ID, medicion.firestoreId)
            put(COL_TIPO_SENSOR, medicion.tipoSensor)
            put(COL_VALOR, medicion.valor)
            put(COL_FECHA_HORA, medicion.fechaHora)
            put(COL_IS_SYNCED, medicion.isSynced)
        }
        return db.insert(TABLE_MEDICIONES, null, values)
    }

    fun obtenerTodas(): List<Medicion> {
        val lista = mutableListOf<Medicion>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MEDICIONES ORDER BY $COL_ID DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Medicion(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        tipoSensor = it.getString(it.getColumnIndexOrThrow(COL_TIPO_SENSOR)),
                        valor = it.getDouble(it.getColumnIndexOrThrow(COL_VALOR)),
                        fechaHora = it.getString(it.getColumnIndexOrThrow(COL_FECHA_HORA)),
                        isSynced = it.getInt(it.getColumnIndexOrThrow(COL_IS_SYNCED))
                    )
                )
            }
        }
        return lista
    }

    fun obtenerNoSincronizadas(): List<Medicion> {
        val lista = mutableListOf<Medicion>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_MEDICIONES WHERE $COL_IS_SYNCED = 0 ORDER BY $COL_ID ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Medicion(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        tipoSensor = it.getString(it.getColumnIndexOrThrow(COL_TIPO_SENSOR)),
                        valor = it.getDouble(it.getColumnIndexOrThrow(COL_VALOR)),
                        fechaHora = it.getString(it.getColumnIndexOrThrow(COL_FECHA_HORA)),
                        isSynced = 0
                    )
                )
            }
        }
        return lista
    }

    fun marcarComoSincronizado(localId: Long, firestoreId: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_IS_SYNCED, 1)
            put(COL_FIRESTORE_ID, firestoreId)
        }
        db.update(TABLE_MEDICIONES, values, "$COL_ID = ?", arrayOf(localId.toString()))
    }

    fun contarPendientes(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MEDICIONES WHERE $COL_IS_SYNCED = 0",
            null
        )
        var total = 0
        cursor.use {
            if (it.moveToFirst()) {
                total = it.getInt(0)
            }
        }
        return total
    }

    fun limpiarTabla() {
        val db = writableDatabase
        db.delete(TABLE_MEDICIONES, null, null)
    }
}
