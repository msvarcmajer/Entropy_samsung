package hr.ferit.entropy_samsung.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import hr.ferit.entropy_samsung.presentation.theme.Entropy_samsungTheme
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private val targetSensors = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_STEP_COUNTER,
        Sensor.TYPE_STEP_DETECTOR,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_PRESSURE
    )

    private var collecting = false
    private val sensorData = mutableMapOf<Int, MutableList<FloatArray>>()
    private var entryIndex = 1
    private lateinit var csvFile: File
    private var lastShakeTime = 0L
    private var updateCounter: ((Int) -> Unit)? = null
    private val dataLock = Any()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        moveOldCsvFiles()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        initCsvFile()
        setupUi()
        registerAccelerometer()
        startAutoLoop()
    }

    private fun initCsvFile() {
        val timestamp = System.currentTimeMillis()
        val formatter = java.text.SimpleDateFormat("HHmm", java.util.Locale.getDefault())
        val timeString = formatter.format(java.util.Date(timestamp))
        csvFile = File(filesDir, "entropy_$timeString.csv").apply {
            appendText("measurement_id,timestamp,sensor_type,lsb_bits,key_hash\n")
        }
    }

    private fun setupUi() {
        setContent {
            Entropy_samsungTheme {
                var counter by rememberSaveable { mutableStateOf(0) }
                updateCounter = { counter = it }

                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Measurement ${counter + 1}\nShake to start!",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.title3,
                            modifier = Modifier.padding(8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Collected: $counter",
                            style = MaterialTheme.typography.caption1
                        )
                    }
                }
            }
        }
    }

    private fun registerAccelerometer() {
        Log.d("SENSOR", "Registering ACCELEROMETER for shake detection")
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: Log.e("SENSOR", "ACCELEROMETER not available")
    }

    private fun registerSensors() {
        Log.d("SENSOR", "Registering full sensor set for data collection")
        targetSensors.forEach { type ->
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                Log.d("SENSOR", "Registering sensor: ${sensor.name} (type=$type)")
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            } else {
                Log.w("SENSOR", "Sensor type=$type is not available on device")
            }
        }
    }
    
    private fun startAutoLoop() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (!collecting) {
                    startCollectionCycle()
                }
                delay(2500L) // 2.5 sekunde između početaka
            }
        }
    }
/* Skupljanje podataka vremenski */
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (collecting) {
            synchronized(dataLock) {
                sensorData.getOrPut(event.sensor.type) { mutableListOf() }
                    .add(event.values.copyOf())
                Log.d("DATA", "Collected data from sensor type=${event.sensor.type}, values=${event.values.joinToString()} ")
            }
        }
    }
/* Skupljanje nakon shakea
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return

        if (!collecting && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val magnitude = sqrt(event.values.fold(0f) { acc, v -> acc + v * v }.toDouble())

            Log.d("SHAKE", "Detected magnitude=$magnitude")
            if (magnitude > 8 && System.currentTimeMillis() - lastShakeTime > 3000) {
                lastShakeTime = System.currentTimeMillis()
                startCollectionCycle()
            }
        } else if (collecting) {
            synchronized(dataLock) {
                sensorData.getOrPut(event.sensor.type) { mutableListOf() }.add(event.values.copyOf())
                Log.d("DATA", "Collected data from sensor type=${event.sensor.type}, values=${event.values.joinToString()} ")
            }
        }
    } */

    private fun startCollectionCycle() {
        Log.i("COLLECT", "Starting data collection cycle")
        collecting = true
        sensorData.clear()
        registerSensors()
        CoroutineScope(Dispatchers.IO).launch {
            delay(2200L)
            stopCollectionCycle()
        }
    }

    private fun stopCollectionCycle() {
        Log.i("COLLECT", "Stopping data collection cycle")
        collecting = false
        targetSensors.forEach { sensorManager.unregisterListener(this) }

        CoroutineScope(Dispatchers.IO).launch {
            val processedData = processSensorData()
            val keyHash = generateKeyHash(processedData)
            writeToCsv(processedData, keyHash)
            withContext(Dispatchers.Main) {
                entryIndex++
                updateCounter?.invoke(entryIndex)
                Log.i("RESULT", "Measurement #$entryIndex done, hash=$keyHash")
                registerAccelerometer()
            }
        }
    }

    private fun processSensorData(): String {
        Log.d("PROCESS", "Processing collected sensor data")
        return synchronized(dataLock) {
            sensorData.flatMap { (type, values) ->
                Log.d("PROCESS", "Sensor type=$type, samples=${values.size}")
                values.flatMap { it.map { value ->
                    ((value * 1e6).toInt() and 0xFF).toString(2).padStart(8, '0')
                } }
            }.joinToString("")
        }
    }

    private fun generateKeyHash(data: String): String {
        Log.d("HASH", "Generating SHA-256 hash")
        return MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeToCsv(data: String, keyHash: String) {
        Log.d("CSV", "Writing result to CSV")
        FileWriter(csvFile, true).use { writer ->
            writer.append("$entryIndex,${System.currentTimeMillis()},${data.take(64)},$keyHash\n")
            writer.flush()
        }
    }
    private fun moveOldCsvFiles() {
        try {
            val internalDir = filesDir
            val exportDir = File(getExternalFilesDir(null), "export")
            exportDir.mkdirs()

            internalDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".csv")) {
                    val dest = File(exportDir, file.name)
                    file.copyTo(dest, overwrite = true)
                    Log.i("EXPORT", "Copied ${file.name} to export directory")
                }
            }
        } catch (e: Exception) {
            Log.e("EXPORT", "Failed to move CSV files: ${e.message}")
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onStop() {
        super.onStop()
        Log.w("LIFECYCLE", "MainActivity is stopping!")
    }
}

