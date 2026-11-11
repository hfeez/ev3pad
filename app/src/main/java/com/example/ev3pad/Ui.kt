package com.example.ev3pad

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun DrawScope.glow(p: Pal) {
    drawRect(Brush.radialGradient(listOf(p.acc.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width * 0.1f, 0f), radius = size.width * 0.75f))
    drawRect(Brush.radialGradient(listOf(p.acc2.copy(alpha = 0.13f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.7f))
}

@Composable
fun MainScreen(vm: MainVm) {
    val pal = LocalPal.current
    var sheetPad by remember { mutableStateOf<Pad?>(null) }
    var sheetItem by remember { mutableStateOf<Long?>(null) }
    var picker by remember { mutableStateOf(false) }
    var profKind by remember { mutableStateOf<String?>(null) }
    var settings by remember { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(true) }   // tampil saat aplikasi dibuka
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    FullscreenEffects(vm.fullscreen)
    LaunchedEffect(vm.padOn) { while (vm.padOn) { vm.refreshBattery(); delay(5000) } }
    BackHandler(vm.fullscreen) { vm.fullscreen = false }

    Box(Modifier.fillMaxSize().background(pal.bg).drawBehind { glow(pal) }.safeDrawingPadding()) {
        if (vm.fullscreen) FullscreenView(vm) { sheetItem = it }
        else Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopCenter) {
            if (land) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Header(vm, { picker = true }, { settings = true })
                        MonitorCard(vm)
                    }
                    ModeCard(vm, Modifier.weight(0.58f).fillMaxHeight(), { sheetPad = it }, { sheetItem = it }, { profKind = it })
                }
            } else {
                Column(Modifier.widthIn(max = 720.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header(vm, { picker = true }, { settings = true })
                    MonitorCard(vm)
                    ModeCard(vm, Modifier.fillMaxWidth().weight(1f), { sheetPad = it }, { sheetItem = it }, { profKind = it })
                }
            }
        }
    }

    if (picker) DevicePicker(vm.bonded(), onPick = { picker = false; vm.connect(it) }, onClose = { picker = false })
    if (settings) SettingsDialog(vm, onAbout = { settings = false; about = true }) { settings = false }
    if (about) AboutDialog { about = false }
    profKind?.let { k -> ProfilesSheet(vm, k) { profKind = null } }
    sheetPad?.let { p ->
        KeySheet(
            title = p.labelFor(vm.ctype), kind = p.kind, initial = vm.map[p], vm = vm, nameInit = null,
            deleteLabel = tr("Hapus pengaturan", "Clear settings"),
            onSave = { c, _ -> vm.saveKey(p, c); sheetPad = null },
            onDelete = { vm.saveKey(p, null); sheetPad = null },
            onClose = { sheetPad = null }
        )
    }
    sheetItem?.let { id ->
        vm.items.firstOrNull { it.id == id }?.let { it0 ->
            KeySheet(
                title = it0.name, kind = it0.kind, initial = it0.cfg, vm = vm, nameInit = it0.name,
                deleteLabel = tr("Hapus elemen", "Delete element"),
                onSave = { c, n -> vm.updateItem(id, n, c); sheetItem = null },
                onDelete = { vm.removeItem(id); sheetItem = null },
                onClose = { sheetItem = null }
            )
        }
    }
    vm.error?.let { msg ->
        AlertDialog(onDismissRequest = { vm.error = null }, title = { Text(tr("Perhatian", "Notice")) },
            text = { Text(tr(msg.id, msg.en)) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } })
    }
}

@Composable
fun Header(vm: MainVm, onConnect: () -> Unit, onSettings: () -> Unit) {
    val pal = LocalPal.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(buildAnnotatedString {
                append("EV3 ")
                withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = pal.acc, fontSize = 22.sp)) { append("Direct Control") }
            }, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val c = when (vm.ev3) { LinkState.ON -> pal.ok; LinkState.WAIT -> pal.acc; else -> pal.bad }
                Box(Modifier.size(8.dp).background(c, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(when (vm.ev3) {
                    LinkState.ON -> tr("Terhubung ke ", "Connected to ") + vm.ev3Name
                    LinkState.WAIT -> tr("Menghubungkan…", "Connecting…")
                    else -> tr("Belum terhubung", "Not connected")
                }, fontSize = 12.sp, color = pal.mute, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                val bat = vm.battery
                if (vm.ev3 == LinkState.ON && bat != null) {
                    Spacer(Modifier.width(8.dp)); BatteryIcon(bat); Spacer(Modifier.width(4.dp))
                    Text("$bat%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = pal.mute)
                }
            }
        }
        OutlinedButton(onClick = onSettings, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(40.dp)) { Text("⚙", fontSize = 18.sp, color = pal.ink) }
        if (vm.ev3 == LinkState.ON) OutlinedButton(onClick = { vm.disconnect() }) { Text(tr("Putuskan", "Disconnect"), color = pal.ink) }
        else PriButton(tr("Hubungkan EV3", "Connect EV3"), enabled = vm.ev3 == LinkState.OFF, onClick = onConnect)
    }
}

