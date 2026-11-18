package com.example.examen

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "examen_alumnos.db"
        const val DATABASE_VERSION = 1

        const val TABLE_ALUMNOS = "alumnos"
        const val COL_ID = "id"
        const val COL_FIRESTORE_ID = "firestore_id"
        const val COL_CI = "ci"
        const val COL_NOMBRES = "nombres"
        const val COL_APELLIDOS = "apellidos"
        const val COL_FECHA_NACIMIENTO = "fecha_nacimiento"
        const val COL_LATITUD = "latitud"
        const val COL_LONGITUD = "longitud"
        const val COL_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val query = """
            CREATE TABLE $TABLE_ALUMNOS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FIRESTORE_ID TEXT,
                $COL_CI TEXT NOT NULL,
                $COL_NOMBRES TEXT NOT NULL,
                $COL_APELLIDOS TEXT NOT NULL,
                $COL_FECHA_NACIMIENTO TEXT NOT NULL,
                $COL_LATITUD REAL NOT NULL,
                $COL_LONGITUD REAL NOT NULL,
                $COL_IS_SYNCED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ALUMNOS")
        onCreate(db)
    }

    fun insertarAlumno(alumno: Alumno): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FIRESTORE_ID, alumno.firestoreId)
            put(COL_CI, alumno.ci)
            put(COL_NOMBRES, alumno.nombres)
            put(COL_APELLIDOS, alumno.apellidos)
            put(COL_FECHA_NACIMIENTO, alumno.fechaNacimiento)
            put(COL_LATITUD, alumno.latitud)
            put(COL_LONGITUD, alumno.longitud)
            put(COL_IS_SYNCED, alumno.isSynced)
        }
        return db.insert(TABLE_ALUMNOS, null, values)
    }

    fun obtenerTodos(): List<Alumno> {
        val lista = mutableListOf<Alumno>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_ALUMNOS ORDER BY $COL_ID DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Alumno(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        ci = it.getString(it.getColumnIndexOrThrow(COL_CI)),
                        nombres = it.getString(it.getColumnIndexOrThrow(COL_NOMBRES)),
                        apellidos = it.getString(it.getColumnIndexOrThrow(COL_APELLIDOS)),
                        fechaNacimiento = it.getString(it.getColumnIndexOrThrow(COL_FECHA_NACIMIENTO)),
                        latitud = it.getDouble(it.getColumnIndexOrThrow(COL_LATITUD)),
                        longitud = it.getDouble(it.getColumnIndexOrThrow(COL_LONGITUD)),
                        isSynced = it.getInt(it.getColumnIndexOrThrow(COL_IS_SYNCED))
                    )
                )
            }
        }
        return lista
    }

    fun obtenerNoSincronizados(): List<Alumno> {
        val lista = mutableListOf<Alumno>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_ALUMNOS WHERE $COL_IS_SYNCED = 0 ORDER BY $COL_ID ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Alumno(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        ci = it.getString(it.getColumnIndexOrThrow(COL_CI)),
                        nombres = it.getString(it.getColumnIndexOrThrow(COL_NOMBRES)),
                        apellidos = it.getString(it.getColumnIndexOrThrow(COL_APELLIDOS)),
                        fechaNacimiento = it.getString(it.getColumnIndexOrThrow(COL_FECHA_NACIMIENTO)),
                        latitud = it.getDouble(it.getColumnIndexOrThrow(COL_LATITUD)),
                        longitud = it.getDouble(it.getColumnIndexOrThrow(COL_LONGITUD)),
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
        db.update(TABLE_ALUMNOS, values, "$COL_ID = ?", arrayOf(localId.toString()))
    }

    /**
     * Guarda en SQLite los alumnos que vienen de Firebase si no existen localmente
     * (cumple la nota del examen: ambas BD deben tener los mismos registros)
     */
    fun insertarSiNoExisteDesdeFirestore(alumno: Alumno) {
        val db = writableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_ID FROM $TABLE_ALUMNOS WHERE $COL_FIRESTORE_ID = ? OR $COL_CI = ?",
            arrayOf(alumno.firestoreId, alumno.ci)
        )
        val existe = cursor.use { it.moveToFirst() }
        if (!existe) {
            val values = ContentValues().apply {
                put(COL_FIRESTORE_ID, alumno.firestoreId)
                put(COL_CI, alumno.ci)
                put(COL_NOMBRES, alumno.nombres)
                put(COL_APELLIDOS, alumno.apellidos)
                put(COL_FECHA_NACIMIENTO, alumno.fechaNacimiento)
                put(COL_LATITUD, alumno.latitud)
                put(COL_LONGITUD, alumno.longitud)
                put(COL_IS_SYNCED, 1)
            }
            db.insert(TABLE_ALUMNOS, null, values)
        }
    }

    fun contarPendientes(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_ALUMNOS WHERE $COL_IS_SYNCED = 0",
            null
        )
        var total = 0
        cursor.use {
            if (it.moveToFirst()) total = it.getInt(0)
        }
        return total
    }
}
