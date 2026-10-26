package com.monsterquest.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.monsterquest.app.GameViewModel
import com.monsterquest.app.data.HealthSteps
import com.monsterquest.app.ui.art.GameIcon
import com.monsterquest.app.ui.art.IconText
import com.monsterquest.core.CALENDAR_DAYS_AHEAD_OPTIONS
import com.monsterquest.core.StepSource
import com.monsterquest.core.ColorStyle
import com.monsterquest.core.ThemeMode

/** Settings: performance, calendar and where steps come from. */
@Composable
fun SettingsScreen(vm: GameViewModel, initialHelp: Boolean = false, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = vm.state.settings
    LaunchedEffect(Unit) { vm.refreshHealthStatus() }
    var showHelp by rememberSaveable { mutableStateOf(initialHelp) }
    BackHandler(enabled = showHelp) { showHelp = false }
    if (showHelp) {
        HelpScreen { showHelp = false }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconText(GameIcon.GEAR, "Settings", tint = Gold, textColor = Gold, size = 22.dp, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Done") }
            }

            GameCard(Modifier.padding(top = 4.dp).clickable { showHelp = true }, border = Gold) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconText(GameIcon.BOOK, "Help: how to play", tint = Gold, textColor = Gold, bold = true)
                    Spacer(Modifier.weight(1f))
                    Text("Open", color = Gold, fontSize = 13.sp)
                }
                Text(
                    "Quests, phone-free sessions, walking, the map and tracker, collecting items, battles and packs, taming, boss raids and more.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Appearance ----
            SectionTitle("Appearance")
            GameCard {
                Text("Theme", fontWeight = FontWeight.SemiBold)
                Text("Light or dark screens, or follow your phone's setting.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { m ->
                        SmallButton(m.label, { vm.updateSettings { it.copy(themeMode = m) } }, Modifier.weight(1f), outlined = m != prefs.themeMode)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Colors", fontWeight = FontWeight.SemiBold)
                ColorStyle.entries.forEach { style ->
                    val supported = style != ColorStyle.SYSTEM || dynamicColorSupported
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = supported) { vm.updateSettings { it.copy(colorStyle = style) } }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = style == prefs.colorStyle,
                            onClick = { vm.updateSettings { it.copy(colorStyle = style) } },
                            enabled = supported,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(style.label, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (supported) style.description else "Needs Android 12 or newer.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---- General ----
            SectionTitle("General")
            GameCard {
                ToggleRow(
                    "Feet and miles",
                    "Show distances in feet and miles. Turn off for meters and kilometers.",
                    prefs.imperialUnits,
                ) { on -> vm.updateSettings { it.copy(imperialUnits = on) } }
            }

            // ---- Steps ----
            SectionTitle("Step sync")
            Text(
                "Choose where the game counts your walking from. Steps level up you and your party, open loot boxes and bring more monsters.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StepSource.entries.forEach { source ->
                GameCard(
                    Modifier.padding(vertical = 3.dp).clickable { if (source != prefs.stepSource) vm.setStepSource(source) },
                    border = if (source == prefs.stepSource) Gold else null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = source == prefs.stepSource, onClick = { if (source != prefs.stepSource) vm.setStepSource(source) })
                        Column(Modifier.weight(1f)) {
                            Text(source.label, fontWeight = FontWeight.SemiBold)
                            Text(source.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            StepSourceStatus(vm)
            Text(
                "Steps today: %,d".format(vm.state.stepsToday),
                fontSize = 13.sp,
                color = HpGreen,
                modifier = Modifier.padding(top = 6.dp),
            )

            // ---- Calendar ----
            SectionTitle("Calendar")
            val hasCalendar = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
            val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                if (ok) vm.syncCalendar()
            }
            GameCard {
                if (!hasCalendar) {
                    Text("Calendar access is off. Connect it to turn your events into quests.", fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    SmallButton("Connect calendar", { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }, Modifier.fillMaxWidth())
                } else {
                    IconText(GameIcon.CHECK, "Connected to your phone's calendars", tint = HpGreen, fontSize = 13.sp)
                }
                ToggleRow("Sync automatically", "Pull new events whenever you open Quests.", prefs.calendarAutoSync) { on ->
                    vm.updateSettings { it.copy(calendarAutoSync = on) }
                }
                ToggleRow("Include all-day events", "Holidays and birthdays are skipped unless you pick their calendar below.", prefs.includeAllDayEvents) { on ->
                    vm.updateSettings { it.copy(includeAllDayEvents = on) }
                }
                if (hasCalendar) CalendarPicker(vm)
                Text("Look ahead", fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CALENDAR_DAYS_AHEAD_OPTIONS.forEach { days ->
                        SmallButton(
                            if (days == 1) "1 day" else "$days days",
                            { vm.updateSettings { it.copy(calendarDaysAhead = days) } },
                            Modifier.weight(1f),
                            outlined = days != prefs.calendarDaysAhead,
                        )
                    }
                }
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton(if (vm.syncingCalendar) "Syncing…" else "Sync now", { vm.syncCalendar() }, Modifier.weight(1f), enabled = hasCalendar && !vm.syncingCalendar)
                    SmallButton("Open calendar", {
                        val intent = Intent(Intent.ACTION_VIEW).setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                        runCatching { context.startActivity(intent) }
                    }, Modifier.weight(1f), outlined = true)
                }
                TextButton(onClick = vm::clearCalendarQuests) {
                    Text("Remove unfinished calendar quests", color = HpRed, fontSize = 13.sp)
                }
            }

            // ---- Performance ----
            SectionTitle("Performance")
            GameCard {
                Text("Frame rate", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                val maxHz = runCatching {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager)
                        .defaultDisplay.supportedModes.maxOf { it.refreshRate }.toInt()
                }.getOrNull()
                Text(
                    prefs.frameRate.description + (maxHz?.let { " This screen goes up to $it Hz." } ?: ""),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.monsterquest.core.FrameRate.entries.forEach { r ->
                        SmallButton(r.label, { vm.updateSettings { it.copy(frameRate = r) } }, Modifier.weight(1f), outlined = r != prefs.frameRate)
                    }
                }
                ToggleRow(
                    "Reduced visual effects",
                    "Turns off glows, auras, mist and engraving. Smoother on older phones and easier on the battery.",
                    prefs.reducedEffects,
                ) { on -> vm.updateSettings { it.copy(reducedEffects = on) } }
                ToggleRow(
                    "Battery saver when upside down",
                    "On the map, hold your phone upside down (at your side or in a pocket) and the screen goes dark while the game keeps tracking. Turn it upright to return.",
                    prefs.flipBatterySaver,
                ) { on -> vm.updateSettings { it.copy(flipBatterySaver = on) } }
                ToggleRow(
                    "Ambushes",
                    "Aggressive monsters that reach you on the map can start a fight and strike first. At most one every 10 minutes.",
                    prefs.ambushes,
                ) { on -> vm.updateSettings { it.copy(ambushes = on) } }
                ToggleRow(
                    "Vibrate for new monsters",
                    "A short buzz when a monster comes within battle range (${formatDistance(com.monsterquest.app.ALERT_RANGE_M, prefs.imperialUnits)}), even on the battery saver screen.",
                    prefs.vibrateOnSpawn,
                ) { on -> vm.updateSettings { it.copy(vibrateOnSpawn = on) } }
                ToggleRow(
                    "Battery saver GPS",
                    "Checks your location less often. The map and GPS step counting update more slowly.",
                    prefs.batterySaverGps,
                ) { on -> vm.updateSettings { it.copy(batterySaverGps = on) } }
            }
            // ---- About ----
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            Text(
                "Monster Quest ${version ?: ""}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Status and fix-it buttons for the chosen step source. */
@Composable
private fun StepSourceStatus(vm: GameViewModel) {
    val context = LocalContext.current
    val source = vm.state.settings.stepSource
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        vm.refreshHealthStatus()
        vm.refreshHealthSteps()
    }
    val activityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    GameCard(Modifier.padding(top = 6.dp)) {
        when {
            source.usesHealthConnect -> when (vm.healthStatus) {
                HealthSteps.Status.READY -> IconText(GameIcon.CHECK, "Health Connect is connected", tint = HpGreen, fontSize = 13.sp)
                HealthSteps.Status.NEEDS_PERMISSION -> {
                    Text("Allow the game to read your steps in Health Connect.", fontSize = 13.sp)
                    SmallButton("Allow step access", { healthPermission.launch(HealthSteps.permissions) }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                HealthSteps.Status.NEEDS_INSTALL -> {
                    Text("Health Connect needs to be installed or updated.", fontSize = 13.sp)
                    SmallButton("Get Health Connect", { runCatching { context.startActivity(HealthSteps.installIntent()) } }, Modifier.fillMaxWidth().padding(top = 6.dp))
                }
                HealthSteps.Status.UNAVAILABLE -> Text("Health Connect isn't available on this phone. Try the phone step counter.", fontSize = 13.sp, color = HpRed)
                null -> Text("Checking Health Connect…", fontSize = 13.sp)
            }
            source == StepSource.PHONE -> {
                val needsPermission = Build.VERSION.SDK_INT >= 29 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
                when {
                    needsPermission -> {
                        Text("Allow physical activity access so the game can read the step counter.", fontSize = 13.sp)
                        SmallButton(
                            "Allow activity access",
                            { activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                            Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                    vm.stepSensorAvailable -> IconText(GameIcon.CHECK, "Step counter is working", tint = HpGreen, fontSize = 13.sp)
                    else -> Text("This phone has no step counter, so GPS distance is used while the game is open.", fontSize = 13.sp)
                }
            }
            else -> Text("Steps are counted from GPS distance while the map is open.", fontSize = 13.sp)
        }
        if (source == StepSource.SAMSUNG_HEALTH) {
            Text(
                "In Samsung Health open Settings, then Health Connect, and allow it to share Steps.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Choose which of the phone's calendars become quests. */
@Composable
private fun CalendarPicker(vm: GameViewModel) {
    LaunchedEffect(Unit) { vm.loadCalendars() }
    val prefs = vm.state.settings
    val calendars = vm.phoneCalendars
    Text("Calendars to sync", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    Text(
        "Tick the calendars whose events should become quests. Changes sync right away; unfinished quests from unticked calendars are removed.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().clickable { vm.syncAllCalendars() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = prefs.syncAllCalendars, onCheckedChange = { vm.syncAllCalendars() })
        Column {
            Text("All calendars", fontSize = 14.sp)
            Text("Everything except holidays and birthdays", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (calendars.isEmpty()) {
        Text("No calendars found on this phone.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 12.dp))
    }
    calendars.groupBy { it.account }.forEach { (account, list) ->
        if (account.isNotBlank()) {
            Text(account, fontSize = 11.sp, color = Gold, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
        }
        list.forEach { cal ->
            val checked = if (prefs.syncAllCalendars) !cal.isNoise else cal.id in prefs.syncCalendars
            Row(
                Modifier.fillMaxWidth().clickable { vm.toggleCalendar(cal.id) }.padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = { vm.toggleCalendar(cal.id) })
                Box(Modifier.size(12.dp).background(androidx.compose.ui.graphics.Color(cal.color or 0xFF000000.toInt()), androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(cal.name, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}
