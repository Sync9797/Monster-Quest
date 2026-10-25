package com.monsterquest.app

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.monsterquest.app.ui.AppRoot
import com.monsterquest.app.ui.FoldInfo
import com.monsterquest.app.ui.LocalFold
import com.monsterquest.app.ui.MonsterQuestTheme
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()
    private var fold by mutableStateOf<FoldInfo?>(null)

    private var stepListener: SensorEventListener? = null

    override fun onResume() {
        super.onResume()
        vm.onAppResumed()
        startStepCounting()
    }

    override fun onPause() {
        stopStepCounting()
        super.onPause()
    }

    /**
     * The step counter keeps counting in hardware while the app is closed, so
     * reading it whenever the app is open captures all walking since last time.
     */
    private fun startStepCounting() {
        val sm = getSystemService(SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val permitted = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        vm.stepSensorAvailable = sensor != null && permitted
        if (sensor == null || !permitted || stepListener != null) return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                vm.onStepCount(event.values[0].toLong())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        stepListener = listener
    }

    private fun stopStepCounting() {
        stepListener?.let { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(it) }
        stepListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenStreetMap tile servers require an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        // Track the hinge on foldables so layouts can avoid it and use
        // tabletop / book postures.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { info ->
                        fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.let {
                            FoldInfo(
                                isSeparating = it.isSeparating,
                                isVertical = it.orientation == FoldingFeature.Orientation.VERTICAL,
                                bounds = it.bounds,
                            )
                        }
                    }
            }
        }

        enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme {
                    AppRoot(vm)
                }
            }
        }
    }
}