@Composable
fun MonitorCard(vm: MainVm) {
    val pal = LocalPal.current
    val on = vm.ev3 == LinkState.ON
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = pal.card), border = BorderStroke(1.dp, pal.line)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonoLbl("Monitor EV3")
            Lbl(tr("Port motor", "Motor ports"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..3) {
                    val d = if (on) vm.motorPorts[i] else null
                    val sp = vm.speeds[i]
                    PortChip(Modifier.weight(1f), "ABCD"[i].toString(),
                        if (!on) "–" else if (d != null) (if (sp > 0) "+" else "") + "$sp%" else tr("kosong", "empty"),
                        d?.label(), d != null, null, if (d != null) sp else null)
                }
            }
            Lbl(tr("Port sensor", "Sensor ports"))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..3) {
                    val d = if (on) vm.sensorPorts[i] else null
                    val m = if (on) vm.sensorMode(i) else null
                    val l1 = when { !on -> "–"; d == null -> tr("kosong", "empty"); m != null -> fmtSensor(m, vm.sensorValue(i)); else -> d.label() }
                    val canCycle = d != null && modesOf(d).size > 1
                    PortChip(Modifier.weight(1f), "${i + 1}", l1, m?.label(), d != null, if (canCycle) ({ vm.cycleSensor(i) }) else null, null)
                }
            }
            if (on && vm.sensorPorts.any { it != null && modesOf(it).size > 1 })
                Lbl(tr("Ketuk port sensor untuk mengganti jenis pengukuran", "Tap a sensor port to change what it measures"))
        }
    }
}

@Composable
private fun PortChip(modifier: Modifier, name: String, line1: String, line2: String?, has: Boolean, onClick: (() -> Unit)?, bar: Int?) {
    val pal = LocalPal.current
    val sh = RoundedCornerShape(12.dp)
    Column(
        modifier.clip(sh).border(1.dp, if (has) pal.ok.copy(alpha = 0.55f) else pal.line, sh)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 7.dp, horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (has) pal.ok else pal.line, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(name, fontWeight = FontWeight.Medium, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Text(line1, fontSize = 13.sp, fontWeight = if (line2 != null) FontWeight.SemiBold else FontWeight.Normal, fontFamily = FontFamily.Monospace,
            color = if (line2 != null) pal.ink else pal.mute, textAlign = TextAlign.Center, maxLines = 2)
        Text(line2 ?: " ", fontSize = 10.5.sp, color = pal.mute, textAlign = TextAlign.Center, maxLines = 1)
        if (bar != null) Box(
            Modifier.padding(horizontal = 6.dp, vertical = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(pal.line).drawBehind {
                val half = size.width / 2f; val v = bar / 100f
                if (v >= 0) drawRect(pal.acc, Offset(half, 0f), Size(half * v, size.height))
                else drawRect(pal.acc2, Offset(half + half * v, 0f), Size(-half * v, size.height))
            }
        )
    }
}

@Composable
fun ModeCard(vm: MainVm, modifier: Modifier, onKey: (Pad) -> Unit, onItem: (Long) -> Unit, onProfiles: (String) -> Unit) {
    val pal = LocalPal.current
    val kind = if (vm.tab == 0) "ctl" else "virt"
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = pal.card), border = BorderStroke(1.dp, pal.line)) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Seg(listOf(0 to "Controller", 1 to "Virtual"), vm.tab) { vm.selectTab(it) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val a = vm.active[kind]
                val dirty = vm.dirty(kind)
                Text(if (a != null) tr("Profil: ", "Profile: ") + a + (if (dirty) tr(" (belum disimpan)", " (unsaved)") else "")
                else tr("Belum ada profil aktif", "No active profile"),
                    fontSize = 12.5.sp, color = pal.mute, modifier = Modifier.weight(1f), maxLines = 1)
                if (dirty && a != null) SmallBtn(tr("Simpan perubahan", "Save changes")) { vm.saveProfile(kind, a) }
                val pb = vm.padBat
                if (vm.padOn && pb != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BatteryIcon(pb, charging = vm.padChg)
                    Text("$pb%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = pal.mute)
                }
                SmallBtn(tr("Profil", "Profiles")) { onProfiles(kind) }
                if (vm.tab == 1) SmallBtn(tr("⛶ Layar penuh", "⛶ Fullscreen")) { vm.fullscreen = true }
            }
            if (vm.tab == 0) PadSection(vm, Modifier.weight(1f).fillMaxWidth(), onKey)
            else VirtualSection(vm, Modifier.weight(1f).fillMaxWidth(), onItem)
        }
    }
}

