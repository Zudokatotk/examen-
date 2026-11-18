package com.example.examen

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Vistas
    private lateinit var layoutEstadoRed: LinearLayout
    private lateinit var tvEstadoRed: TextView
    private lateinit var tvSensorMagnitud: TextView
    private lateinit var tvEjeX: TextView
    private lateinit var tvEjeY: TextView
    private lateinit var tvEjeZ: TextView
    private lateinit var btnRegistrar: Button
    private lateinit var btnSincronizar: Button
    private lateinit var tvTotalRegistros: TextView
    private lateinit var tvVacio: TextView
    private lateinit var rvMediciones: RecyclerView

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var valorMagnitudActual: Double = 9.81
    private var actualX: Double = 0.0
    private var actualY: Double = 0.0
    private var actualZ: Double = 9.81

    // Base de datos y red
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var firestoreManager: FirestoreManager
    private lateinit var adapter: MedicionAdapter
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Inicializar helpers
        dbHelper = DatabaseHelper(this)
        firestoreManager = FirestoreManager()

        // Vincular vistas
        layoutEstadoRed = findViewById(R.id.layout_estado_red)
        tvEstadoRed = findViewById(R.id.tv_estado_red)
        tvSensorMagnitud = findViewById(R.id.tv_sensor_magnitud)
        tvEjeX = findViewById(R.id.tv_eje_x)
        tvEjeY = findViewById(R.id.tv_eje_y)
        tvEjeZ = findViewById(R.id.tv_eje_z)
        btnRegistrar = findViewById(R.id.btn_registrar)
        btnSincronizar = findViewById(R.id.btn_sincronizar)
        tvTotalRegistros = findViewById(R.id.tv_total_registros)
        tvVacio = findViewById(R.id.tv_vacio)
        rvMediciones = findViewById(R.id.rv_mediciones)

        // Configurar RecyclerView
        adapter = MedicionAdapter(emptyList())
        rvMediciones.layoutManager = LinearLayoutManager(this)
        rvMediciones.adapter = adapter

        // Configurar sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Configurar eventos
        btnRegistrar.setOnClickListener { registrarMedicion() }
        btnSincronizar.setOnClickListener { sincronizarPendientes() }

        // Monitorear red y cargar datos
        registrarObservadorRed()
        actualizarEstadoRedUI(NetworkHelper.hayConexion(this))
        recargarDatos()
    }

    override fun onResume() {
        super.onResume()
        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        actualizarEstadoRedUI(NetworkHelper.hayConexion(this))
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(it)
        }
    }

    // --- SENSORES ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            actualX = event.values[0].toDouble()
            actualY = event.values[1].toDouble()
            actualZ = event.values[2].toDouble()
            valorMagnitudActual = sqrt(actualX * actualX + actualY * actualY + actualZ * actualZ)

            tvSensorMagnitud.text = String.format(Locale.US, "%.2f", valorMagnitudActual)
            tvEjeX.text = String.format(Locale.US, "%.2f", actualX)
            tvEjeY.text = String.format(Locale.US, "%.2f", actualY)
            tvEjeZ.text = String.format(Locale.US, "%.2f", actualZ)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- ACCIONES CRUD Y SINCRONIZACIÓN ---

    private fun registrarMedicion() {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fecha = sdf.format(Date())

        val nuevaMedicion = Medicion(
            tipoSensor = "Acelerómetro",
            valor = valorMagnitudActual,
            ejeX = actualX,
            ejeY = actualY,
            ejeZ = actualZ,
            fechaHora = fecha,
            isSynced = 0
        )

        // 1. Siempre se guarda localmente primero en SQLite (1 sola tabla)
        val idLocal = dbHelper.insertarMedicion(nuevaMedicion)
        nuevaMedicion.id = idLocal

        // 2. Si hay conexión, subir a Firestore de inmediato
        if (NetworkHelper.hayConexion(this)) {
            firestoreManager.subirMedicion(
                medicion = nuevaMedicion,
                onSuccess = { firestoreId ->
                    dbHelper.marcarComoSincronizado(idLocal, firestoreId)
                    Toast.makeText(this, "Guardado y sincronizado en Firestore ☁️", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                },
                onFailure = {
                    Toast.makeText(this, "Guardado local (Fallo al subir a Firestore)", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                }
            )
        } else {
            // Sin conexión: queda guardado solo local con is_synced = 0
            Toast.makeText(this, "Guardado localmente en SQLite (Offline) 📱", Toast.LENGTH_SHORT).show()
            recargarDatos()
        }
    }

    private fun sincronizarPendientes() {
        if (!NetworkHelper.hayConexion(this)) {
            Toast.makeText(this, "No hay conexión a internet para sincronizar ❌", Toast.LENGTH_SHORT).show()
            return
        }

        val pendientes = dbHelper.obtenerNoSincronizadas()
        if (pendientes.isEmpty()) {
            Toast.makeText(this, "Todos los registros ya están en la nube ✔️", Toast.LENGTH_SHORT).show()
            return
        }

        btnSincronizar.isEnabled = false
        var subidos = 0

        for (item in pendientes) {
            firestoreManager.subirMedicion(
                medicion = item,
                onSuccess = { firestoreId ->
                    dbHelper.marcarComoSincronizado(item.id, firestoreId)
                    subidos++
                    if (subidos == pendientes.size) {
                        btnSincronizar.isEnabled = true
                        Toast.makeText(this, "¡Sincronización completa! ($subidos subidos) ☁️", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                },
                onFailure = {
                    btnSincronizar.isEnabled = true
                }
            )
        }
    }

    private fun recargarDatos() {
        val lista = dbHelper.obtenerTodas()
        adapter.actualizarLista(lista)

        tvTotalRegistros.text = "Total: ${lista.size}"
        tvVacio.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE

        val pendientes = dbHelper.contarPendientes()
        btnSincronizar.text = "🔄 Sincronizar ($pendientes)"
    }

    // --- MONITOREO DE CONEXIÓN EN TIEMPO REAL ---

    private fun registrarObservadorRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    actualizarEstadoRedUI(true)
                    if (dbHelper.contarPendientes() > 0) {
                        sincronizarPendientes()
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    actualizarEstadoRedUI(false)
                }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }

    private fun actualizarEstadoRedUI(hayInternet: Boolean) {
        if (hayInternet) {
            layoutEstadoRed.setBackgroundColor(Color.parseColor("#2E7D32")) // Verde
            tvEstadoRed.text = "🟢 MODO ONLINE • Conexión Activa a Firestore"
        } else {
            layoutEstadoRed.setBackgroundColor(Color.parseColor("#C62828")) // Rojo
            tvEstadoRed.text = "🔴 MODO OFFLINE • Guardando en SQLite Local"
        }
    }
}
