package com.monsterquest.app.ui

import com.monsterquest.app.ui.art.ArtQuality
import com.monsterquest.app.Haptics
import com.monsterquest.app.ui.art.BeaconState
import com.monsterquest.app.ui.art.drawMonsterStanding
import com.monsterquest.app.ui.art.drawPlayerOn
import com.monsterquest.app.ui.art.drawShrineOn
import com.monsterquest.app.ui.art.drawVillainOn
import com.monsterquest.app.ui.art.spriteDrawable
import com.monsterquest.app.ui.art.HeroOutfit
import com.monsterquest.app.ui.art.MonsterArt
import com.monsterquest.app.ui.art.ThugArt
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.app.ui.art.color
import com.monsterquest.app.ui.art.icon
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.GIcon
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewOutlineProvider
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import com.monsterquest.app.ui.art.drawDenOn
import com.monsterquest.core.Den
import com.monsterquest.core.Dens
import com.monsterquest.app.ui.art.drawFocusPin
import com.monsterquest.app.data.StreetLookup
import com.monsterquest.app.Navigation
import androidx.compose.runtime.produceState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.monsterquest.app.GameViewModel
import com.monsterquest.core.Bestiary
import com.monsterquest.core.GameEngine
import com.monsterquest.core.Schedule
import com.monsterquest.core.Shrine
import com.monsterquest.core.ShrineTier
import com.monsterquest.core.WildSpawn
import com.monsterquest.core.Item
import com.monsterquest.core.VillainCamp
import com.monsterquest.core.VillainTier
import com.monsterquest.core.Villains
import com.monsterquest.core.World
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

/** Nothing farther than this (10 miles) shows in the Nearby list. */
private const val MAX_NEARBY_M = 16_093.0

/** A spot picked from the Nearby list: the map pans there and a card offers directions. */
private class MapFocus(
    val lat: Double,
    val lon: Double,
    val title: String,
    val sub: String,
    /** Drop a temporary pin (monsters, which may not be drawn on the map yet). */
    val pin: Boolean,
    val details: () -> Unit,
)

private sealed interface MapPick {
    data class ShrinePick(val shrine: Shrine) : MapPick
    data class SpawnPick(val spawn: WildSpawn) : MapPick
    data class VillainPick(val camp: VillainCamp) : MapPick
    data class TrackedPick(val spawn: WildSpawn) : MapPick
    data class DenPick(val den: Den) : MapPick
}

