# 📱 Guía Maestra para el Examen: Móvil II (Univalle)

Esta guía te prepara para resolver el examen en las computadoras de la facultad con **dos estrategias a prueba de fallos**:
1. **Opción 1: Repositorio en GitHub** (para clonar directo si no hay restricciones de red).
2. **Opción 2: Copia y Pega desde WhatsApp Web** (la más segura: creas un proyecto nuevo desde cero en la PC del lab y solo pegas los archivos).

---

## ⚠️ ¿Por qué suelen fallar los proyectos importados de GitHub en la facultad?

1. **`local.properties`:** Guarda la ruta `sdk.dir=C:\Users\TuNombre\AppData\...`. Si se sube a GitHub y lo abres en otra PC, Android Studio no encuentra el SDK y se cuelga. *(En nuestro repo ya está ignorado en `.gitignore`)*.
2. **Versión de Gradle:** Si el proyecto usa una versión de Gradle que la máquina del lab no tiene descargada y no hay buen internet, la importación se congela.
3. **`google-services.json`:** Si no se incluye en el repo o no coincide con el `applicationId`, Firebase rechaza la app.

---

## 🚀 OPCIÓN 1: Subir tu Repositorio a GitHub

El proyecto actual en tu laptop ya está compilado, probado (**BUILD SUCCESSFUL**) y listo en Git.

