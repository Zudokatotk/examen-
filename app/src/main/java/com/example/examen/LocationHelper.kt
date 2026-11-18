package com.example.examen

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat

object LocationHelper {

    const val DEFAULT_LAT = -17.3753
    const val DEFAULT_LNG = -66.1568

    @SuppressLint("MissingPermission")
    fun obtenerCoordenadas(
        context: Context,
        onUbicacionObtenida: (Double, Double) -> Unit
    ) {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocation && !coarseLocation) {
            onUbicacionObtenida(DEFAULT_LAT, DEFAULT_LNG)
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onUbicacionObtenida(DEFAULT_LAT, DEFAULT_LNG)
            return
        }

        val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNetwork = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val bestLocation: Location? = lastGps ?: lastNetwork

        if (bestLocation != null) {
            onUbicacionObtenida(bestLocation.latitude, bestLocation.longitude)
            return
        }

        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onUbicacionObtenida(location.latitude, location.longitude)
                    lm.removeUpdates(this)
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
                override fun onProviderEnabled(p0: String) {}
                override fun onProviderDisabled(p0: String) {}
            }

            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, null)
            } else if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, null)
            } else {
                onUbicacionObtenida(DEFAULT_LAT, DEFAULT_LNG)
            }
        } catch (e: Exception) {
            onUbicacionObtenida(DEFAULT_LAT, DEFAULT_LNG)
        }
    }
}
