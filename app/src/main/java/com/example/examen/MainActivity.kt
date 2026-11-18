package com.example.examen

import android.Manifest
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.os.Bundle
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    // Sensores de Hardware (Punto 9)
    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var sensorProximidad: Sensor? = null
    private var ultimoTimestampSensor: Long = 0L

    // Bases de Datos Local (SQLite) y Nube (Firestore) (Punto 10)
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var firestoreManager: FirestoreManager
    private var listaAlumnos = mutableStateOf<List<Alumno>>(emptyList())
    private var pendientesCount = mutableIntStateOf(0)

    // Estados reactivos
    private var estadoConexion = mutableStateOf(TipoConexion.SIN_CONEXION)
    private var mensajeAlerta = mutableStateOf<String?>(null)
    private var latitudCargada = mutableStateOf("0.0")
    private var longitudCargada = mutableStateOf("0.0")
    private var estadoSensorEnVivo = mutableStateOf("Esperando detección (Pasa tu mano o agita)")

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = DatabaseHelper(this)
        firestoreManager = FirestoreManager()

        iniciarMonitoreoRed()

        // Configuración de Sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorProximidad = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        recargarDatos()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1565C0),
                    secondary = Color(0xFF2E7D32),
                    surface = Color(0xFFF8F9FA)
                )
            ) {
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {}

                LaunchedEffect(Unit) {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fineGranted) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    RegistroAlumnoScreen(
                        tipoConexion = estadoConexion.value,
                        mensajeAlerta = mensajeAlerta.value,
                        latitud = latitudCargada.value,
                        longitud = longitudCargada.value,
                        sensorInfo = estadoSensorEnVivo.value,
                        alumnos = listaAlumnos.value,
                        pendientes = pendientesCount.intValue,
                        onVerificarConexionManual = {
                            comprobarRedYEmitirAlerta(mostrarToast = true)
                        },
                        onSimularSensorGPS = {
                            activarCapturaUbicacion("Simulación manual de sensor")
                        },
                        onDismissAlerta = { mensajeAlerta.value = null },
                        onRegistrarAlumno = { alumno ->
                            guardarAlumno(alumno)
                        },
                        onSincronizarManual = {
                            sincronizarBasesDeDatos()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        comprobarRedYEmitirAlerta(mostrarToast = false)

        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Registrar Proximidad con la mayor sensibilidad posible (SENSOR_DELAY_NORMAL o UI)
        sensorProximidad?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
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

    // ─────────────────────────────────────────────────────────────────────────
    // PUNTO 9: Detección Mejorada de Proximidad y Acelerómetro
    // ─────────────────────────────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val ahora = System.currentTimeMillis()

        // 1. SENSOR DE PROXIMIDAD
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distancia = event.values[0]
            val maxRango = event.sensor.maximumRange

            // En muchos dispositivos (Xiaomi, Samsung) 'cerca' es 0.0 o menor que el rango maximo
            val estaCerca = (distancia < maxRango) || (distancia == 0.0f) || (distancia <= 2.0f)

            estadoSensorEnVivo.value = if (estaCerca) {
                "✋ ¡MANO DETECTADA CERCA! (${distancia} cm)"
            } else {
                "👋 Mano Lejos (${distancia} cm / Max: ${maxRango} cm)"
            }

            // Si está cerca y pasaron al menos 1.2 segundos del último disparo
            if (estaCerca && (ahora - ultimoTimestampSensor > 1200)) {
                ultimoTimestampSensor = ahora
                activarCapturaUbicacion("Sensor de Proximidad (Mano cerca)")
            }
        }

        // 2. SENSOR DE ACELERÓMETRO (Agitar)
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val aceleracionTotal = sqrt((x * x + y * y + z * z).toDouble())

            // Si se agita con fuerza
            if (aceleracionTotal > 14.5 && (ahora - ultimoTimestampSensor > 1200)) {
                ultimoTimestampSensor = ahora
                estadoSensorEnVivo.value = "⚡ ¡SACUDIDA DETECTADA! (Aceleración: ${String.format(Locale.US, "%.1f", aceleracionTotal)})"
                activarCapturaUbicacion("Dispositivo Agitado (Acelerómetro)")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun activarCapturaUbicacion(origenSensor: String) {
        runOnUiThread {
            Toast.makeText(this, "📡 $origenSensor: Leyendo GPS...", Toast.LENGTH_SHORT).show()

            LocationHelper.obtenerCoordenadas(this) { lat, lng ->
                runOnUiThread {
                    latitudCargada.value = String.format(Locale.US, "%.5f", lat)
                    longitudCargada.value = String.format(Locale.US, "%.5f", lng)
                    Toast.makeText(
                        this,
                        "📍 Coordenadas cargadas: Lat ${latitudCargada.value}, Lng ${longitudCargada.value}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUNTO 10: Guardado y Sincronización
    // ─────────────────────────────────────────────────────────────────────────

    private fun guardarAlumno(alumno: Alumno) {
        if (alumno.ci.isEmpty() || alumno.nombres.isEmpty()) {
            Toast.makeText(this, "Por favor completa CI y Nombres", Toast.LENGTH_SHORT).show()
            return
        }

        val idLocal = dbHelper.insertarAlumno(alumno)
        alumno.id = idLocal

        val hayConexion = (estadoConexion.value != TipoConexion.SIN_CONEXION)

        if (hayConexion) {
            firestoreManager.subirAlumno(
                alumno = alumno,
                onSuccess = { firestoreId ->
                    dbHelper.marcarComoSincronizado(idLocal, firestoreId)
                    runOnUiThread {
                        Toast.makeText(this, "Guardado en SQLite y sincronizado en Firestore ☁️", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                },
                onFailure = {
                    runOnUiThread {
                        Toast.makeText(this, "Guardado en SQLite local (Fallo al subir)", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                }
            )
        } else {
            Toast.makeText(this, "Guardado en SQLite local (Modo Offline) 📱", Toast.LENGTH_SHORT).show()
            recargarDatos()
        }
    }

    private fun sincronizarBasesDeDatos() {
        if (estadoConexion.value == TipoConexion.SIN_CONEXION) {
            Toast.makeText(this, "No hay conexión para sincronizar ❌", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Sincronizando...", Toast.LENGTH_SHORT).show()

        val pendientes = dbHelper.obtenerNoSincronizados()
        for (item in pendientes) {
            firestoreManager.subirAlumno(
                alumno = item,
                onSuccess = { firestoreId ->
                    dbHelper.marcarComoSincronizado(item.id, firestoreId)
                    recargarDatos()
                },
                onFailure = {}
            )
        }

        firestoreManager.descargarAlumnos(
            onSuccess = { listaNube ->
                for (remoto in listaNube) {
                    dbHelper.insertarSiNoExisteDesdeFirestore(remoto)
                }
                runOnUiThread {
                    recargarDatos()
                    Toast.makeText(this, "¡Sincronización completa! Ambas bases coinciden ✔️", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = {
                runOnUiThread { recargarDatos() }
            }
        )
    }

    private fun recargarDatos() {
        listaAlumnos.value = dbHelper.obtenerTodos()
        pendientesCount.intValue = dbHelper.contarPendientes()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUNTO 8: Red y Alerta Sonora
    // ─────────────────────────────────────────────────────────────────────────

    private fun comprobarRedYEmitirAlerta(mostrarToast: Boolean) {
        val tipo = NetworkSoundHelper.verificarConexion(this)
        estadoConexion.value = tipo

        val hayInternet = (tipo != TipoConexion.SIN_CONEXION)
        NetworkSoundHelper.reproducirSonidoAlerta(hayInternet)

        val texto = when (tipo) {
            TipoConexion.WIFI -> "🟢 Wi-Fi ACTIVO • Se registrará en la nube (Firestore)"
            TipoConexion.DATOS_MOVILES -> "📱 Datos Móviles ACTIVOS • Se registrará en la nube"
            TipoConexion.SIN_CONEXION -> "🔴 SIN CONEXIÓN • Se registrará en la Base de Datos Local"
        }
        mensajeAlerta.value = texto

        if (mostrarToast) {
            Toast.makeText(this, texto, Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarMonitoreoRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    comprobarRedYEmitirAlerta(mostrarToast = true)
                    if (dbHelper.contarPendientes() > 0) {
                        sincronizarBasesDeDatos()
                    }
                }
            }
            override fun onLost(network: Network) {
                runOnUiThread { comprobarRedYEmitirAlerta(mostrarToast = true) }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PANTALLA COMPOSE
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroAlumnoScreen(
    tipoConexion: TipoConexion,
    mensajeAlerta: String?,
    latitud: String,
    longitud: String,
    sensorInfo: String,
    alumnos: List<Alumno>,
    pendientes: Int,
    onVerificarConexionManual: () -> Unit,
    onSimularSensorGPS: () -> Unit,
    onDismissAlerta: () -> Unit,
    onRegistrarAlumno: (Alumno) -> Unit,
    onSincronizarManual: () -> Unit
) {
    val context = LocalContext.current

    var ci by remember { mutableStateOf("") }
    var nombres by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }

    // Dialogo de Calendario para Fecha de Nacimiento
    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                fechaNacimiento = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            },
            calendar.get(Calendar.YEAR) - 20, // Año aproximado para estudiante (ej. 2004)
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "👨‍🎓 Registro de Alumnos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                "Examen Móvil II • SQLite ⇄ Cloud Firestore",
                                fontSize = 12.sp,
                                color = Color(0xFFBBDEFB)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1565C0))
                )

                val (colorFondo, textoBarra) = when (tipoConexion) {
                    TipoConexion.WIFI -> Color(0xFF2E7D32) to "📶 Wi-Fi ACTIVO (Online)"
                    TipoConexion.DATOS_MOVILES -> Color(0xFF1976D2) to "📶 DATOS MÓVILES ACTIVOS (Online)"
                    TipoConexion.SIN_CONEXION -> Color(0xFFC62828) to "⚠️ SIN CONEXIÓN (Modo Offline Local)"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colorFondo)
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = textoBarra,
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

            // Botones de prueba Punto 8 y Punto 9
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onVerificarConexionManual,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("🔊 Probar Red", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onSimularSensorGPS,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                    ) {
                        Text("📡 Forzar Sensor", fontSize = 12.sp)
                    }
                }
            }

            // Alerta si cambió la red
            mensajeAlerta?.let { mensaje ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (tipoConexion != TipoConexion.SIN_CONEXION) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mensaje,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tipoConexion != TipoConexion.SIN_CONEXION) Color(0xFF1B5E20) else Color(0xFFB71C1C),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onDismissAlerta) {
                                Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Card del Sensor en Vivo (Proximidad + Agitar)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📲 Sensor de Proximidad & Agitar (Punto 9):",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0277BD)
                        )
                        Text(
                            "• Tapa el auricular/cámara frontal con la mano O agita el teléfono para cargar Lat y Long.",
                            fontSize = 11.sp,
                            color = Color(0xFF01579B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFB3E5FC)
                        ) {
                            Text(
                                text = sensorInfo,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF004D40),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Título Formulario
            item {
                Text(
                    text = "Formulario de Registro (Punto 7)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1)
                )
            }

            // CI
            item {
                OutlinedTextField(
                    value = ci,
                    onValueChange = { ci = it },
                    label = { Text("Cédula de Identidad (CI)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Nombres
            item {
                OutlinedTextField(
                    value = nombres,
                    onValueChange = { nombres = it },
                    label = { Text("Nombres") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Apellidos
            item {
                OutlinedTextField(
                    value = apellidos,
                    onValueChange = { apellidos = it },
                    label = { Text("Apellidos") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Fecha de Nacimiento (con DatePicker / Calendario)
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fechaNacimiento,
                        onValueChange = {},
                        readOnly = true, // Se abre el calendario al hacer click
                        label = { Text("Fecha de Nacimiento (Toca para elegir)") },
                        placeholder = { Text("Seleccionar en el calendario") },
                        trailingIcon = {
                            IconButton(onClick = { datePickerDialog.show() }) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Calendario",
                                    tint = Color(0xFF1565C0)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    // Capa clickeable transparente para que al tocar cualquier parte del campo se abra el calendario
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { datePickerDialog.show() }
                    )
                }
            }

            // Latitud y Longitud (readOnly = true)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = latitud,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Latitud (Sensor)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFECEFF1),
                            unfocusedContainerColor = Color(0xFFECEFF1)
                        )
                    )

                    OutlinedTextField(
                        value = longitud,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Longitud (Sensor)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFECEFF1),
                            unfocusedContainerColor = Color(0xFFECEFF1)
                        )
                    )
                }
            }

            // Botones de Registro y Sincronización
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val nuevo = Alumno(
                                ci = ci.trim(),
                                nombres = nombres.trim(),
                                apellidos = apellidos.trim(),
                                fechaNacimiento = fechaNacimiento.trim(),
                                latitud = latitud.toDoubleOrNull() ?: 0.0,
                                longitud = longitud.toDoubleOrNull() ?: 0.0
                            )
                            onRegistrarAlumno(nuevo)
                            ci = ""
                            nombres = ""
                            apellidos = ""
                            fechaNacimiento = ""
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                    ) {
                        Text("💾 Guardar Alumno", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSincronizarManual,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("🔄 Sync ($pendientes)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Título de la lista
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📋 Alumnos Registrados (Punto 10)",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF37474F),
                        fontSize = 14.sp
                    )
                    Text(
                        "Total: ${alumnos.size}",
                        color = Color(0xFF78909C),
                        fontSize = 12.sp
                    )
                }
            }

            if (alumnos.isEmpty()) {
                item {
                    Text(
                        "No hay alumnos registrados aún.\nLlena el formulario y presiona 'Guardar Alumno'.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                        color = Color(0xFF9E9E9E),
                        fontSize = 13.sp
                    )
                }
            }

            items(alumnos, key = { it.id }) { alumno ->
                AlumnoItemCard(alumno = alumno)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun AlumnoItemCard(alumno: Alumno) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🎓 ${alumno.nombres} ${alumno.apellidos}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1565C0)
                )

                val (badgeText, badgeBg, badgeTextColor) = if (alumno.isSynced == 1) {
                    Triple("☁️ Firestore", Color(0xFFE8F5E9), Color(0xFF1B5E20))
                } else {
                    Triple("⏳ SQLite Local", Color(0xFFFFF3E0), Color(0xFFE65100))
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                "CI: ${alumno.ci} • Nacimiento: ${alumno.fechaNacimiento}",
                fontSize = 12.sp,
                color = Color(0xFF424242)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFFEEEEEE))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "📍 Lat: ${alumno.latitud} | Lng: ${alumno.longitud}",
                    fontSize = 11.sp,
                    color = Color(0xFF546E7A)
                )

                val docText = if (alumno.isSynced == 1 && alumno.firestoreId.isNotEmpty()) {
                    val shortId = if (alumno.firestoreId.length > 8) alumno.firestoreId.substring(0, 8) + "..." else alumno.firestoreId
                    "Doc: $shortId"
                } else {
                    "Doc: pendiente"
                }

                Text(
                    docText,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (alumno.isSynced == 1) Color(0xFF2E7D32) else Color(0xFFB0BEC5)
                )
            }
        }
    }
}
