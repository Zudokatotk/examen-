package com.example.examen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
}

// ─────────────────────────────────────────────────────────────────────────────
// PUNTO 7: Formulario de Alumno en Jetpack Compose
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistroAlumnoScreen(
    onRegistrarAlumno: (Alumno) -> Unit
) {
    // Estados para cada campo del formulario
    var ci by remember { mutableStateOf("") }
    var nombres by remember { mutableStateOf("") }
    var apellidos by remember { mutableStateOf("") }
    var fechaNacimiento by remember { mutableStateOf("") }
    
    // Latitud y Longitud (Inicialmente en 0.0 o cargados por sensor)
    var latitud by remember { mutableStateOf("0.0") }
    var longitud by remember { mutableStateOf("0.0") }

    Scaffold(
        topBar = {
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
            Text(
                text = "Datos del Estudiante",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D47A1)
            )

            // Campo: CI
            OutlinedTextField(
                value = ci,
                onValueChange = { ci = it },
                label = { Text("Cédula de Identidad (CI)") },
                placeholder = { Text("Ej: 8392102") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Campo: Nombres
            OutlinedTextField(
                value = nombres,
                onValueChange = { nombres = it },
                label = { Text("Nombres") },
                placeholder = { Text("Ej: Juan Carlos") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Campo: Apellidos
            OutlinedTextField(
                value = apellidos,
                onValueChange = { apellidos = it },
                label = { Text("Apellidos") },
                placeholder = { Text("Ej: Pérez Gomez") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            // Campo: Fecha de Nacimiento
            OutlinedTextField(
                value = fechaNacimiento,
                onValueChange = { fechaNacimiento = it },
                label = { Text("Fecha de Nacimiento") },
                placeholder = { Text("DD/MM/AAAA (Ej: 15/04/2001)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Coordenadas GPS (Sensor)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF455A64)
            )

            // Fila con Latitud y Longitud (readOnly = true tal como pide el examen)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Latitud
                OutlinedTextField(
                    value = latitud,
                    onValueChange = {},
                    readOnly = true, // No editable por el usuario
                    label = { Text("Latitud") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFECEFF1),
                        unfocusedContainerColor = Color(0xFFECEFF1)
                    )
                )

                // Longitud
                OutlinedTextField(
                    value = longitud,
                    onValueChange = {},
                    readOnly = true, // No editable por el usuario
                    label = { Text("Longitud") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFECEFF1),
                        unfocusedContainerColor = Color(0xFFECEFF1)
                    )
                )
            }

            Text(
                text = "ℹ️ Lat y Long se cargarán automáticamente con el sensor.",
                fontSize = 11.sp,
                color = Color(0xFF78909C)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de Registro
            Button(
                onClick = {
                    val nuevoAlumno = Alumno(
                        ci = ci.trim(),
                        nombres = nombres.trim(),
                        apellidos = apellidos.trim(),
                        fechaNacimiento = fechaNacimiento.trim(),
                        latitud = latitud.toDoubleOrNull() ?: 0.0,
                        longitud = longitud.toDoubleOrNull() ?: 0.0
                    )
                    onRegistrarAlumno(nuevoAlumno)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
            ) {
                Text(
                    "💾 Registrar Alumno",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
