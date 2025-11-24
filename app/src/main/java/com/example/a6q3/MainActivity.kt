package com.example.a6q3

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.a6q3.ui.theme.A6Q3Theme
import kotlin.math.abs
import kotlin.math.log10

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // Target Android 14 (API 34)
class MainActivity : ComponentActivity() {

    private var soundMeter: SoundMeter? = null
    private var micPermissionState: MutableState<Boolean>? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // update compose state when the user answers
            micPermissionState?.value = granted
            if (granted) startMeter {}
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            A6Q3Theme {
                val hasPermission = remember { mutableStateOf(hasMicPermission()) }
                micPermissionState = hasPermission

                var dbLevel by remember { mutableStateOf(0f) }
                var isRunning by remember { mutableStateOf(false) }
                val threshold = 85f

                LaunchedEffect(hasPermission.value, isRunning) {
                    if (hasPermission.value && isRunning) {
                        startMeter { db ->
                            dbLevel = db
                        }
                    } else {
                        stopMeter()
                        dbLevel = 0f
                    }
                }

                MeterScreen(
                    hasPermission = hasPermission.value,
                    dbLevel = dbLevel,
                    threshold = threshold,
                    isRunning = isRunning,
                    onToggle = { isRunning = !isRunning },
                    onAskPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeter()
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // start listening and send dB values back
    private fun startMeter(onLevel: (Float) -> Unit) {
        if (soundMeter == null) {
            soundMeter = SoundMeter()
        }
        soundMeter?.start(onLevel)
    }

    // stop the recorder
    private fun stopMeter() {
        soundMeter?.stop()
    }
}

class SoundMeter {
    private val sampleRate = 44100
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private var audioRecord: AudioRecord? = null
    private var running = false
    private var worker: Thread? = null

    // basic AudioRecord loop, converts amplitude to dB
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start(onLevel: (Float) -> Unit) {
        if (running) return
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        audioRecord?.startRecording()
        running = true
        worker = Thread {
            val buffer = ShortArray(bufferSize)
            while (running) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    val maxAmp = buffer.take(read)
                        .map { abs(it.toInt()) }
                        .maxOrNull()
                        ?.toFloat() ?: 0f
                    val safeAmp = if (maxAmp <= 0f) 1f else maxAmp
                    val db = 20f * log10(safeAmp)
                    onLevel(db)
                }
                try {
                    Thread.sleep(100)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        worker?.start()
    }

    // release resources
    fun stop() {
        running = false
        worker?.interrupt()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        worker = null
    }
}

@Composable
fun MeterScreen(
    hasPermission: Boolean,
    dbLevel: Float,
    threshold: Float,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onAskPermission: () -> Unit
) {
    val clamped = dbLevel.coerceIn(0f, 120f)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sound Meter", style = MaterialTheme.typography.headlineSmall)
        Text(text = "Current dB: ${clamped.toInt()} dB")

        SoundBar(level = clamped, threshold = threshold)

        if (!hasPermission) {
            Button(onClick = onAskPermission) {
                Text(text = "Allow Microphone")
            }
        } else {
            Button(onClick = onToggle) {
                Text(if (isRunning) "Stop" else "Start")
            }
        }

        if (dbLevel > threshold) {
            Text(text = "Too loud! Lower the volume.", color = Color.Red)
        }
    }
}

@Composable
fun SoundBar(level: Float, threshold: Float) {
    val percent = (level / 120f).coerceIn(0f, 1f)
    val barColor = if (level > threshold) Color.Red else Color(0xFF4CAF50)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percent)
                    .background(barColor, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MeterScreenPreview() {
    A6Q3Theme {
        MeterScreen(
            hasPermission = true,
            dbLevel = 72f,
            threshold = 85f,
            isRunning = true,
            onToggle = {},
            onAskPermission = {}
        )
    }
}
