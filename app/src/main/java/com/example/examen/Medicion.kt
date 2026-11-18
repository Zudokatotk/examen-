package com.example.examen

data class Medicion(
    var id: Long = 0,
    var firestoreId: String = "",
    val tipoSensor: String = "",
    val valor: Double = 0.0,
    val fechaHora: String = "",
    var isSynced: Int = 0 // 0 = pendiente (offline), 1 = sincronizado (online)
)
