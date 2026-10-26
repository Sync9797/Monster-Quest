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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

private sealed interface MapPick {
    data class ShrinePick(val shrine: Shrine) : MapPick
    data class SpawnPick(val spawn: WildSpawn) : MapPick
    data class VillainPick(val camp: VillainCamp) : MapPick
}

internal fun hasLocationPermission(context: Context) =
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
    val villains = vm.villainsNear(now)
    var pick by remember { mutableStateOf<MapPick?>(null) }

    // Buzz when a new monster shows up nearby (also while the battery saver screen is on).
    LaunchedEffect(spawns.map { it.id }, loc) {
        if (loc == null) return@LaunchedEffect
        val fresh = vm.newSpawnsNearby(spawns)
        if (fresh.isNotEmpty() && vm.state.settings.vibrateOnSpawn) Haptics.spawn(context)
    }
    var centered by remember { mutableStateOf(false) }

    val mapView = rememberMapView()
    val icons = remember { HashMap<String, BitmapDrawable>() }
    fun sprite(key: String, wDp: Int, hDp: Int, draw: (android.graphics.Canvas, Float, Float) -> Unit) =
        icons.getOrPut("$key:${ArtQuality.reduced}") { spriteDrawable(context, wDp, hDp, draw) }

    val mapPane: @Composable () -> Unit = {
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
                                    setIcon(sprite("spawn_${sp.speciesId}_${sp.lured}", 58, 58) { c, w, h -> drawMonsterStanding(c, w, h, sp.species, sp.lured) })
                                    title = sp.species.name
                                    setOnMarkerClickListener { _, _ -> pick = MapPick.SpawnPick(sp); true }
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
                                    val outfit = HeroOutfit.of(vm.state.equippedGear, vm.state.equippedArmor)
                                    setIcon(sprite("player_${vm.state.hero.heroClass}_$outfit", 60, 60) { c, w, h -> drawPlayerOn(c, w, h, vm.state.hero.heroClass, outfit) })
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
            SpawnInfoRow(vm, now)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        vm.loadingPlaces -> "Scanning for shrines…"
                        else -> "${shrines.count { it.tier == ShrineTier.ITEM }} item • ${shrines.count { it.tier == ShrineTier.SILVER }} silver • " +
                            "${shrines.count { it.tier == ShrineTier.GOLD }} gold • ${spawns.size} monsters • ${villains.size} foes"
                    },
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    OutlinedButton(onClick = {
                        loc?.let { mapView.controller.animateTo(GeoPoint(it.first, it.second)) }
                    }) { IconText(GameIcon.PIN, "Center") }
                    OutlinedButton(onClick = { vm.batterySaver = true }) { IconText(GameIcon.MOON, "Saver") }
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

    AdaptivePanes(
        firstWeight = 0.62f,
        compact = mapPane,
        first = mapPane,
        second = {
            NearbyList(
                vm = vm,
                shrines = shrines,
                spawns = spawns,
                villains = villains,
                onShrine = { pick = MapPick.ShrinePick(it) },
                onSpawn = { pick = MapPick.SpawnPick(it) },
                onVillain = { pick = MapPick.VillainPick(it) },
            )
        },
    )

    when (val p = pick) {
        is MapPick.ShrinePick -> ShrineDialog(vm, p.shrine, now) { pick = null }
        is MapPick.SpawnPick -> SpawnDialog(vm, p.spawn) { pick = null }
        is MapPick.VillainPick -> VillainDialog(vm, p.camp) { pick = null }
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
                if (pct >= 5) "On the move: +$pct% spawns, rarer monsters" else "Standing still: monsters still wander up to you. Walk for more.",
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

@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            // osmdroid draws tiles past its own edges; clip it to its bounds so it
            // can't paint over the nav rail, headers or the status row.
            outlineProvider = ViewOutlineProvider.BOUNDS
            clipToOutline = true
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
internal fun TrackLocation(context: Context, batterySaver: Boolean = false, onLocation: (Double, Double) -> Unit) {
    DisposableEffect(batterySaver) {
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
            val (interval, distance) = if (batterySaver) 20_000L to 25f else 4_000L to 5f
            providers.forEach { lm.requestLocationUpdates(it, interval, distance, listener, Looper.getMainLooper()) }
        } catch (e: SecurityException) {
            // Permission revoked while running; the screen will ask again.
        }
        onDispose { lm.removeUpdates(listener) }
    }
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
    val guardian = shrine.bossSpeciesId?.let { Bestiary.get(it) }
    val access = Schedule.access(vm.state, shrine, now)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { IconText(shrine.tier.icon(), shrine.name, tint = shrine.tier.color(), size = 20.dp, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("${shrine.tier.label} • ${distanceText(dist)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        "Walk within ${World.SHRINE_RANGE_M.toInt()} m to interact.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wild ${sp.name}") },
        text = {
            Column {
                MonsterArt(sp, Modifier.size(110.dp).align(Alignment.CenterHorizontally))
                Text("Level ${spawn.level} • ${sp.element.label} • ${sp.rarity.label}", color = rarityColor(sp.rarity))
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

@Composable
private fun VillainDialog(vm: GameViewModel, camp: VillainCamp, onDismiss: () -> Unit) {
    val dist = vm.distanceTo(camp.lat, camp.lon)
    val inRange = dist != null && dist <= Villains.RANGE_M
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { IconText(GameIcon.SKULL, camp.leader, tint = Color(0xFFF28B9B), size = 20.dp, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("${camp.tier.label} of the ${camp.crew} • ${distanceText(dist)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (!inRange) Text("Walk within ${Villains.RANGE_M.toInt()} m to fight.", modifier = Modifier.padding(top = 6.dp))
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
            "${sh.tier.label} • " + distanceText(d.takeIf { it < Double.MAX_VALUE }),
            d <= World.SHRINE_RANGE_M,
        ) { onShrine(sh) }
    } + spawns.map { sp ->
        val d = vm.distanceTo(sp.lat, sp.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { MonsterArt(sp.species, Modifier.size(40.dp)) },
            "Wild ${sp.species.name}",
            "Lv ${sp.level} ${sp.species.rarity.label} • " + distanceText(d.takeIf { it < Double.MAX_VALUE }),
            d <= World.SPAWN_RANGE_M,
        ) { onSpawn(sp) }
    } + villains.map { v ->
        val d = vm.distanceTo(v.lat, v.lon) ?: Double.MAX_VALUE
        Entry(
            d,
            { GIcon(GameIcon.SKULL, Modifier.size(28.dp), Color(0xFFF28B9B)) },
            v.leader,
            "${v.tier.label} • ${v.team.size} cursed monsters • " + distanceText(d.takeIf { it < Double.MAX_VALUE }),
            d <= Villains.RANGE_M,
        ) { onVillain(v) }
    }

    LazyColumn(Modifier.fillMaxSize().padding(end = 12.dp)) {
        item { SectionTitle("Nearby") }
        if (entries.isEmpty()) {
            item { Text("Nothing found nearby yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(entries.sortedBy { it.dist }.take(40)) { e ->
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
