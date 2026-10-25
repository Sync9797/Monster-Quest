package com.monsterquest.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Bestiary
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Shrine
import com.monsterquest.core.WildSpawn
import com.monsterquest.core.World
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

private sealed interface MapPick {
    data class ShrinePick(val shrine: Shrine) : MapPick
    data class SpawnPick(val spawn: WildSpawn) : MapPick
}

private fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun MapScreen(vm: GameViewModel) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🗺️", fontSize = 56.sp)
            Text(
                "Explore the real world! Visit shrines at parks, landmarks and other places for loot, " +
                    "challenge boss shrines, and battle monsters roaming nearby.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
        return
    }

    TrackLocation(context, vm::onLocation)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = System.currentTimeMillis()
        }
    }

    val loc = vm.location
    val spawns = vm.spawnsNear(now)
    val shrines = vm.shrines
    var pick by remember { mutableStateOf<MapPick?>(null) }
    var centered by remember { mutableStateOf(false) }

    val mapView = rememberMapView()
    val icons = remember { HashMap<String, BitmapDrawable>() }
    fun markerIcon(key: String, emoji: String, ring: Int) =
        icons.getOrPut(key) { emojiDrawable(context, emoji, ring) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { mv ->
                    mv.overlays.clear()
                    if (loc != null) {
                        val here = GeoPoint(loc.first, loc.second)
                        if (!centered) {
                            mv.controller.setCenter(here)
                            centered = true
                        }
                        // Reach circle for shrines.
                        mv.overlays.add(
                            Polygon(mv).apply {
                                setPoints(Polygon.pointsAsCircle(here, World.SHRINE_RANGE_M))
                                fillPaint.color = 0x228B5CF6
                                outlinePaint.color = 0x888B5CF6.toInt()
                                outlinePaint.strokeWidth = 3f
                            },
                        )
                    }
                    shrines.forEach { sh ->
                        val rec = vm.state.shrines[sh.id]
                        val cooling = GameEngine.shrineCooldownLeft(vm.state, sh, now) > 0
                        val emoji = when {
                            sh.isBoss && rec?.bossDefeated == true -> "🏯"
                            sh.isBoss -> "👹"
                            cooling -> "🪨"
                            else -> "⛩️"
                        }
                        val ring = when {
                            sh.isBoss -> 0xCCEF4444.toInt()
                            cooling -> 0x88666666.toInt()
                            else -> 0xCC8B5CF6.toInt()
                        }
                        mv.overlays.add(
                            Marker(mv).apply {
                                position = GeoPoint(sh.lat, sh.lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setIcon(markerIcon("shrine_${emoji}_$ring", emoji, ring))
                                title = sh.name
                                setOnMarkerClickListener { _, _ -> pick = MapPick.ShrinePick(sh); true }
                            },
                        )
                    }
                    spawns.forEach { sp ->
                        mv.overlays.add(
                            Marker(mv).apply {
                                position = GeoPoint(sp.lat, sp.lon)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setIcon(markerIcon("spawn_${sp.speciesId}", sp.species.emoji, 0xAA1E1A2B.toInt()))
                                title = sp.species.name
                                setOnMarkerClickListener { _, _ -> pick = MapPick.SpawnPick(sp); true }
                            },
                        )
                    }
                    if (loc != null) {
                        mv.overlays.add(
                            Marker(mv).apply {
                                position = GeoPoint(loc.first, loc.second)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                setIcon(markerIcon("player", "🧙", 0xCCF5C542.toInt()))
                                setOnMarkerClickListener { _, _ -> true }
                            },
                        )
                    }
                    mv.invalidate()
                },
            )
            if (loc == null) {
                Text(
                    "Finding your location…",
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    vm.loadingPlaces -> "Scanning for shrines…"
                    else -> "⛩️ ${shrines.count { !it.isBoss }}  👹 ${shrines.count { it.isBoss }}  🐾 ${spawns.size} nearby"
                },
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = {
                loc?.let { mapView.controller.animateTo(GeoPoint(it.first, it.second)) }
            }) { Text("📍 Center") }
        }
        Text(
            "© OpenStreetMap contributors",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
        )
    }

    when (val p = pick) {
        is MapPick.ShrinePick -> ShrineDialog(vm, p.shrine, now) { pick = null }
        is MapPick.SpawnPick -> SpawnDialog(vm, p.spawn) { pick = null }
        null -> {}
    }
}

