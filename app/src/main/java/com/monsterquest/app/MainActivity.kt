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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.monsterquest.core.StepSource
import com.monsterquest.core.FrameRate
import com.monsterquest.app.focus.FocusService
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
        vm.refreshHealthStatus()
        vm.refreshHealthSteps()
    }

    override fun onPause() {
        stopStepCounting()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        if (vm.state.focus != null) FocusService.appReturned(this)
    }

    /**
     * Leaving for another app during a phone-free session fails it (after a
     * short grace). Turning the screen off is fine: the timer keeps going.
     */
    override fun onStop() {
        val screenOn = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive
        if (vm.state.focus != null && screenOn && !isChangingConfigurations) FocusService.appLeft(this)
        super.onStop()
    }

    /**
     * The step counter keeps counting in hardware while the app is closed, so
     * reading it whenever the app is open captures all walking since last time.
     */
    private fun startStepCounting() {
        if (vm.state.settings.stepSource != StepSource.PHONE) {
            stopStepCounting()
            return
        }
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

    /** Picks the display mode closest to (not above) the requested refresh rate, at the current resolution. */
    private fun applyFrameRate(rate: FrameRate) {
        @Suppress("DEPRECATION")
        val screen = (if (Build.VERSION.SDK_INT >= 30) display else null) ?: windowManager.defaultDisplay
        val attrs = window.attributes
        val target = rate.hz
        if (target == null) {
            attrs.preferredDisplayModeId = 0
            attrs.preferredRefreshRate = 0f
        } else {
            val current = screen.mode
            val best = screen.supportedModes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .filter { it.refreshRate <= target + 1f }
                .maxByOrNull { it.refreshRate } ?: current
            attrs.preferredDisplayModeId = best.modeId
            attrs.preferredRefreshRate = best.refreshRate
        }
        window.attributes = attrs
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // OpenStreetMap tile servers require an identifying user agent.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }

        // Health Connect has no live updates, so poll it while the game is open.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(60_000)
                    vm.refreshHealthSteps()
                    startStepCounting()
                }
            }
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
            // Ask for the chosen refresh rate; re-applied on fold/unfold (each screen has its own modes).
            val rate = vm.state.settings.frameRate
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            androidx.compose.runtime.LaunchedEffect(rate, vm.batterySaver, config.screenWidthDp, config.screenHeightDp) {
                applyFrameRate(if (vm.batterySaver) FrameRate.STANDARD else rate)
            }
            CompositionLocalProvider(LocalFold provides fold) {
                MonsterQuestTheme {
                    AppRoot(vm)
                }
            }
        }
    }
}