internal fun hasLocationPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
            GIcon(GameIcon.MAP, Modifier.size(64.dp), Gold)
            Text(
                "Explore the real world! Item shrines at cafes, restaurants and parks hold weapons, " +
                    "spell pages, orbs and potions. Silver shrines (Tue/Thu) and Gold shrines (weekends) hold guardians " +
                    "that unlock when you keep up with your quests. " +
                    "Monsters roam nearby too.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Button(onClick = {
                launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }) { Text("Enable location") }
        }
        return
    }

    TrackLocation(context, vm.state.settings.batterySaverGps || vm.batterySaver, vm::onLocation)
    LaunchedEffect(Unit) { vm.onMapOpened() }
    // Secret walking encounters only happen while the map is open.
    DisposableEffect(Unit) {
        vm.mapVisible = true
        onDispose { vm.mapVisible = false }
    }
    vm.secretAmbush?.let { SecretAmbushPopup(vm, it) }

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            // Monsters creep closer over time, so refresh often.
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val loc = vm.location
    // Only monsters within the reveal distance stand on the map; farther ones show in the tracker.
    val spawns = vm.visibleSpawns(now)
    val tracked = vm.trackedSpawns(now)
    val shrines = vm.shrines
    val villains = vm.villainsNear(now)
    val dens = vm.densNear(now)
    var pick by remember { mutableStateOf<MapPick?>(null) }
    var focus by remember { mutableStateOf<MapFocus?>(null) }
    // The pin and card are temporary: they clear themselves after a few minutes.
    LaunchedEffect(focus) {
        if (focus != null) {
            delay(5 * 60_000L)
            focus = null
        }
    }

    // Battery saver: turn the phone upside down (e.g. hanging at your side or in a
    // pocket) and the screen goes dark; turn it back upright to return.
    if (vm.state.settings.flipBatterySaver) UpsideDownSaver(vm)

    // Buzz when a monster comes within battle range (also while the battery saver
    // screen is on), and let aggressive ones that reach you ambush.
    LaunchedEffect(spawns.map { it.id to (vm.distanceTo(it.lat, it.lon)?.toInt() ?: 0) / 20 }, loc) {
        if (loc == null) return@LaunchedEffect
        val fresh = vm.newSpawnsNearby(spawns)
        if (vm.checkAmbush(spawns, System.currentTimeMillis())) {
            if (vm.state.settings.vibrateOnSpawn) Haptics.ambush(context)
        } else if (fresh.isNotEmpty() && vm.state.settings.vibrateOnSpawn) {
            Haptics.spawn(context)
        }
    }
    // The map follows the hero; looking at something from the Nearby list leaves it there briefly.
    var following by remember { mutableStateOf(true) }

    val mapView = rememberMapView()
    val darkMap = when (vm.state.settings.mapStyle) {
        com.monsterquest.core.MapStyle.AUTO -> isDarkTheme(vm.state.settings.themeMode)
        com.monsterquest.core.MapStyle.LIGHT -> false
        com.monsterquest.core.MapStyle.DARK -> true
    }
    LaunchedEffect(darkMap) { applyMapStyle(mapView, darkMap) }
    val icons = remember { HashMap<String, BitmapDrawable>() }
    fun sprite(key: String, wDp: Int, hDp: Int, draw: (android.graphics.Canvas, Float, Float) -> Unit) =
        icons.getOrPut("$key:${ArtQuality.reduced}") { spriteDrawable(context, wDp, hDp, draw) }

    // Phones show the tracker bar over the map; big screens list those monsters in Nearby instead.
    val mapPane: @Composable (Boolean) -> Unit = { showTracker ->
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                AndroidView(
                    factory = {
                        // The same MapView moves between layouts when the phone folds or unfolds.
                        (mapView.parent as? ViewGroup)?.removeView(mapView)
                        mapView
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { mv ->
                        mv.overlays.clear()
                        if (loc != null) {
                            val here = GeoPoint(loc.first, loc.second)
                            if (following) mv.controller.setCenter(here)
                            // Reach circle for shrines.
                            mv.overlays.add(
                                Polygon(mv).apply {
                                    setPoints(Polygon.pointsAsCircle(here, World.SHRINE_RANGE_M))
                                    fillPaint.color = 0x228B5CF6
                                    outlinePaint.color = 0x888B5CF6.toInt()
                                    outlinePaint.strokeWidth = 3f
                                    // No (empty) info bubble when the circle is tapped; let the tap through.
                                    infoWindow = null
                                    setOnClickListener { _, _, _ -> false }
                                },
                            )
                        }
                        shrines.forEach { sh ->
                            val rec = vm.state.shrines[sh.id]
                            val cooling = GameEngine.shrineCooldownLeft(vm.state, sh, now) > 0
                            val open = Schedule.access(vm.state, sh, now).open
                            val beacon = when {
                                sh.isBoss && !open -> BeaconState.LOCKED
                                cooling -> BeaconState.COOLING
                                else -> BeaconState.OPEN
                            }
                            // Boss shrines stand taller so they're easy to spot from afar.
                            val (bw, bh) = if (sh.isBoss) 60 to 110 else 48 to 88
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(sh.lat, sh.lon)
                                    setAnchor(Marker.ANCHOR_CENTER, 0.945f)
                                    setIcon(sprite("shrine_${sh.tier}_$beacon", bw, bh) { c, w, h -> drawShrineOn(c, w, h, sh.tier, beacon) })
                                    title = sh.name
                                    setOnMarkerClickListener { _, _ -> pick = MapPick.ShrinePick(sh); true }
                                },
                            )
                        }
                        spawns.forEach { sp ->
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(sp.lat, sp.lon)
                                    // Monsters stand on the map themselves, feet at their spot.
                                    setAnchor(Marker.ANCHOR_CENTER, 0.95f)
                                    setIcon(sprite("spawn_${sp.speciesId}_${sp.lured}_${sp.shiny}", 58, 58) { c, w, h -> drawMonsterStanding(c, w, h, sp.species, sp.lured, sp.shiny) })
                                    title = sp.species.name
                                    setOnMarkerClickListener { _, _ -> pick = MapPick.SpawnPick(sp); true }
                                },
                            )
                        }
                        dens.forEach { den ->
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(den.lat, den.lon)
                                    setAnchor(Marker.ANCHOR_CENTER, 0.93f)
                                    setIcon(sprite("den_${den.species.element}", 56, 48) { c, w, h -> drawDenOn(c, w, h, den.species.element) })
                                    title = den.name
                                    setOnMarkerClickListener { _, _ -> pick = MapPick.DenPick(den); true }
                                },
                            )
                        }
                        villains.forEach { v ->
                            val isVillain = v.tier == VillainTier.VILLAIN
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(v.lat, v.lon)
                                    setAnchor(Marker.ANCHOR_CENTER, 0.955f)
                                    setIcon(sprite("villain_$isVillain", 46, 69) { c, w, h -> drawVillainOn(c, w, h, isVillain) })
                                    title = v.leader
                                    setOnMarkerClickListener { _, _ -> pick = MapPick.VillainPick(v); true }
                                },
                            )
                        }
                        if (loc != null) {
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(loc.first, loc.second)
                                    setAnchor(Marker.ANCHOR_CENTER, 0.86f)
                                    val outfit = HeroOutfit.of(vm.state.equippedGear, vm.state.equippedArmor, vm.state.equippedPieces)
                                    setIcon(sprite("player_${vm.state.hero.heroClass}_$outfit", 60, 60) { c, w, h -> drawPlayerOn(c, w, h, vm.state.hero.heroClass, outfit) })
                                    setOnMarkerClickListener { _, _ -> true }
                                },
                            )
                        }
                        focus?.takeIf { it.pin }?.let { f ->
                            mv.overlays.add(
                                Marker(mv).apply {
                                    position = GeoPoint(f.lat, f.lon)
                                    setAnchor(Marker.ANCHOR_CENTER, 1f)
                                    setIcon(sprite("focus_pin", 30, 40) { c, w, h -> drawFocusPin(c, w, h) })
                                    setOnMarkerClickListener { _, _ -> f.details(); true }
                                },
                            )
                        }
                        mv.invalidate()
                    },
                )
                if (showTracker) TrackerBar(vm, tracked, Modifier.align(Alignment.TopCenter)) { pick = MapPick.TrackedPick(it) }
                focus?.let { f -> FocusCard(vm, f, Modifier.align(Alignment.BottomCenter)) { focus = null } }
                if (loc == null) {
                    Text(
                        "Finding your location…",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            SpawnInfoRow(vm, now)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (vm.loadingPlaces) {
                    Text("Scanning for shrines…", fontSize = 13.sp, modifier = Modifier.weight(1f))
                } else {
                    // What's around, as small pills that wrap cleanly instead of one long line.
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        MapCount("${spawns.size} near")
                        MapCount("${tracked.size} tracked")
                        MapCount("${shrines.count { it.tier == ShrineTier.ITEM }} item · ${shrines.count { it.tier == ShrineTier.SILVER }} silver · ${shrines.count { it.tier == ShrineTier.GOLD }} gold")
                        MapCount("${dens.size} dens")
                        if (villains.isNotEmpty()) MapCount("${villains.size} foes")
                    }
                }
                if (!following) {
                    Column(horizontalAlignment = Alignment.End) {
                        OutlinedButton(onClick = {
                            following = true
                            loc?.let { mapView.controller.animateTo(GeoPoint(it.first, it.second)) }
                        }) { IconText(GameIcon.PIN, "Back to me") }
                    }
                }
            }
            Text(
                "© OpenStreetMap contributors",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
            )
        }
    }

    fun focusOn(f: MapFocus) {
        focus = f
        following = false
        mapView.controller.animateTo(GeoPoint(f.lat, f.lon))
    }
    // After a look at something in the Nearby list, the map glides back to the hero.
    LaunchedEffect(following, focus) {
        if (!following) {
            kotlinx.coroutines.delay(LOOK_AWAY_MS)
            following = true
            loc?.let { mapView.controller.animateTo(GeoPoint(it.first, it.second)) }
        }
    }
    val imperial = vm.state.settings.imperialUnits
    AdaptivePanes(
        firstWeight = 0.62f,
        compact = { mapPane(true) },
        first = { mapPane(false) },
        second = {
            NearbyList(
                vm = vm,
                shrines = shrines,
                spawns = spawns + tracked,
                villains = villains,
                dens = dens,
                onDen = { den ->
                    focusOn(MapFocus(den.lat, den.lon, den.name, "Lv ${den.monster.level} ${den.species.name} • strong", pin = false) { pick = MapPick.DenPick(den) })
                },
                onShrine = { sh ->
                    focusOn(MapFocus(sh.lat, sh.lon, sh.name, sh.subtitle ?: sh.tier.label, pin = false) { pick = MapPick.ShrinePick(sh) })
                },
                onSpawn = { sp ->
                    val seen = sp.speciesId in vm.state.seen
                    val near = sp in spawns
                    focusOn(
                        MapFocus(
                            sp.lat, sp.lon,
                            if (seen) "Wild ${sp.species.name}" else "Unknown monster",
                            "Lv ${sp.level} • " + distanceText(vm.distanceTo(sp.lat, sp.lon), imperial),
                            pin = true,
                        ) { pick = if (near) MapPick.SpawnPick(sp) else MapPick.TrackedPick(sp) },
                    )
                },
                onVillain = { v ->
                    focusOn(MapFocus(v.lat, v.lon, v.leader, "${v.tier.label} of the ${v.crew}", pin = false) { pick = MapPick.VillainPick(v) })
                },
            )
        },
    )

    when (val p = pick) {
        is MapPick.ShrinePick -> ShrineDialog(vm, p.shrine, now) { pick = null }
        is MapPick.SpawnPick -> SpawnDialog(vm, p.spawn) { pick = null }
        is MapPick.VillainPick -> VillainDialog(vm, p.camp) { pick = null }
        is MapPick.DenPick -> DenDialog(vm, p.den) { pick = null }
        is MapPick.TrackedPick -> TrackedDialog(
            vm,
            p.spawn,
            onShowArea = {
                following = false
                mapView.controller.animateTo(GeoPoint(p.spawn.lat, p.spawn.lon))
            },
        ) { pick = null }
        null -> {}
    }
}