@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDetach()
        }
    }
    return mapView
}

@SuppressLint("MissingPermission")
@Composable
private fun TrackLocation(context: Context, onLocation: (Double, Double) -> Unit) {
    DisposableEffect(Unit) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location.latitude, location.longitude)

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        try {
            providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
                ?.let { onLocation(it.latitude, it.longitude) }
            providers.forEach { lm.requestLocationUpdates(it, 4_000L, 5f, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            // Permission revoked while running; the screen will ask again.
        }
        onDispose { lm.removeUpdates(listener) }
    }
}

/** Renders an emoji on a colored circle as a map marker. */
private fun emojiDrawable(context: Context, emoji: String, ringColor: Int): BitmapDrawable {
    val size = (44 * context.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, ring)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.58f
        textAlign = Paint.Align.CENTER
    }
    val y = size / 2f - (text.descent() + text.ascent()) / 2
    canvas.drawText(emoji, size / 2f, y, text)
    return BitmapDrawable(context.resources, bmp)
}

private fun distanceText(m: Double?): String = when {
    m == null -> "unknown distance"
    m < 1000 -> "${m.toInt()} m away"
    else -> String.format(java.util.Locale.US, "%.1f km away", m / 1000)
}

@Composable
private fun ShrineDialog(vm: GameViewModel, shrine: Shrine, now: Long, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(shrine.lat, shrine.lon)
    val inRange = dist != null && dist <= World.SHRINE_RANGE_M
    val rec = vm.state.shrines[shrine.id]
    val cooldown = GameEngine.shrineCooldownLeft(vm.state, shrine, now)
    val boss = shrine.bossSpeciesId?.let { Bestiary.get(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (shrine.isBoss) "👹 " else "⛩️ ") + shrine.name) },
        text = {
            Column {
                Text(distanceText(dist), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (boss != null) {
                    Text("Boss shrine", color = BossRed, fontWeight = FontWeight.Bold)
                    Text("Guardian: ${boss.emoji} ${boss.name} (${boss.element.emoji} ${boss.element.label})")
                    Text("Level ${GameEngine.bossLevel(vm.state.hero.level)} • Acts twice per round • Double HP")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (rec?.bossDefeated == true) "✅ Defeated. You may now try to tame it!"
                        else "🔒 Defeat the guardian once to unlock taming.",
                    )
                } else {
                    Text("Visit for gold, XP, items and a hunt token.")
                }
                if (cooldown > 0) Text("Recharging: ${formatDuration(cooldown)}", color = HpYellow)
                if (!inRange) {
                    Text(
                        "Walk within ${World.SHRINE_RANGE_M.toInt()} m to interact.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (shrine.isBoss) {
                TextButton(onClick = { vm.challengeBoss(shrine); onDismiss() }, enabled = inRange && cooldown == 0L) {
                    Text(if (rec?.bossDefeated == true) "Tame boss" else "Challenge")
                }
            } else {
                TextButton(onClick = { vm.visitShrine(shrine); onDismiss() }, enabled = inRange && cooldown == 0L) {
                    Text("Visit")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SpawnDialog(vm: GameViewModel, spawn: WildSpawn, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(spawn.lat, spawn.lon)
    val inRange = dist != null && dist <= World.SPAWN_RANGE_M
    val sp = spawn.species
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${sp.emoji} Wild ${sp.name}") },
        text = {
            Column {
                Text("Level ${spawn.level} • ${sp.element.emoji} ${sp.element.label} • ${sp.rarity.label}", color = rarityColor(sp.rarity))
                Text(distanceText(dist), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Leaves in ${formatDuration(spawn.expiresAtMs - System.currentTimeMillis())}")
                if (!inRange) Text("Walk within ${World.SPAWN_RANGE_M.toInt()} m to battle.", modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.battleSpawn(spawn); onDismiss() }, enabled = inRange) { Text("Battle!") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
