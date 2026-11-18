package com.example.examen

import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    private var estadoConexion = mutableStateOf(TipoConexion.SIN_CONEXION)
    private var mensajeAlerta = mutableStateOf<String?>(null)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Monitoreo en tiempo real de Wi-Fi y Datos
        iniciarMonitoreoRed()

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
                    RegistroAlumnoScreen(
                        tipoConexion = estadoConexion.value,
                        mensajeAlerta = mensajeAlerta.value,
                        onVerificarConexionManual = {
                            comprobarRedYEmitirAlerta(mostrarToast = true)
                        },
                        onDismissAlerta = { mensajeAlerta.value = null },
                        onRegistrarAlumno = { alumno ->
                            Toast.makeText(
                                this,
                                "Alumno registrado: ${alumno.nombres} ${alumno.apellidos}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        comprobarRedYEmitirAlerta(mostrarToast = false)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            cm?.unregisterNetworkCallback(it)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUNTO 8: Verificación de sensores Wi-Fi / Datos + Sonido + Mensaje
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
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    comprobarRedYEmitirAlerta(mostrarToast = true)
                }
            }
        }
        cm.registerNetworkCallback(request, networkCallback!!)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PANTALLA COMPOSE (Punto 7 + Punto 8)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroAlumnoScreen(
    tipoConexion: TipoConexion,
    mensajeAlerta: String?,
    onVerificarConexionManual: () -> Unit,
    onDismissAlerta: () -> Unit,
    onRegistrarAlumno: (Alumno) -> Unit
) {
    var ci by remember { mutableStateOf("") }
    var nombres by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }

    var latitud by remember { mutableStateOf("0.0") }
    var longitud by remember { mutableStateOf("0.0") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "👨‍🎓 Registro de Alumnos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1565C0)
                    )
                )

                // Barra indicadora de Wi-Fi / Datos (Punto 8)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botón de prueba para Punto 8 (Emitir sonido y verificar Wi-Fi)
            OutlinedButton(
                onClick = onVerificarConexionManual,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("🔊 Probar Sensor Wi-Fi / Datos (Alerta Sonora)")
            }

            // Diálogo o Card de Alerta si cambió la red (Punto 8)
            mensajeAlerta?.let { mensaje ->
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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

            Text(
                text = "Datos del Estudiante (Punto 7)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1)
            )

            // CI
            OutlinedTextField(
                value = ci,
                onValueChange = { ci = it },
                label = { Text("Cédula de Identidad (CI)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Nombres
            OutlinedTextField(
                value = nombres,
                onValueChange = { nombres = it },
                label = { Text("Nombres") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Apellidos
            OutlinedTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                label = { Text("Apellidos") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Fecha de Nacimiento
            OutlinedTextField(
                value = fechaNacimiento,
                onValueChange = { fechaNacimiento = it },
                label = { Text("Fecha de Nacimiento (DD/MM/AAAA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Latitud y Longitud (readOnly)
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

            Spacer(modifier = Modifier.height(4.dp))

            // Botón de Registro
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
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text("💾 Registrar Alumno", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