/** Time of day, how much you've been moving, and the lure. */
@Composable
private fun SpawnInfoRow(vm: GameViewModel, now: Long) {
    val cond = vm.spawnConditions(now)
    val tod = cond.timeOfDay
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${tod.label}: ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                tod.favored.forEach { e ->
                    GIcon(e.icon(), Modifier.size(14.dp), e.color())
                    Text(" ${e.label}  ", fontSize = 12.sp, color = e.color())
                }
            }
            val pct = (cond.activity * 100).toInt()
            Text(
                if (pct >= 5) "On the move: monsters hear you and close in fast (+$pct% spawns, rarer)"
                else "Standing still: curious monsters still find you. Walk to draw them in faster.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (cond.lure) {
            IconText(GameIcon.LURE, formatDuration(vm.state.lureUntilMs - now), tint = Color(0xFFF2A198), fontSize = 12.sp)
        } else {
            SmallButton("Lure (${vm.state.count(Item.LURE)})", vm::useLure, enabled = vm.state.count(Item.LURE) > 0, outlined = true)
        }
    }
}

/** How long the map stays on something picked from the Nearby list before returning to the hero. */
private const val LOOK_AWAY_MS = 10_000L

/** A small rounded count under the map ("8 near", "3 dens"...). */
@Composable
private fun MapCount(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Dark map: the OpenStreetMap tiles inverted and turned back to their own
 * hues (parks stay green, water blue), a little softer, plus a dark
 * loading grid so the map never flashes white.
 */
private val DarkTiles = android.graphics.ColorMatrixColorFilter(
    android.graphics.ColorMatrix().apply {
        // Invert...
        set(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
        // ...rotate hue 180 degrees so colors come back...
        postConcat(android.graphics.ColorMatrix(floatArrayOf(
            -0.574f, 1.43f, 0.144f, 0f, 0f,
            0.426f, 0.43f, 0.144f, 0f, 0f,
            0.426f, 1.43f, -0.856f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
        // ...then soften the contrast and dim slightly, with a hint of the game's teal.
        postConcat(android.graphics.ColorMatrix(floatArrayOf(
            0.82f, 0f, 0f, 0f, 10f,
            0f, 0.86f, 0f, 0f, 14f,
            0f, 0f, 0.88f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f,
        )))
    },
)

private fun applyMapStyle(map: MapView, dark: Boolean) {
    val tiles = map.overlayManager.tilesOverlay
    tiles.setColorFilter(if (dark) DarkTiles else null)
    // The filter also covers the loading grid, so light loading colors come out dark on the dark map.
    tiles.setLoadingBackgroundColor(0xFFE8E4DC.toInt())
    tiles.setLoadingLineColor(0xFFD8D3CA.toInt())
    map.invalidate()
}

@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            // osmdroid draws tiles past its own edges; clip it to its bounds so it
            // can't paint over the nav rail, headers or the status row.
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
            // The map moves between layouts when the phone folds or unfolds. By default
            // osmdroid tears itself down when detached from a window, which broke (and
            // crashed) the map on every fold; we clean it up ourselves on dispose instead.
            setDestroyMode(false)
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(17.0)
            // Close enough to see the neighbourhood, never so far the hero gets lost.
            minZoomLevel = 12.0
            maxZoomLevel = 20.0
            // Locked to the hero like Pokemon GO: pinch, double-tap, or double-tap-and-slide to zoom; no panning.
            MapGestures(this).attach()
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
internal fun TrackLocation(context: Context, batterySaver: Boolean = false, onLocation: (Double, Double, Float?) -> Unit) {
    DisposableEffect(batterySaver) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) =
                onLocation(location.latitude, location.longitude, if (location.hasSpeed()) location.speed else null)

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
                ?.let { onLocation(it.latitude, it.longitude, null) }
            val (interval, distance) = if (batterySaver) 20_000L to 25f else 4_000L to 5f
            providers.forEach { lm.requestLocationUpdates(it, interval, distance, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            // Permission revoked while running; the screen will ask again.
        }
        onDispose { lm.removeUpdates(listener) }
    }
}


/** A monster leapt out while you walked: a long buzz, "Ambushed!", then the fight starts on its own. */
@Composable
private fun SecretAmbushPopup(vm: GameViewModel, group: List<com.monsterquest.core.Monster>) {
    val context = LocalContext.current
    LaunchedEffect(group) {
        if (vm.state.settings.vibrateOnSpawn) Haptics.secretAmbush(context)
        delay(2_200)
        vm.startSecretAmbush()
    }
    val sp = group.first().species
    val shake = rememberInfiniteTransition(label = "ambush").animateFloat(
        -4f, 4f, infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "shake",
    )
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                "AMBUSHED!",
                color = HpRed,
                fontWeight = FontWeight.Black,
                fontSize = 30.sp,
                modifier = Modifier.fillMaxWidth().offset(x = shake.value.dp),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val thug = vm.secretThug
                if (thug != null) {
                    // A thug and their crew: one monster for each of yours.
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                        ThugArt(thug.cls, Modifier.size(96.dp))
                        group.forEach { m -> MonsterArt(m.species, Modifier.size(64.dp), shiny = m.shiny) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${thug.title} of the ${thug.crew} jumps you!", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(
                        "Lv ${thug.level} ${thug.kind.lowercase()} • ${group.size} monster${if (group.size == 1) "" else "s"}, one for each of yours • they fight together",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                Row(horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
                    group.forEach { m -> MonsterArt(m.species, Modifier.size(if (group.size == 1) 120.dp else 90.dp), shiny = m.shiny) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (group.size == 1) "A wild ${sp.name} leaps out in front of you!"
                    else "${group.size} wild ${sp.name}s leap out in front of you!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Lv ${group.first().level} • ${sp.element.label}" + if (group.size > 1) " • each one weaker as a pack" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                }
                if (group.any { it.shiny }) Text("Shiny!", color = Gold, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { Button(onClick = vm::startSecretAmbush) { Text("Fight!") } },
    )
}

/** The street at a point: the map data's own when it has one, otherwise asked of the phone's geocoder. */
@Composable
private fun rememberStreet(lat: Double, lon: Double, known: String? = null): String? {
    val context = LocalContext.current
    val street by produceState(known ?: StreetLookup.cached(lat, lon), lat, lon, known) {
        if (known == null) value = StreetLookup.streetAt(context, lat, lon)
    }
    return street
}

/** A dialog title with the real-world place underneath, e.g. "Barnes & Noble on Wards Road". */
@Composable
private fun PlaceTitle(icon: GameIcon, title: String, color: Color, subtitle: String?) {
    Column {
        IconText(icon, title, tint = color, size = 20.dp, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 26.dp, top = 2.dp))
        }
    }
}

/** Opens walking directions to a point in the phone's map app. */
@Composable
private fun WalkHereButton(lat: Double, lon: Double, label: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { Navigation.walkTo(context, lat, lon, label) }, modifier = Modifier.padding(top = 8.dp)) {
        GIcon(GameIcon.WALK, Modifier.size(16.dp), Gold)
        Spacer(Modifier.width(6.dp))
        Text("Walk here")
    }
}

/** Where a Nearby pick is: the map has panned there; walk there or see more. */
@Composable
private fun FocusCard(vm: GameViewModel, f: MapFocus, modifier: Modifier, onClose: () -> Unit) {
    val dist = vm.distanceTo(f.lat, f.lon)
    val here = vm.location
    val dir = here?.let { World.compass(World.bearing(it.first, it.second, f.lat, f.lon)) }
    Column(
        modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .border(1.dp, Gold, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(f.sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dist != null && dir != null) {
                    Text("${formatDistance(dist, vm.state.settings.imperialUnits)} to the $dir", fontSize = 12.sp, color = Gold)
                }
            }
            TextButton(onClick = onClose) { Text("Close") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WalkHereButton(f.lat, f.lon, f.title)
            OutlinedButton(onClick = f.details, modifier = Modifier.padding(top = 8.dp)) { Text("Details") }
        }
    }
}

/** "At Barnes & Noble" / "Near Central Park (120 ft from it)". */
private fun landmarkText(place: Shrine, gap: Double, imperial: Boolean): String {
    val where = place.place ?: place.name
    return if (gap < 40) "At $where" else "Near $where (${formatDistance(gap, imperial)} from it)"
}

private fun distanceText(m: Double?, imperial: Boolean): String =
    if (m == null) "unknown distance" else "${formatDistance(m, imperial)} away"

@Composable
private fun ShrineDialog(vm: GameViewModel, shrine: Shrine, now: Long, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(shrine.lat, shrine.lon)
    val inRange = dist != null && dist <= World.SHRINE_RANGE_M
    val rec = vm.state.shrines[shrine.id]
    val cooldown = GameEngine.shrineCooldownLeft(vm.state, shrine, now)
    val guardian = shrine.bossSpeciesId?.let { Bestiary.get(it) }
    val access = Schedule.access(vm.state, shrine, now)
    val street = rememberStreet(shrine.lat, shrine.lon, shrine.street)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(shrine.tier.icon(), shrine.name, shrine.tier.color(), shrine.copy(street = street).subtitle) },
        text = {
            Column {
                Text("${shrine.tier.label} • ${distanceText(dist, vm.state.settings.imperialUnits)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when (shrine.tier) {
                    ShrineTier.ITEM -> Text("Weapons, spell-book pages, capture orbs and potions.")
                    ShrineTier.SILVER -> if (guardian != null) {
                        GuardianPreview(guardian)
                        Text("Level ${GameEngine.silverLevel(vm.state.hero.level)} • 1.5× HP • Acts twice per round")
                        Text("At least 3 perfect stats • Harder to tame", color = SilverColor)
                    }
                    ShrineTier.GOLD -> if (guardian != null) {
                        GuardianPreview(guardian)
                        Text("Level ${GameEngine.bossLevel(vm.state.hero.level)} • Double HP • Acts twice per round")
                        Text(
                            if (rec?.bossDefeated == true) "Defeated. You may now try to tame it!"
                            else "Defeat the guardian once to unlock taming.",
                        )
                    }
                }
                if (shrine.isBoss) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        if (!access.open) {
                            GIcon(GameIcon.LOCK, Modifier.size(14.dp).padding(top = 2.dp), HpYellow)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(access.reason, color = if (access.open) HpGreen else HpYellow, fontSize = 13.sp)
                    }
                }
                if (cooldown > 0) Text("Recharging: ${formatDuration(cooldown)}", color = HpYellow)
                if (!inRange) {
                    Text(
                        "Walk within ${formatDistance(World.SHRINE_RANGE_M, vm.state.settings.imperialUnits)} to interact.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    WalkHereButton(shrine.lat, shrine.lon, shrine.place ?: shrine.name)
                }
            }
        },
        confirmButton = {
            if (shrine.isBoss) {
                TextButton(onClick = { vm.challengeBoss(shrine); onDismiss() }, enabled = inRange && cooldown == 0L && access.open) {
                    Text(
                        when {
                            shrine.tier == ShrineTier.SILVER -> "Challenge"
                            rec?.bossDefeated == true -> "Tame boss"
                            else -> "Challenge"
                        },
                    )
                }
            } else {
                TextButton(onClick = { vm.visitShrine(shrine); onDismiss() }, enabled = inRange && cooldown == 0L) {
                    Text("Collect")
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
    val landmark = vm.landmarkNear(spawn.lat, spawn.lon)
    val street = rememberStreet(spawn.lat, spawn.lon)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Wild ${sp.name}")
                val where = landmark?.let { (place, gap) -> landmarkText(place, gap, vm.state.settings.imperialUnits) }
                    ?: street?.let { "On $it" }
                if (where != null) Text(where, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            val pack = vm.packFor(spawn)
            Column {
                MonsterArt(sp, Modifier.size(110.dp).align(Alignment.CenterHorizontally), shiny = spawn.shiny)
                if (spawn.shiny) IconText(GameIcon.SPARK, "Shiny! A rare color variant.", tint = Gold, textColor = Gold, bold = true, fontSize = 13.sp)
                Text("Level ${spawn.level} • ${sp.element.label} • ${sp.rarity.label}", color = rarityColor(sp.rarity))
                if (spawn.follower) Text("It followed your footsteps.", color = HpGreen, fontSize = 13.sp)
                Text(distanceText(dist, vm.state.settings.imperialUnits), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Leaves in ${formatDuration(spawn.expiresAtMs - System.currentTimeMillis())}")
                if (pack.isNotEmpty()) {
                    Text(
                        "Watch out: ${pack.joinToString { it.species.name }} nearby will join the fight as a pack " +
                            "(${pack.size + 1} vs your team, bigger rewards).",
                        color = HpYellow,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (!inRange) {
                    Text("Walk within ${formatDistance(World.SPAWN_RANGE_M, vm.state.settings.imperialUnits)} to battle.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(spawn.lat, spawn.lon, "Wild ${sp.name}")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.battleSpawn(spawn); onDismiss() }, enabled = inRange) { Text("Battle!") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Like Pokémon GO's "Nearby": monsters you can sense but not yet see, nearest
 * first, with footprints for distance (one is close, three is far) and the
 * direction to walk. Walking draws them toward you.
 */
@Composable
private fun TrackerBar(vm: GameViewModel, tracked: List<WildSpawn>, modifier: Modifier = Modifier, onSelect: (WildSpawn) -> Unit) {
    if (tracked.isEmpty()) return
    val here = vm.location ?: return
    val imperial = vm.state.settings.imperialUnits
    Row(
        modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GIcon(GameIcon.EYE, Modifier.size(16.dp), Gold)
        tracked.take(8).forEach { sp ->
            val d = World.distanceMeters(here.first, here.second, sp.lat, sp.lon)
            val dir = World.compass(World.bearing(here.first, here.second, sp.lat, sp.lon))
            val seen = sp.speciesId in vm.state.seen
            Column(Modifier.clickable { onSelect(sp) }, horizontalAlignment = Alignment.CenterHorizontally) {
                MonsterArt(sp.species, Modifier.size(34.dp), silhouette = !seen)
                Text(formatDistance(d, imperial), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text(dir, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** More about a monster sensed by the tracker: where it is, what it's near, and what it's doing. */
@Composable
private fun TrackedDialog(vm: GameViewModel, spawn: WildSpawn, onShowArea: () -> Unit, onDismiss: () -> Unit) {
    val here = vm.location
    val imperial = vm.state.settings.imperialUnits
    val seen = spawn.speciesId in vm.state.seen
    val sp = spawn.species
    val d = here?.let { World.distanceMeters(it.first, it.second, spawn.lat, spawn.lon) }
    val dir = here?.let { World.compass(World.bearing(it.first, it.second, spawn.lat, spawn.lon)) }
    val landmark = vm.landmarkNear(spawn.lat, spawn.lon)
    val moving = vm.spawnConditions(System.currentTimeMillis()).activity >= 0.05
    val street = rememberStreet(spawn.lat, spawn.lon)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (seen) "${sp.name} nearby" else "Something is out there") },
        text = {
            Column {
                MonsterArt(sp, Modifier.size(96.dp).align(Alignment.CenterHorizontally), silhouette = !seen)
                if (seen) {
                    IconText(sp.element.icon(), "${sp.element.label} • ${sp.rarity.label}", tint = sp.element.color(), fontSize = 13.sp)
                } else {
                    Text("You've never met this kind of monster. Find it to add it to your Journal.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (d != null && dir != null) {
                    IconText(GameIcon.COMPASS, "About ${formatDistance(d, imperial)} to the $dir", tint = Gold, textColor = Gold, bold = true, fontSize = 15.sp)
                }
                if (landmark != null) {
                    val (place, gap) = landmark
                    IconText(place.tier.icon(), landmarkText(place, gap, imperial), tint = place.tier.color(), fontSize = 13.sp)
                    val placeStreet = place.street ?: street
                    if (placeStreet != null) Text("On $placeStreet", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (street != null) {
                    Text("On $street, away from any landmark.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Not near any landmark: open ground.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (moving) "It heard you walking and is closing in fast." else "It's slowly wandering your way. Walk toward it to meet it sooner.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Leaves in ${formatDuration(spawn.expiresAtMs - System.currentTimeMillis())}. " +
                        "It shows on the map within ${formatDistance(World.REVEAL_M, imperial)}; battle it within ${formatDistance(World.SPAWN_RANGE_M, imperial)}.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                WalkHereButton(spawn.lat, spawn.lon, if (seen) sp.name else "Monster")
            }
        },
        confirmButton = { TextButton(onClick = { onShowArea(); onDismiss() }) { Text("Show area") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** A den: a strong monster to beat, then 5-10 throws to tame it. */
@Composable
private fun DenDialog(vm: GameViewModel, den: Den, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(den.lat, den.lon)
    val inRange = dist != null && dist <= Dens.RANGE_M
    val imperial = vm.state.settings.imperialUnits
    val landmark = vm.landmarkNear(den.lat, den.lon)
    val street = rememberStreet(den.lat, den.lon)
    val where = landmark?.let { (place, gap) -> landmarkText(place, gap, imperial) } ?: street?.let { "On $it" }
    val m = den.monster
    val seen = m.speciesId in vm.state.seen
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.PAW, den.name, den.species.element.color(), where) },
        text = {
            Column {
                MonsterArt(den.species, Modifier.size(110.dp).align(Alignment.CenterHorizontally), shiny = m.shiny, silhouette = !seen)
                Text(
                    (if (seen) m.species.name else "Something big") + " • Lv ${m.level} • ${den.species.element.label}",
                    fontWeight = FontWeight.SemiBold,
                    color = rarityColor(den.species.rarity),
                )
                Text(distanceText(dist, imperial), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Stronger than wild monsters: more HP, great stats (${GameEngine.perfectStatCount(m)} perfect). No loot here, just the monster.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "It can't be tamed mid-fight. Beat it, and you get ${Dens.MIN_THROWS} to ${Dens.MAX_THROWS} throws to tame it. Each one is a long shot.",
                    fontSize = 13.sp,
                    color = HpYellow,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (!inRange) {
                    Text("Walk within ${formatDistance(Dens.RANGE_M, imperial)} to fight.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(den.lat, den.lon, den.name)
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.challengeDen(den); onDismiss() }, enabled = inRange) { Text("Fight!") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun VillainDialog(vm: GameViewModel, camp: VillainCamp, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(camp.lat, camp.lon)
    val inRange = dist != null && dist <= Villains.RANGE_M
    val landmark = vm.landmarkNear(camp.lat, camp.lon)
    val street = rememberStreet(camp.lat, camp.lon)
    val where = landmark?.let { (place, gap) -> landmarkText(place, gap, vm.state.settings.imperialUnits) }?.let { w -> street?.let { "$w on $it" } ?: w }
        ?: street?.let { "Camped on $it" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { PlaceTitle(GameIcon.SKULL, camp.leader, Color(0xFFF28B9B), where) },
        text = {
            Column {
                Text("${camp.tier.label} of the ${camp.crew} • ${distanceText(dist, vm.state.settings.imperialUnits)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Their monsters carry the shadow curse (Defense -50%, Attack +25%) and can't be tamed. " +
                        "Beat the whole team in a row for gold and loot, then use a Restore Potion to try to free one.",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    camp.team.forEach { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MonsterArt(m.species, Modifier.size(52.dp))
                            Text("Lv ${m.level}", fontSize = 11.sp)
                        }
                    }
                }
                if (!inRange) {
                    Text("Walk within ${formatDistance(Villains.RANGE_M, vm.state.settings.imperialUnits)} to fight.", modifier = Modifier.padding(top = 6.dp))
                    WalkHereButton(camp.lat, camp.lon, camp.leader)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.challengeVillain(camp); onDismiss() }, enabled = inRange) { Text("Fight!") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private class Entry(
    val dist: Double,
    val art: @Composable () -> Unit,
    val title: String,
    val sub: String,
    val inRange: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun GuardianPreview(sp: com.monsterquest.core.Species) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonsterArt(sp, Modifier.size(72.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Guardian: ${sp.name}", fontWeight = FontWeight.SemiBold)
            Text("${sp.element.label} type", fontSize = 12.sp, color = sp.element.color())
        }
    }
}

/** Nearby shrines and monsters sorted by distance, shown beside the map on big screens. */
@Composable
private fun NearbyList(
    vm: GameViewModel,
    shrines: List<Shrine>,
    spawns: List<WildSpawn>,
    villains: List<VillainCamp>,
    dens: List<Den>,
    onDen: (Den) -> Unit,
    onShrine: (Shrine) -> Unit,
    onSpawn: (WildSpawn) -> Unit,
    onVillain: (VillainCamp) -> Unit,
) {
    val entries = shrines.map { sh ->
        val d = vm.distanceTo(sh.lat, sh.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { GIcon(sh.tier.icon(), Modifier.size(28.dp), sh.tier.color()) },
            sh.name,
            (sh.subtitle ?: sh.tier.label) + " • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= World.SHRINE_RANGE_M,
        ) { onShrine(sh) }
    } + spawns.map { sp ->
        val d = vm.distanceTo(sp.lat, sp.lon) ?: Double.MAX_VALUE
        val seen = sp.speciesId in vm.state.seen
        Entry(
            d,
            { MonsterArt(sp.species, Modifier.size(40.dp), silhouette = !seen) },
            if (seen) "Wild ${sp.species.name}" else "Unknown monster",
            "Lv ${sp.level} ${sp.species.rarity.label} • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= World.SPAWN_RANGE_M,
        ) { onSpawn(sp) }
    } + dens.map { den ->
        val d = vm.distanceTo(den.lat, den.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { MonsterArt(den.species, Modifier.size(40.dp), silhouette = den.monster.speciesId !in vm.state.seen) },
            den.name,
            "Den • Lv ${den.monster.level} • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= Dens.RANGE_M,
        ) { onDen(den) }
    } + villains.map { v ->
        val d = vm.distanceTo(v.lat, v.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { GIcon(GameIcon.SKULL, Modifier.size(28.dp), Color(0xFFF28B9B)) },
            v.leader,
            "${v.tier.label} • ${v.team.size} cursed monsters • " + distanceText(d.takeIf { it < Double.MAX_VALUE }, vm.state.settings.imperialUnits),
            d <= Villains.RANGE_M,
        ) { onVillain(v) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp)) {
        item { SectionTitle("Nearby") }
        if (entries.none { it.dist <= MAX_NEARBY_M }) {
            item { Text("Nothing found nearby yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(entries.filter { it.dist <= MAX_NEARBY_M }.sortedBy { it.dist }.take(40)) { e ->
            GameCard(Modifier.padding(vertical = 3.dp).clickable(onClick = e.onClick), border = if (e.inRange) Gold else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    e.art()
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (e.inRange) Text("In range", fontSize = 12.sp, color = Gold)
                }
            }
        }
    }
}