### 1. En tu laptop (ahora mismo):
1. Entra a [github.com/new](https://github.com/new) y crea un repositorio (ejemplo: `examen-movil-2`, ponlo en **Público** o **Privado**).
2. En la terminal de esta carpeta, ejecuta:
```bash
git remote add origin https://github.com/TU_USUARIO/TU_REPO.git
git branch -M main
git push -u origin main
```

### 2. En la PC de la Facultad:
1. Abre Android Studio.
2. Si aparece la pantalla de bienvenida: **Get from VCS** $\rightarrow$ pega la URL de tu repositorio.
3. Si ya hay un proyecto abierto: **File > New > Project from Version Control...**
4. Espera a que termine el sync de Gradle.

---

## 🛡️ OPCIÓN 2: El Método WhatsApp Web (100% Seguro)

Si al importar el repositorio te sale error de Gradle o desconfías del monitoreo, este método **nunca falla** porque el proyecto se genera con las versiones nativas de la PC del laboratorio.

### Paso 1: Enviar a tu WhatsApp (Mensajes Guardados)
Envía a tu propio chat de WhatsApp:
1. El archivo adjunto [google-services.json](file:///c:/univalle/Movil%20II/examen/app/google-services.json) como documento.
2. Los bloques de código que están a continuación.

---

### Paso 2: En la PC del laboratorio (Crear el Proyecto)
1. Abre Android Studio $\rightarrow$ **New Project** $\rightarrow$ **Empty Views Activity** (con XML).
2. **DATOS CLAVE DEL PROYECTO:**
   - **Name:** `Examen`
   - **Package name:** `com.univalle.nube_base_de_datos` *(¡Muy importante para que coincida con Firebase!)*
   - **Language:** `Kotlin`
   - **Minimum SDK:** `API 26` (Android 8.0)
3. Descarga `google-services.json` desde WhatsApp Web y pégalo dentro de la carpeta `app/` del proyecto.

---

### Paso 3: Configurar Gradle (Solo 2 archivos)

#### 1. `build.gradle.kts` (Proyecto / Raíz)
En el bloque `plugins`, agrega el plugin de Google:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

#### 2. `app/build.gradle.kts` (Módulo App)
Agrega el plugin arriba y la dependencia abajo:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

// ... dentro de dependencies { ... }
dependencies {
    // ... las que ya vienen ...
    implementation("com.google.firebase:firebase-firestore:25.1.1")
}
```
*(Haz clic en **Sync Now** en la esquina superior derecha).*

---

### Paso 4: Permisos en `AndroidManifest.xml`
Agrega estas dos líneas antes de `<application>`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

---

### Paso 5: Los Archivos de Código (Copiar y Pegar)

#### 📄 Archivo 1: `Medicion.kt`
*(Click derecho en el paquete `com.univalle.nube_base_de_datos` > New > Kotlin Class/File > File)*
```kotlin
package com.univalle.nube_base_de_datos

data class Medicion(
    var id: Long = 0,
    var firestoreId: String = "",
    val tipoSensor: String = "",
    val valor: Double = 0.0,
    val fechaHora: String = "",
    var isSynced: Int = 0 // 0 = local/pendiente, 1 = sincronizado en la nube
)
```

---

#### 📄 Archivo 2: `DatabaseHelper.kt` (1 Sola Tabla en SQLite)
```kotlin
package com.univalle.nube_base_de_datos

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "examen_sensores.db", null, 1) {

    companion object {
        const val TABLE_MEDICIONES = "mediciones"
        const val COL_ID = "id"
        const val COL_FIRESTORE_ID = "firestore_id"
        const val COL_TIPO_SENSOR = "tipo_sensor"
        const val COL_VALOR = "valor"
        const val COL_FECHA_HORA = "fecha_hora"
        const val COL_IS_SYNCED = "is_synced"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val query = """
            CREATE TABLE $TABLE_MEDICIONES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FIRESTORE_ID TEXT,
                $COL_TIPO_SENSOR TEXT NOT NULL,
                $COL_VALOR REAL NOT NULL,
                $COL_FECHA_HORA TEXT NOT NULL,
                $COL_IS_SYNCED INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(query)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEDICIONES")
        onCreate(db)
    }

    fun insertarMedicion(medicion: Medicion): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_FIRESTORE_ID, medicion.firestoreId)
            put(COL_TIPO_SENSOR, medicion.tipoSensor)
            put(COL_VALOR, medicion.valor)
            put(COL_FECHA_HORA, medicion.fechaHora)
            put(COL_IS_SYNCED, medicion.isSynced)
        }
        return db.insert(TABLE_MEDICIONES, null, values)
    }

    fun obtenerTodas(): List<Medicion> {
        val lista = mutableListOf<Medicion>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_MEDICIONES ORDER BY $COL_ID DESC", null)
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Medicion(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        tipoSensor = it.getString(it.getColumnIndexOrThrow(COL_TIPO_SENSOR)),
                        valor = it.getDouble(it.getColumnIndexOrThrow(COL_VALOR)),
                        fechaHora = it.getString(it.getColumnIndexOrThrow(COL_FECHA_HORA)),
                        isSynced = it.getInt(it.getColumnIndexOrThrow(COL_IS_SYNCED))
                    )
                )
            }
        }
        return lista
    }

    fun obtenerNoSincronizadas(): List<Medicion> {
        val lista = mutableListOf<Medicion>()
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_MEDICIONES WHERE $COL_IS_SYNCED = 0 ORDER BY $COL_ID ASC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Medicion(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        firestoreId = it.getString(it.getColumnIndexOrThrow(COL_FIRESTORE_ID)) ?: "",
                        tipoSensor = it.getString(it.getColumnIndexOrThrow(COL_TIPO_SENSOR)),
                        valor = it.getDouble(it.getColumnIndexOrThrow(COL_VALOR)),
                        fechaHora = it.getString(it.getColumnIndexOrThrow(COL_FECHA_HORA)),
                        isSynced = 0
                    )
                )
            }
        }
        return lista
    }

    fun marcarComoSincronizado(localId: Long, firestoreId: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_IS_SYNCED, 1)
            put(COL_FIRESTORE_ID, firestoreId)
        }
        db.update(TABLE_MEDICIONES, values, "$COL_ID = ?", arrayOf(localId.toString()))
    }

    fun contarPendientes(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_MEDICIONES WHERE $COL_IS_SYNCED = 0",
            null
        )
        var total = 0
        cursor.use {
            if (it.moveToFirst()) {
                total = it.getInt(0)
            }
        }
        return total
    }
}
```

---

#### 📄 Archivo 3: `NetworkHelper.kt` (Verificación de Red)
```kotlin
package com.univalle.nube_base_de_datos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object NetworkHelper {
    fun hayConexion(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
```

---

#### 📄 Archivo 4: `FirestoreManager.kt` (Cloud Firestore)
```kotlin
package com.univalle.nube_base_de_datos

import com.google.firebase.firestore.FirebaseFirestore

class FirestoreManager {
    private val db = FirebaseFirestore.getInstance()

    fun subirMedicion(
        medicion: Medicion,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val data = hashMapOf(
            "tipoSensor" to medicion.tipoSensor,
            "valor" to medicion.valor,
            "fechaHora" to medicion.fechaHora,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("mediciones")
            .add(data)
            .addOnSuccessListener { docRef -> onSuccess(docRef.id) }
            .addOnFailureListener { e -> onFailure(e) }
    }
}
```

---

#### 📄 Archivo 5: `MedicionAdapter.kt`
```kotlin
package com.univalle.nube_base_de_datos

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MedicionAdapter(private var lista: List<Medicion>) :
    RecyclerView.Adapter<MedicionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSensor: TextView = view.findViewById(R.id.tv_item_sensor)
        val tvEstado: TextView = view.findViewById(R.id.tv_item_estado)
        val tvValor: TextView = view.findViewById(R.id.tv_item_valor)
        val tvFecha: TextView = view.findViewById(R.id.tv_item_fecha)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        holder.tvSensor.text = "Sensor: ${item.tipoSensor} (#${item.id})"
        holder.tvValor.text = String.format("Magnitud: %.2f", item.valor)
        holder.tvFecha.text = item.fechaHora

        if (item.isSynced == 1) {
            holder.tvEstado.text = "☁️ Nube"
            holder.tvEstado.setTextColor(Color.parseColor("#2E7D32"))
            holder.tvEstado.setBackgroundColor(Color.parseColor("#E8F5E9"))
        } else {
            holder.tvEstado.text = "⏳ Local"
            holder.tvEstado.setTextColor(Color.parseColor("#E65100"))
            holder.tvEstado.setBackgroundColor(Color.parseColor("#FFF3E0"))
        }
    }

    override fun getItemCount(): Int = lista.size

    fun actualizarLista(nuevaLista: List<Medicion>) {
        this.lista = nuevaLista
        notifyDataSetChanged()
    }
}
```

---

#### 📄 Archivo 6: `res/layout/item_medicion.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="8dp"
    android:layout_marginVertical="4dp"
    app:cardCornerRadius="10dp"
    app:cardElevation="2dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="12dp">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <TextView
                android:id="@+id/tv_item_sensor"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Sensor: Acelerómetro"
                android:textSize="15sp"
                android:textStyle="bold"
                android:textColor="#212121" />

            <TextView
                android:id="@+id/tv_item_estado"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:paddingHorizontal="8dp"
                android:paddingVertical="4dp"
                android:text="☁️ Nube"
                android:textSize="12sp"
                android:textStyle="bold" />
        </LinearLayout>

        <TextView
            android:id="@+id/tv_item_valor"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:text="Valor: 9.81"
            android:textSize="14sp"
            android:textColor="#424242" />

        <TextView
            android:id="@+id/tv_item_fecha"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:text="Fecha: 18/11/2026 05:30:00"
            android:textSize="12sp"
            android:textColor="#757575" />

    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

---

#### 📄 Archivo 7: `res/layout/activity_main.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#F5F5F5"
    tools:context=".MainActivity">

    <!-- Indicador de Red -->
    <LinearLayout
        android:id="@+id/layout_estado_red"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="#2E7D32"
        android:gravity="center"
        android:padding="8dp">

        <TextView
            android:id="@+id/tv_estado_red"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="🟢 ESTADO: ONLINE"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:textSize="13sp" />
    </LinearLayout>

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="12dp">

            <!-- Card del Sensor -->
            <com.google.android.material.card.MaterialCardView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginBottom="12dp"
                app:cardCornerRadius="12dp"
                app:cardElevation="3dp">

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="16dp">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="📡 Sensor en Vivo (Acelerómetro)"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:textColor="#1976D2" />

                    <TextView
                        android:id="@+id/tv_sensor_valores"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:text="X: 0.00 | Y: 0.00 | Z: 0.00"
                        android:textSize="14sp"
                        android:textColor="#333333" />

                    <TextView
                        android:id="@+id/tv_sensor_magnitud"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="4dp"
                        android:text="Magnitud: 9.81 m/s²"
                        android:textSize="16sp"
                        android:textStyle="bold"
                        android:textColor="#000000" />
                </LinearLayout>
            </com.google.android.material.card.MaterialCardView>

            <!-- Botones -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:layout_marginBottom="12dp">

                <Button
                    android:id="@+id/btn_registrar"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginEnd="6dp"
                    android:text="💾 Guardar" />

                <Button
                    android:id="@+id/btn_sincronizar"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:layout_marginStart="6dp"
                    android:text="🔄 Sync (0)" />
            </LinearLayout>

            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginBottom="8dp"
                android:text="📋 Mediciones en SQLite y Firestore:"
                android:textStyle="bold"
                android:textSize="14sp"
                android:textColor="#424242" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rv_mediciones"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:nestedScrollingEnabled="false" />

        </LinearLayout>
    </ScrollView>
</LinearLayout>
```

---

#### 📄 Archivo 8: `MainActivity.kt`
```kotlin
package com.univalle.nube_base_de_datos

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

    private lateinit var layoutEstadoRed: LinearLayout
    private lateinit var tvEstadoRed: TextView
    private lateinit var tvSensorValores: TextView
    private lateinit var tvSensorMagnitud: TextView
    private lateinit var btnRegistrar: Button
    private lateinit var btnSincronizar: Button
    private lateinit var rvMediciones: RecyclerView

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var valorMagnitudActual: Double = 9.81

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

        dbHelper = DatabaseHelper(this)
        firestoreManager = FirestoreManager()

        layoutEstadoRed = findViewById(R.id.layout_estado_red)
        tvEstadoRed = findViewById(R.id.tv_estado_red)
        tvSensorValores = findViewById(R.id.tv_sensor_valores)
        tvSensorMagnitud = findViewById(R.id.tv_sensor_magnitud)
        btnRegistrar = findViewById(R.id.btn_registrar)
        btnSincronizar = findViewById(R.id.btn_sincronizar)
        rvMediciones = findViewById(R.id.rv_mediciones)

        adapter = MedicionAdapter(emptyList())
        rvMediciones.layoutManager = LinearLayoutManager(this)
        rvMediciones.adapter = adapter

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        btnRegistrar.setOnClickListener { registrarMedicion() }
        btnSincronizar.setOnClickListener { sincronizarPendientes() }

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

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            valorMagnitudActual = sqrt((x * x + y * y + z * z).toDouble())
            tvSensorValores.text = String.format(Locale.US, "X: %.2f | Y: %.2f | Z: %.2f", x, y, z)
            tvSensorMagnitud.text = String.format(Locale.US, "Magnitud: %.2f m/s²", valorMagnitudActual)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun registrarMedicion() {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val fecha = sdf.format(Date())

        val nuevaMedicion = Medicion(
            tipoSensor = "Acelerómetro",
            valor = valorMagnitudActual,
            fechaHora = fecha,
            isSynced = 0
        )

        // 1. Guardar en SQLite local
        val idLocal = dbHelper.insertarMedicion(nuevaMedicion)
        nuevaMedicion.id = idLocal

        // 2. Si hay conexión, subir a Firestore de inmediato
        if (NetworkHelper.hayConexion(this)) {
            firestoreManager.subirMedicion(
                medicion = nuevaMedicion,
                onSuccess = { firestoreId ->
                    dbHelper.marcarComoSincronizado(idLocal, firestoreId)
                    Toast.makeText(this, "Guardado y sincronizado online ☁️", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                },
                onFailure = {
                    Toast.makeText(this, "Guardado localmente (Error en nube)", Toast.LENGTH_SHORT).show()
                    recargarDatos()
                }
            )
        } else {
            Toast.makeText(this, "Guardado localmente (Modo Offline) 📱", Toast.LENGTH_SHORT).show()
            recargarDatos()
        }
    }

    private fun sincronizarPendientes() {
        if (!NetworkHelper.hayConexion(this)) {
            Toast.makeText(this, "No hay conexión a internet ❌", Toast.LENGTH_SHORT).show()
            return
        }

        val pendientes = dbHelper.obtenerNoSincronizadas()
        if (pendientes.isEmpty()) {
            Toast.makeText(this, "Todo está sincronizado ✔️", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "Sincronizados $subidos registros ☁️", Toast.LENGTH_SHORT).show()
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
        val pendientes = dbHelper.contarPendientes()
        btnSincronizar.text = "🔄 Sync ($pendientes)"
    }

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
            layoutEstadoRed.setBackgroundColor(Color.parseColor("#2E7D32"))
            tvEstadoRed.text = "🟢 ESTADO: ONLINE (Conexión activa)"
        } else {
            layoutEstadoRed.setBackgroundColor(Color.parseColor("#C62828"))
            tvEstadoRed.text = "🔴 ESTADO: OFFLINE (Sin internet)"
        }
    }
}
```
