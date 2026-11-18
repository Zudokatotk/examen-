package com.example.examen

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper

enum class TipoConexion {
    WIFI,
    DATOS_MOVILES,
    SIN_CONEXION
}

object NetworkSoundHelper {

    /**
     * Verifica si Wi-Fi y/o Datos Móviles están activos
     */
    fun verificarConexion(context: Context): TipoConexion {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return TipoConexion.SIN_CONEXION

        val activeNetwork = cm.activeNetwork ?: return TipoConexion.SIN_CONEXION
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return TipoConexion.SIN_CONEXION

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TipoConexion.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TipoConexion.DATOS_MOVILES
            else -> TipoConexion.SIN_CONEXION
        }
    }

    /**
     * Emite un sonido de alerta según el estado de la conexión
     * - isConnected = true -> Sonido de confirmación (tono agudo agradable)
     * - isConnected = false -> Sonido de advertencia (tono grave doble)
     */
    fun reproducirSonidoAlerta(isConnected: Boolean) {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            if (isConnected) {
                // Tono de conexión exitosa
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            } else {
                // Tono de alerta de desconexión
                toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 350)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                toneGenerator.release()
            }, 600)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
