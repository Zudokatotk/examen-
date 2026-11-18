package com.example.examen

data class Medicion(
    var id: Long = 0,
    var firestoreId: String = "",
    val tipoSensor: String = "Acelerómetro",
    val valor: Double = 0.0,
    val ejeX: Double = 0.0,
    val ejeY: Double = 0.0,
    val ejeZ: Double = 0.0,
    val fechaHora: String = "",
    var isSynced: Int = 0 // 0 = pendiente local, 1 = sincronizado en la nube
)
