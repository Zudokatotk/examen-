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
    const val RADIO_PERIMETRO_METROS = 100.0f

    fun calcularDistanciaMetros(
        lat1: Double,
        lon1: Double,
        lat2: Double = DEFAULT_LAT,
        lon2: Double = DEFAULT_LNG
    ): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

    fun estaDentroDelPerimetro(
        lat: Double,
        lon: Double,
        latCentro: Double = DEFAULT_LAT,
        lonCentro: Double = DEFAULT_LNG
    ): Boolean {
        return calcularDistanciaMetros(lat, lon, latCentro, lonCentro) <= RADIO_PERIMETRO_METROS
    }

    fun generarHtmlMapa(
        campusLat: Double,
        campusLng: Double,
        studentLat: Double,
        studentLng: Double,
        distancia: Float,
        dentro: Boolean
    ): String {
        val colorCirculo = if (dentro) "#2E7D32" else "#C62828"
        val estadoTexto = if (dentro) "REGISTRO HABILITADO" else "REGISTRO BLOQUEADO"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    body { margin:0; padding:0; font-family: sans-serif; background-color: #f5f5f5; }
                    #map { width: 100%; height: 100vh; }
                    .info-box {
                        position: absolute;
                        top: 10px;
                        left: 10px;
                        right: 10px;
                        background: white;
                        padding: 10px 14px;
                        border-radius: 10px;
                        box-shadow: 0 3px 8px rgba(0,0,0,0.25);
                        z-index: 1000;
                        font-size: 13px;
                    }
                </style>
            </head>
            <body>
                <div class="info-box">
                    <span style="font-weight:bold; color: $colorCirculo;">$estadoTexto</span><br>
                    <span>Distancia a Univalle: <b>${distancia.toInt()} m</b> (Límite: 100m)</span>
                </div>
                <div id="map"></div>
                <script>
                    var map = L.map('map').setView([$campusLat, $campusLng], 17);
                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19
                    }).addTo(map);

                    L.marker([$campusLat, $campusLng]).addTo(map)
                        .bindPopup('<b>🏛️ Campus Univalle</b><br>Centro del Perímetro');

                    L.circle([$campusLat, $campusLng], {
                        color: '$colorCirculo',
                        fillColor: '$colorCirculo',
                        fillOpacity: 0.25,
                        radius: 100
                    }).addTo(map);

                    L.marker([$studentLat, $studentLng]).addTo(map)
                        .bindPopup('<b>📍 Estudiante</b><br>Distancia: ${distancia.toInt()} m')
                        .openPopup();

                    var bounds = L.latLngBounds([
                        [$campusLat, $campusLng],
                        [$studentLat, $studentLng]
                    ]);
                    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 18 });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

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
