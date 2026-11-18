package com.example.examen

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirestoreManager"

    companion object {
        const val COLECCION_ALUMNOS = "alumnos"
    }

    /**
     * Sube un alumno a Cloud Firestore
     */
    fun subirAlumno(
        alumno: Alumno,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val documento = hashMapOf(
            "ci" to alumno.ci,
            "nombres" to alumno.nombres,
            "apellidos" to alumno.apellidos,
            "fechaNacimiento" to alumno.fechaNacimiento,
            "latitud" to alumno.latitud,
            "longitud" to alumno.longitud,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection(COLECCION_ALUMNOS)
            .add(documento)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Alumno subido a Firestore con ID: ${docRef.id}")
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al subir alumno a Firestore", e)
                onFailure(e)
            }
    }

    /**
     * Descarga todos los alumnos de Firestore para sincronizar bidireccionalmente
     */
    fun descargarAlumnos(
        onSuccess: (List<Alumno>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection(COLECCION_ALUMNOS)
            .get()
            .addOnSuccessListener { snapshot ->
                val lista = snapshot.documents.mapNotNull { doc ->
                    try {
                        Alumno(
                            firestoreId = doc.id,
                            ci = doc.getString("ci") ?: "",
                            nombres = doc.getString("nombres") ?: "",
                            apellidos = doc.getString("apellidos") ?: "",
                            fechaNacimiento = doc.getString("fechaNacimiento") ?: "",
                            latitud = doc.getDouble("latitud") ?: 0.0,
                            longitud = doc.getDouble("longitud") ?: 0.0,
                            isSynced = 1
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                onSuccess(lista)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al descargar alumnos de Firestore", e)
                onFailure(e)
            }
    }
}
