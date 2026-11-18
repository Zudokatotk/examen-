package com.example.examen

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var ultimoTimestampSensor: Long = 0L

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var firestoreManager: FirestoreManager
    private var listaAlumnos = mutableStateOf<List<Alumno>>(emptyList())
    private var pendientesCount = mutableIntStateOf(0)

    private var estadoConexion = mutableStateOf(TipoConexion.SIN_CONEXION)
    private var mensajeAlerta = mutableStateOf<String?>(null)
    private var latitudCargada = mutableStateOf("0.0")
    private var longitudCargada = mutableStateOf("0.0")
    private var estadoSensorEnVivo = mutableStateOf("Listo: Agita el teléfono para cargar GPS")

    private var campusCentroLat = mutableDoubleStateOf(LocationHelper.DEFAULT_LAT)
    private var campusCentroLng = mutableDoubleStateOf(LocationHelper.DEFAULT_LNG)
    private var mostrarDialogoMapa = mutableStateOf(false)
    private var mensajeBloqueoPerimetro = mutableStateOf<String?>(null)

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = DatabaseHelper(this)
        firestoreManager = FirestoreManager()

        iniciarMonitoreoRed()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

                RegistroAlumnoScreen(
                    tipoConexion = estadoConexion.value,
                    mensajeAlerta = mensajeAlerta.value,
                    latitud = latitudCargada.value,
                    longitud = longitudCargada.value,
                    sensorInfo = estadoSensorEnVivo.value,
                    alumnos = listaAlumnos.value,
                    pendientes = pendientesCount.intValue,
                    campusLat = campusCentroLat.doubleValue,
                    campusLng = campusCentroLng.doubleValue,
                    onVerificarConexionManual = { comprobarRedYEmitirAlerta(mostrarToast = true) },
                    onSimularSensorGPS = {
                        estadoSensorEnVivo.value = "⚡ ¡Sensor Agitado!"
                        activarCapturaUbicacion("Manual")
                    },
                    onDismissAlerta = { mensajeAlerta.value = null },
                    onRegistrarAlumno = { alumno -> guardarAlumno(alumno) },
                    onSincronizarManual = { sincronizarBasesDeDatos() },
                    onAbrirMapa = { mostrarDialogoMapa.value = true },
                    onCalibrarCampusActual = {
                        val latActual = latitudCargada.value.toDoubleOrNull() ?: LocationHelper.DEFAULT_LAT
                        val lngActual = longitudCargada.value.toDoubleOrNull() ?: LocationHelper.DEFAULT_LNG
                        campusCentroLat.doubleValue = latActual
                        campusCentroLng.doubleValue = lngActual
                        Toast.makeText(this, "Centro de Univalle calibrado a tu ubicación actual", Toast.LENGTH_SHORT).show()
                    },
                    onSimularDentro = {
                        latitudCargada.value = String.format(Locale.US, "%.5f", campusCentroLat.doubleValue + 0.0003)
                        longitudCargada.value = String.format(Locale.US, "%.5f", campusCentroLng.doubleValue + 0.0003)
                        Toast.makeText(this, "Simulando ubicación DENTRO del campus (<100m)", Toast.LENGTH_SHORT).show()
                    },
                    onSimularFuera = {
                        latitudCargada.value = String.format(Locale.US, "%.5f", campusCentroLat.doubleValue + 0.0035)
                        longitudCargada.value = String.format(Locale.US, "%.5f", campusCentroLng.doubleValue + 0.0035)
                        Toast.makeText(this, "Simulando ubicación FUERA del campus (>100m)", Toast.LENGTH_SHORT).show()
                    }
                )

                if (mostrarDialogoMapa.value) {
                    val latEstudiante = latitudCargada.value.toDoubleOrNull() ?: campusCentroLat.doubleValue
                    val lngEstudiante = longitudCargada.value.toDoubleOrNull() ?: campusCentroLng.doubleValue
                    val distancia = LocationHelper.calcularDistanciaMetros(
                        latEstudiante,
                        lngEstudiante,
                        campusCentroLat.doubleValue,
                        campusCentroLng.doubleValue
                    )
                    val dentro = distancia <= LocationHelper.RADIO_PERIMETRO_METROS

                    DialogoMapaPerimetro(
                        campusLat = campusCentroLat.doubleValue,
                        campusLng = campusCentroLng.doubleValue,
                        studentLat = latEstudiante,
                        studentLng = lngEstudiante,
                        distancia = distancia,
                        dentro = dentro,
                        onDismiss = { mostrarDialogoMapa.value = false },
                        onAbrirGoogleMaps = {
                            val uri = Uri.parse("geo:$latEstudiante,$lngEstudiante?q=$latEstudiante,$lngEstudiante(Estudiante)")
                            val mapIntent = Intent(Intent.ACTION_VIEW, uri)
                            try {
                                startActivity(mapIntent)
                            } catch (e: Exception) {
                                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=$latEstudiante,$lngEstudiante"))
                                startActivity(webIntent)
                            }
                        }
                    )
                }

                mensajeBloqueoPerimetro.value?.let { mensaje ->
                    AlertDialog(
                        onDismissRequest = { mensajeBloqueoPerimetro.value = null },
                        title = { Text("Perímetro No Autorizado", fontWeight = FontWeight.Bold) },
                        text = { Text(mensaje) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    mensajeBloqueoPerimetro.value = null
                                    mostrarDialogoMapa.value = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                            ) {
                                Text("Verificar en Mapa")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { mensajeBloqueoPerimetro.value = null }) {
                                Text("Entendido")
                            }
                        }
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
        comprobarRedYEmitirAlerta(mostrarToast = false)
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

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val ahora = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0] / SensorManager.GRAVITY_EARTH
            val y = event.values[1] / SensorManager.GRAVITY_EARTH
            val z = event.values[2] / SensorManager.GRAVITY_EARTH
            val gForce = sqrt((x * x + y * y + z * z).toDouble())

            if (gForce > 1.25 && (ahora - ultimoTimestampSensor > 1200)) {
                ultimoTimestampSensor = ahora
                estadoSensorEnVivo.value = "⚡ ¡Dispositivo Agitado!"
                activarCapturaUbicacion("Acelerómetro (Agitado)")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun activarCapturaUbicacion(origenSensor: String) {
        runOnUiThread {
            Toast.makeText(this, "Obteniendo coordenadas por GPS...", Toast.LENGTH_SHORT).show()
            LocationHelper.obtenerCoordenadas(this) { lat, lng ->
                runOnUiThread {
                    latitudCargada.value = String.format(Locale.US, "%.5f", lat)
                    longitudCargada.value = String.format(Locale.US, "%.5f", lng)
                    Toast.makeText(
                        this,
                        "Coordenadas cargadas: Lat ${latitudCargada.value}, Lng ${longitudCargada.value}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun guardarAlumno(alumno: Alumno) {
        if (alumno.ci.isEmpty() || alumno.nombres.isEmpty()) {
            Toast.makeText(this, "Por favor completa CI y Nombres", Toast.LENGTH_SHORT).show()
            return
        }

        if (alumno.latitud == 0.0 && alumno.longitud == 0.0) {
            Toast.makeText(this, "Debes agitar el teléfono para capturar la ubicación antes de registrar", Toast.LENGTH_SHORT).show()
            return
        }

        val distancia = LocationHelper.calcularDistanciaMetros(
            alumno.latitud,
            alumno.longitud,
            campusCentroLat.doubleValue,
            campusCentroLng.doubleValue
        )

        if (distancia > LocationHelper.RADIO_PERIMETRO_METROS) {
            mensajeBloqueoPerimetro.value = "No es posible registrar al estudiante.\n\nSe encuentra a ${distancia.toInt()} metros del campus universitario. El registro solo está permitido dentro del perímetro de 100 metros."
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
                        Toast.makeText(this, "Guardado en SQLite y sincronizado en Firestore", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                },
                onFailure = {
                    runOnUiThread {
                        Toast.makeText(this, "Guardado en SQLite local", Toast.LENGTH_SHORT).show()
                        recargarDatos()
                    }
                }
            )
        } else {
            Toast.makeText(this, "Guardado en SQLite local (Modo Offline)", Toast.LENGTH_SHORT).show()
            recargarDatos()
        }
    }

    private fun sincronizarBasesDeDatos() {
        if (estadoConexion.value == TipoConexion.SIN_CONEXION) {
            Toast.makeText(this, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Sincronización completa", Toast.LENGTH_SHORT).show()
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

    private fun comprobarRedYEmitirAlerta(mostrarToast: Boolean) {
        val tipo = NetworkSoundHelper.verificarConexion(this)
        estadoConexion.value = tipo

        val hayInternet = (tipo != TipoConexion.SIN_CONEXION)
        NetworkSoundHelper.reproducirSonidoAlerta(hayInternet)

        val texto = when (tipo) {
            TipoConexion.WIFI -> "Wi-Fi Activo"
            TipoConexion.DATOS_MOVILES -> "Datos Móviles Activos"
            TipoConexion.SIN_CONEXION -> "Sin Conexión (Modo Offline)"
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
    campusLat: Double,
    campusLng: Double,
    onVerificarConexionManual: () -> Unit,
    onSimularSensorGPS: () -> Unit,
    onDismissAlerta: () -> Unit,
    onRegistrarAlumno: (Alumno) -> Unit,
    onSincronizarManual: () -> Unit,
    onAbrirMapa: () -> Unit,
    onCalibrarCampusActual: () -> Unit,
    onSimularDentro: () -> Unit,
    onSimularFuera: () -> Unit
) {
    val context = LocalContext.current

    var ci by remember { mutableStateOf("") }
    var nombres by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }

    val latNum = latitud.toDoubleOrNull() ?: 0.0
    val lngNum = longitud.toDoubleOrNull() ?: 0.0
    val tieneCoordenadas = (latNum != 0.0 && lngNum != 0.0)
    val distanciaActual = if (tieneCoordenadas) {
        LocationHelper.calcularDistanciaMetros(latNum, lngNum, campusLat, campusLng)
    } else {
        0f
    }
    val dentroPerimetro = tieneCoordenadas && (distanciaActual <= LocationHelper.RADIO_PERIMETRO_METROS)

    val calendar = Calendar.getInstance()
    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                fechaNacimiento = String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)
            },
            calendar.get(Calendar.YEAR) - 20,
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
                                "Registro de Alumnos",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            Text(
                                "SQLite y Cloud Firestore",
                                fontSize = 12.sp,
                                color = Color(0xFFBBDEFB)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1565C0))
                )

                val (colorFondo, textoBarra) = when (tipoConexion) {
                    TipoConexion.WIFI -> Color(0xFF2E7D32) to "Wi-Fi Activo"
                    TipoConexion.DATOS_MOVILES -> Color(0xFF1976D2) to "Datos Móviles Activos"
                    TipoConexion.SIN_CONEXION -> Color(0xFFC62828) to "Sin Conexión (Modo Offline)"
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
                        Text("Verificar Conexión", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onSimularSensorGPS,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF455A64))
                    ) {
                        Text("Activar Sensor", fontSize = 12.sp)
                    }
                }
            }

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

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Sensor de Agitado:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0277BD)
                        )
                        Text(
                            "Agita el teléfono para cargar Latitud y Longitud.",
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

            item {
                Text(
                    text = "Datos del Estudiante",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D47A1)
                )
            }

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

            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = fechaNacimiento,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fecha de Nacimiento") },
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
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { datePickerDialog.show() }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = latitud,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Latitud") },
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
                        label = { Text("Longitud") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFECEFF1),
                            unfocusedContainerColor = Color(0xFFECEFF1)
                        )
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (!tieneCoordenadas) {
                            Color(0xFFF5F5F5)
                        } else if (dentroPerimetro) {
                            Color(0xFFE8F5E9)
                        } else {
                            Color(0xFFFFEBEE)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (!tieneCoordenadas) Color(0xFF616161) else if (dentroPerimetro) Color(0xFF2E7D32) else Color(0xFFC62828),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Perímetro Universidad (100 m):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (!tieneCoordenadas) Color(0xFF424242) else if (dentroPerimetro) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val estadoTexto = if (!tieneCoordenadas) {
                            "Agita el teléfono para obtener GPS y comprobar si estás dentro del perímetro."
                        } else if (dentroPerimetro) {
                            "🟢 Registro Habilitado: Estás a ${distanciaActual.toInt()}m de Univalle (Dentro de los 100m)."
                        } else {
                            "🔴 Registro Bloqueado: Estás a ${distanciaActual.toInt()}m de Univalle (Supera los 100m)."
                        }

                        Text(
                            text = estadoTexto,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (!tieneCoordenadas) Color(0xFF616161) else if (dentroPerimetro) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onAbrirMapa,
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B))
                            ) {
                                Text("Verificar en Mapa", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onCalibrarCampusActual,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Fijar Campus", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onSimularDentro,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Probar Adentro (45m)", fontSize = 10.sp, color = Color(0xFF2E7D32))
                            }
                            TextButton(
                                onClick = onSimularFuera,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Probar Afuera (350m)", fontSize = 10.sp, color = Color(0xFFC62828))
                            }
                        }
                    }
                }
            }

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
                        Text("Guardar Alumno", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onSincronizarManual,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Sync ($pendientes)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Alumnos Registrados",
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
                        "No hay alumnos registrados aún.",
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DialogoMapaPerimetro(
    campusLat: Double,
    campusLng: Double,
    studentLat: Double,
    studentLng: Double,
    distancia: Float,
    dentro: Boolean,
    onDismiss: () -> Unit,
    onAbrirGoogleMaps: () -> Unit
) {
    val colorEstado = if (dentro) Color(0xFF2E7D32) else Color(0xFFC62828)
    val tituloEstado = if (dentro) "REGISTRO POSIBLE" else "REGISTRO BLOQUEADO"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Verificación en Mapa (100m)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (dentro) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = "$tituloEstado: ${distancia.toInt()} m",
                        color = colorEstado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Perímetro permitido: radio de 100m alrededor de Univalle.",
                    fontSize = 11.sp,
                    color = Color.DarkGray
                )
                Spacer(modifier = Modifier.height(8.dp))

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadDataWithBaseURL(
                                "https://openstreetmap.org",
                                LocationHelper.generarHtmlMapa(
                                    campusLat = campusLat,
                                    campusLng = campusLng,
                                    studentLat = studentLat,
                                    studentLng = studentLng,
                                    distancia = distancia,
                                    dentro = dentro
                                ),
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(10.dp))
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onAbrirGoogleMaps,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Abrir en App Google Maps", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("Cerrar")
            }
        }
    )
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
fun PreviewPantallaAlumno() {
    RegistroAlumnoScreen(
        tipoConexion = TipoConexion.WIFI,
        mensajeAlerta = null,
        latitud = "-17.3753",
        longitud = "-66.1568",
        sensorInfo = "Listo: Agita el teléfono",
        alumnos = listOf(
            Alumno(
                id = 1,
                ci = "8392102",
                nombres = "Juan Carlos",
                apellidos = "Pérez Gómez",
                fechaNacimiento = "15/04/2001",
                latitud = -17.3753,
                longitud = -66.1568,
                isSynced = 1,
                firestoreId = "AbCdEf123"
            )
        ),
        pendientes = 0,
        campusLat = -17.3753,
        campusLng = -66.1568,
        onVerificarConexionManual = {},
        onSimularSensorGPS = {},
        onDismissAlerta = {},
        onRegistrarAlumno = {},
        onSincronizarManual = {},
        onAbrirMapa = {},
        onCalibrarCampusActual = {},
        onSimularDentro = {},
        onSimularFuera = {}
    )
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
                    "${alumno.nombres} ${alumno.apellidos}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1565C0)
                )

                val (badgeText, badgeBg, badgeTextColor) = if (alumno.isSynced == 1) {
                    Triple("Firestore", Color(0xFFE8F5E9), Color(0xFF1B5E20))
                } else {
                    Triple("SQLite Local", Color(0xFFFFF3E0), Color(0xFFE65100))
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
                "CI: ${alumno.ci}",
                fontSize = 12.sp,
                color = Color(0xFF455A64)
            )

            Text(
                "Nacimiento: ${alumno.fechaNacimiento}",
                fontSize = 12.sp,
                color = Color(0xFF455A64)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFF5F5F5)
            ) {
                Text(
                    "📍 Lat: ${alumno.latitud} | Lng: ${alumno.longitud}",
                    fontSize = 11.sp,
                    color = Color(0xFF37474F),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
