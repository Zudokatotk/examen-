package com.example.examen

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    // Sensores
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var actualX = mutableFloatStateOf(0f)
    private var actualY = mutableFloatStateOf(0f)
    private var actualZ = mutableFloatStateOf(9.81f)
    private var actualMagnitud = mutableFloatStateOf(9.81f)

    // Red y Base de datos
    private var isOnlineState = mutableStateOf(true)
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var firestoreManager: FirestoreManager
    private var listaMediciones = mutableStateOf<List<Medicion>>(emptyList())
    private var pendientesCount = mutableIntStateOf(0)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = DatabaseHelper(this)
        firestoreManager = FirestoreManager()

        // Configurar Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Monitoreo de Red
        registrarObservadorRed()
        isOnlineState.value = NetworkHelper.hayConexion(this)
        recargarDatos()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF2E7D32),
                    surface = Color(0xFFF8F9FA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    SensorSyncScreen(
                        isOnline = isOnlineState.value,
                        magnitud = actualMagnitud.floatValue,
                        ejeX = actualX.floatValue,
                        ejeY = actualY.floatValue,
                        ejeZ = actualZ.floatValue,
                        mediciones = listaMediciones.value,
                        pendientes = pendientesCount.intValue,
                        onRegistrar = { registrarMedicion() },
                        onSincronizar = { sincronizarPendientes() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        isOnlineState.value = NetworkHelper.hayConexion(this)
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

    // --- SENSOR LISTENER ---
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            actualX.floatValue = x
            actualY.floatValue = y
            actualZ.floatValue = z
            actualMagnitud.floatValue = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- CRUD Y SINCRONIZACIÓN ---
    private fun registrarMedicion() {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fecha = sdf.format(Date())

        val nueva = Medicion(
            tipoSensor = "Acelerómetro",
            valor = actualMagnitud.floatValue.toDouble(),
            ejeX = actualX.floatValue.toDouble(),
            ejeY = actualY.floatValue.toDouble(),
            ejeZ = actualZ.floatValue.toDouble(),
            fechaHora = fecha,
            isSynced = 0
        )

        // 1. Guardar local siempre
        val idLocal = dbHelper.insertarMedicion(nueva)
        nueva.id = idLocal

        // 2. Si hay conexión, subir directo a Firestore
        if (NetworkHelper.hayConexion(this)) {
            firestoreManager.subirMedicion(
                medicion = nueva,
                onSuccess = { fId ->
                    dbHelper.marcarComoSincronizado(idLocal, fId)
                    Toast.makeText(this, "Guardado y subido a Firestore ☁️", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                },
                onFailure = {
                    Toast.makeText(this, "Guardado local (Fallo al subir)", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                }
            )
        } else {
            Toast.makeText(this, "Guardado solo local (Sin conexión) 📱", Toast.LENGTH_SHORT).show()
            recargarDatos()
        }
    }

    private fun sincronizarPendientes() {
        if (!NetworkHelper.hayConexion(this)) {
            Toast.makeText(this, "No hay conexión a internet ❌", Toast.LENGTH_SHORT).show()
            return
        }

        val noSinc = dbHelper.obtenerNoSincronizadas()
        if (noSinc.isEmpty()) {
            Toast.makeText(this, "Todo está al día en Firestore ✔️", Toast.LENGTH_SHORT).show()
            return
        }

        var count = 0
        for (item in noSinc) {
            firestoreManager.subirMedicion(
                medicion = item,
                onSuccess = { docId ->
                    dbHelper.marcarComoSincronizado(item.id, docId)
                    count++
                    if (count == noSinc.size) {
                        Toast.makeText(this, "Sincronizados $count registros ☁️", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                },
                onFailure = {
                    recargarDatos()
                }
            )
        }
    }

    private fun recargarDatos() {
        listaMediciones.value = dbHelper.obtenerTodas()
        pendientesCount.intValue = dbHelper.contarPendientes()
    }

    private fun registrarObservadorRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val req = NetworkRequest.Builder().build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    isOnlineState.value = true
                    if (dbHelper.contarPendientes() > 0) {
                        sincronizarPendientes()
                    }
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread { isOnlineState.value = false }
            }
        }
        cm.registerNetworkCallback(req, networkCallback!!)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPOSABLE UI (Jetpack Compose)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSyncScreen(
    isOnline: Boolean,
    magnitud: Float,
    ejeX: Float,
    ejeY: Float,
    ejeZ: Float,
    mediciones: List<Medicion>,
    pendientes: Int,
    onRegistrar: () -> Unit,
    onSincronizar: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "📡 Monitor de Sensores y Nube",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                "Examen Móvil II • Jetpack Compose + Firestore",
                                fontSize = 12.sp,
                                color = Color(0xFFBBDEFB)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1565C0)
                    )
                )
                // Barra de Conexión
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isOnline) Color(0xFF2E7D32) else Color(0xFFC62828))
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isOnline) "🟢 ONLINE • Conectado a Firestore" else "🔴 OFFLINE • Guardando en SQLite Local",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(6.dp)) }

            // Card del Sensor en Vivo
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Acelerómetro en Vivo",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1),
                                fontSize = 15.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE3F2FD)
                            ) {
                                Text(
                                    "ACTIVO",
                                    color = Color(0xFF1976D2),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.2f", magnitud),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "m/s² (Magnitud total)",
                                fontSize = 13.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Desglose de Ejes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AxisBox(title = "EJE X", value = ejeX, color = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                            AxisBox(title = "EJE Y", value = ejeY, color = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                            AxisBox(title = "EJE Z", value = ejeZ, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Botones de Acción
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRegistrar,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text("💾 Capturar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = onSincronizar,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("🔄 Sync ($pendientes)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Título de la Lista
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📋 Registros (SQLite 1 Tabla)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF37474F),
                        fontSize = 14.sp
                    )
                    Text(
                        "Total: ${mediciones.size}",
                        color = Color(0xFF78909C),
                        fontSize = 12.sp
                    )
                }
            }

            // Estado Vacío
            if (mediciones.isEmpty()) {
                item {
                    Text(
                        "No hay mediciones aún.\nMueve el dispositivo y presiona 'Capturar'.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }

            // Lista de Mediciones
            items(mediciones, key = { it.id }) { item ->
                MedicionCard(medicion = item)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun AxisBox(title: String, value: Float, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF5F5F5)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF757575))
            Text(
                String.format(Locale.US, "%.2f", value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun MedicionCard(medicion: Medicion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header del item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📡 ${medicion.tipoSensor} (#${medicion.id})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A237E)
                )

                val (badgeText, badgeBg, badgeTextColor) = if (medicion.isSynced == 1) {
                    Triple("☁️ Sincronizado", Color(0xFFE8F5E9), Color(0xFF1B5E20))
                } else {
                    Triple("⏳ Solo Local", Color(0xFFFFF3E0), Color(0xFFE65100))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        badgeText,
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Valor y ejes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        String.format(Locale.US, "%.2f", medicion.valor),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF212121)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("m/s²", fontSize = 12.sp, color = Color(0xFF757575))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF0F2F5)
                ) {
                    Text(
                        String.format(Locale.US, "X:%.1f | Y:%.1f | Z:%.1f", medicion.ejeX, medicion.ejeY, medicion.ejeZ),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF455A64),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))

            // Fecha y doc id
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "🕒 ${medicion.fechaHora}",
                    fontSize = 11.sp,
                    color = Color(0xFF757575)
                )

                val docText = if (medicion.isSynced == 1 && medicion.firestoreId.isNotEmpty()) {
                    val shortId = if (medicion.firestoreId.length > 8) medicion.firestoreId.substring(0, 8) + "..." else medicion.firestoreId
                    "Doc: $shortId"
                } else {
                    "Doc: pendiente"
                }

                Text(
                    docText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (medicion.isSynced == 1) Color(0xFF2E7D32) else Color(0xFFB0BEC5)
                )
            }
        }
    }
}
