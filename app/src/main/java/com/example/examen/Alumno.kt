package com.example.examen

data class Alumno(
    var id: Long = 0,
    var firestoreId: String = "",
    val ci: String = "",
    val nombres: String = "",
    val apellidos: String = "",
    val fechaNacimiento: String = "",
    val latitud: Double = 0.0,
    val longitud: Double = 0.0,
    var isSynced: Int = 0 // 0 = pendiente local, 1 = sincronizado en Firebase
)