@Composable
private fun PadSection(vm: MainVm, modifier: Modifier, onKey: (Pad) -> Unit) {
    val pal = LocalPal.current
    val skin = remember(vm.ctype) { skinFor(vm.ctype) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(190.dp)) {
                Seg(listOf(false to tr("Atur", "Configure"), true to tr("Uji input", "Test input")), vm.testMode) { vm.changeTestMode(it) }
            }
            val sh = RoundedCornerShape(99.dp)
            Row(Modifier.clip(sh).border(1.dp, pal.line, sh).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(if (vm.padOn) pal.ok else pal.bad, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(if (vm.padOn) vm.ctype.title() else "–", fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = pal.mute, maxLines = 1)
            }
            Text(
                if (vm.testMode) tr("Tahan tombol atau geser stik untuk mencoba", "Hold a button or drag a stick to try")
                else tr("Ketuk tombol untuk membuka pengaturan", "Tap a button to open its settings"),
                fontSize = 12.sp, color = pal.mute, modifier = Modifier.weight(1f), maxLines = 2
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight * (skin.vw / skin.vh))
            Box(Modifier.width(w), contentAlignment = Alignment.Center) {
                ControllerView(vm, skin, vm.padOn, onKey, Modifier.fillMaxWidth())
                if (!vm.padOn) NoPad()
            }
        }
        val n = vm.map.count { it.value.motors.isNotEmpty() }
        Text(
            if (vm.padOn) (if (n > 0) "$n " + tr("tombol sudah diatur", "buttons configured") else tr("Ketuk tombol untuk mengatur", "Tap a button to configure"))
            else tr("Hubungkan controller melalui Bluetooth", "Connect the controller via Bluetooth"),
            fontSize = 13.sp, color = pal.mute, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun VirtualSection(vm: MainVm, modifier: Modifier, onItem: (Long) -> Unit) {
    val pal = LocalPal.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.width(190.dp)) {
                Seg(listOf(true to "✎ Edit", false to tr("▶ Kontrol", "▶ Control")), vm.edit) { vm.changeEdit(it) }
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (vm.edit) {
                    SmallBtn(tr("+ Tombol", "+ Button")) { vm.addItem(VType.BTN) }
                    SmallBtn("+ Slider") { vm.addItem(VType.SLIDER) }
                    SmallBtn("+ Analog") { vm.addItem(VType.ANALOG) }
                }
            }
        }
        VirtualStage(vm, vm.edit, Modifier.weight(1f).fillMaxWidth(), onTap = onItem)
        Text(
            if (vm.edit) tr("Seret untuk memindah, tarik titik di pojok untuk mengubah ukuran, ketuk untuk mengatur. Di HP, pakai ⛶ untuk mengedit di layar penuh.",
                "Drag to move, pull the corner dot to resize, tap to configure. On phones, use ⛶ to edit in fullscreen.")
            else tr("Mode kontrol: sentuh elemen untuk menggerakkan motor. Elemen redup = terkunci oleh syarat sensor.",
                "Control mode: touch elements to drive motors. A dimmed element is locked by a sensor condition."),
            fontSize = 12.sp, color = pal.mute
        )
    }
}

@Composable
private fun NoPad() {
    val pal = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(90.dp)) {
            drawLine(pal.bad, Offset(8f, 8f), Offset(size.width - 8f, size.height - 8f), strokeWidth = 18f, cap = StrokeCap.Round)
            drawLine(pal.bad, Offset(size.width - 8f, 8f), Offset(8f, size.height - 8f), strokeWidth = 18f, cap = StrokeCap.Round)
        }
        Text(tr("Controller PS4 belum terhubung", "PS4 controller not connected"), fontSize = 14.sp,
            modifier = Modifier.background(pal.bg2, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun DevicePicker(devs: List<BluetoothDevice>, onPick: (BluetoothDevice) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Pilih EV3", "Select EV3")) },
        text = {
            if (devs.isEmpty()) Text(tr(
                "Belum ada perangkat Bluetooth yang dipasangkan. Pasangkan EV3 dulu di Pengaturan Bluetooth (PIN default 1234), lalu kembali ke sini.",
                "No paired Bluetooth devices yet. Pair the EV3 in Bluetooth settings first (default PIN 1234), then come back."))
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devs.forEach { d -> OutlinedButton(onClick = { onPick(d) }, modifier = Modifier.fillMaxWidth()) { Text(d.safeName()) } }
            }
        },
        confirmButton = { TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text(tr("Pengaturan Bluetooth", "Bluetooth settings")) } },
        dismissButton = { TextButton(onClick = onClose) { Text(tr("Batal", "Cancel")) } }
    )
}

@Composable
fun SettingsDialog(vm: MainVm, onAbout: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Pengaturan", "Settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lbl(tr("Bahasa", "Language"))
                Seg(listOf("id" to "Indonesia", "en" to "English"), Lang.code) { vm.setLang(it) }
                Spacer(Modifier.height(2.dp))
                Lbl(tr("Jenis controller", "Controller type"))
                Seg(listOf("auto" to tr("Otomatis", "Auto"), "ds4" to "DualShock", "xbox" to "Xbox", "joycon" to "Joy-Con"), vm.ctypePref) { vm.chooseCtype(it) }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text("About this app", color = LocalPal.current.ink) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Tutup", "Close")) } }
    )
}
