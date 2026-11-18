package com.example.examen

import android.os.Build
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreManager"

    companion object {
        // Nombre de la colección en Firestore
        const val COLECCION_MEDICIONES = "mediciones"
    }

    fun subirMedicion(
        medicion: Medicion,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        // Campos que se guardan en el documento de Firestore
        val documento = hashMapOf(
            "tipoSensor" to medicion.tipoSensor,
            "valor" to medicion.valor,
            "ejeX" to medicion.ejeX,
            "ejeY" to medicion.ejeY,
            "ejeZ" to medicion.ejeZ,
            "fechaHora" to medicion.fechaHora,
            "timestamp" to System.currentTimeMillis(),
            "dispositivo" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "origen" to "Examen Móvil II"
        )

        db.collection(COLECCION_MEDICIONES)
            .add(documento)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Documento creado en la colección '$COLECCION_MEDICIONES' con ID: ${docRef.id}")
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar en Firestore", e)
                onFailure(e)
            }
    }
}
