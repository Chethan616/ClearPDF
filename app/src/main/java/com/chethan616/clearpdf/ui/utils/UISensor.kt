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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The gravity source behind every glass surface's specular rim ([HighlightStyle.Default]'s angle).
 *
 * Listens only while the hosting lifecycle is RESUMED. Inside the NavHost that lifecycle is the
 * back-stack entry's, so the sensor is off while a screen is in the back stack, while its enter or
 * exit transition is still running, and while the app is in the background — previously it kept
 * firing (and invalidating every glass panel) from first composition until the screen was disposed.
 */
@Composable
fun rememberUISensor(): UISensor {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiSensor = remember { UISensor(context) }
    // Settings -> Appearance -> "Reduce glass motion" (#53): with it on, the sensor simply never
    // starts, so gravityAngle holds its fixed default and every liquidGlassPanel/capsule that reads
    // it in draw stops re-publishing (and re-running blur+lens) on tilt. Off by default -- full glass.
    val reducedMotion = com.chethan616.clearpdf.ui.theme.LocalReducedGlassMotion.current
    DisposableEffect(lifecycleOwner, uiSensor, reducedMotion) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!reducedMotion) uiSensor.start()
                Lifecycle.Event.ON_PAUSE -> uiSensor.stop()
                else -> Unit
            }
        }
        // addObserver replays the events up to the current state, so an already-resumed screen
        // starts listening immediately.
        lifecycleOwner.lifecycle.addObserver(observer)
        if (reducedMotion) uiSensor.stop()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            uiSensor.stop()
        }
    }
    return uiSensor
}

/**
 * Why this is more than a raw `atan2`:
 *
 * [gravityAngle] is read in the *draw* phase of every `liquidGlassPanel`, search pill and capsule
 * menu, and each write re-records that surface and re-runs its blur + lens on the GPU — plus the
 * header and tab bar that sample the content under them. The old filter (alpha 0.5, 0.35 deg
 * threshold) let ordinary hand tremor through on nearly every sample, so an idle Home or Tools
 * screen re-rendered all of its glass ~15 times a second. Worse, on a phone lying flat the in-plane
 * gravity is pure noise, `atan2` of noise is a random angle, and the highlight spun continuously.
 *
 * Now: the angle is only derived when the device is tilted enough for it to mean something, it is
 * filtered along the shortest arc (no 359 -> 0 sweep), and it is published only once it has moved
 * past a [ANGLE_DEADBAND_DEG] dead band. A steady hand or a phone on a desk publishes nothing; a
 * deliberate tilt still sweeps the highlight round smoothly.
 */
class UISensor(context: Context) {
    private companion object {
        /** Low-pass weight of each new sample. Lower = steadier, slower to follow a tilt. */
        const val SMOOTHING_ALPHA = 0.25f
        /** The published angle only moves once the filtered one has drifted this far. */
        const val ANGLE_DEADBAND_DEG = 1.5f
        /** Below this in-plane gravity (m/s^2, ~9 deg from flat) the angle is noise: hold it. */
        const val MIN_PLANAR_GRAVITY = 1.5f
        const val GRAVITY_DELTA_THRESHOLD = 0.02f
        const val RAD_TO_DEG = (180.0 / PI).toFloat()
    }

    var gravityAngle: Float by mutableFloatStateOf(45f)
        private set
    var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    /** The filtered angle, tracked privately so only dead-band crossings reach snapshot state. */
    private var filteredAngle = 45f
    private var filteredGravity = Offset.Zero
    private var listening = false

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
            val x = event.values[0]
            val y = event.values[1]

            val planar = sqrt(x * x + y * y)
            if (planar >= MIN_PLANAR_GRAVITY) {
                val raw = atan2(y, x) * RAD_TO_DEG
                filteredAngle = wrap(filteredAngle + wrap(raw - filteredAngle) * SMOOTHING_ALPHA)
                if (abs(wrap(filteredAngle - gravityAngle)) >= ANGLE_DEADBAND_DEG) {
                    gravityAngle = filteredAngle
                }
            }

            val norm = sqrt(x * x + y * y + 9.81f * 9.81f).coerceAtLeast(0.001f)
            filteredGravity = filteredGravity * (1f - SMOOTHING_ALPHA) + Offset(x / norm, y / norm) * SMOOTHING_ALPHA
            val dx = filteredGravity.x - gravity.x
            val dy = filteredGravity.y - gravity.y
            if (dx * dx + dy * dy >= GRAVITY_DELTA_THRESHOLD * GRAVITY_DELTA_THRESHOLD) {
                gravity = filteredGravity
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (listening || accelerometer == null) return
        listening = sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(listener)
        listening = false
    }

    /** Maps any angle in degrees onto (-180, 180]. */
    private fun wrap(deg: Float): Float {
        var d = deg % 360f
        if (d > 180f) d -= 360f
        if (d <= -180f) d += 360f
        return d
    }
}
