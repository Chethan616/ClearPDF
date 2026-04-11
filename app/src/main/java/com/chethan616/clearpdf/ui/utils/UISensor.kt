package com.chethan616.clearpdf.ui.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun rememberUISensor(): UISensor {
    val context = LocalContext.current
    val uiSensor = remember { UISensor(context) }
    DisposableEffect(Unit) {
        uiSensor.start()
        onDispose { uiSensor.stop() }
    }
    return uiSensor
}

class UISensor(context: Context) {
    private companion object {
        const val SMOOTHING_ALPHA = 0.5f
        const val ANGLE_DELTA_THRESHOLD_DEG = 0.35f
        const val GRAVITY_DELTA_THRESHOLD = 0.004f
    }

    var gravityAngle: Float by mutableFloatStateOf(45f)
        private set
    var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val norm = sqrt(x * x + y * y + 9.81f * 9.81f).coerceAtLeast(0.001f)

                val rawAngle = atan2(y, x) * (180f / PI).toFloat()
                val filteredAngle = gravityAngle * (1f - SMOOTHING_ALPHA) + rawAngle * SMOOTHING_ALPHA
                if (abs(filteredAngle - gravityAngle) >= ANGLE_DELTA_THRESHOLD_DEG) {
                    gravityAngle = filteredAngle
                }

                val normalizedGravity = Offset(x / norm, y / norm)
                val filteredGravity = gravity * (1f - SMOOTHING_ALPHA) + normalizedGravity * SMOOTHING_ALPHA
                val dx = filteredGravity.x - gravity.x
                val dy = filteredGravity.y - gravity.y
                if (dx * dx + dy * dy >= GRAVITY_DELTA_THRESHOLD * GRAVITY_DELTA_THRESHOLD) {
                    gravity = filteredGravity
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
    }
}
