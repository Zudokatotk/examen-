package com.example.examen

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreManager"

    companion object {
        const val COLECCION_MEDICIONES = "mediciones"
    }

    fun subirMedicion(
        medicion: Medicion,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "tipoSensor" to medicion.tipoSensor,
            "valor" to medicion.valor,
            "fechaHora" to medicion.fechaHora,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection(COLECCION_MEDICIONES)
            .add(data)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Documento subido con ID: ${docRef.id}")
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al subir documento", e)
                onFailure(e)
            }
    }
}
