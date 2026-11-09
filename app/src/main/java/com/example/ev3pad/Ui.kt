package com.example.ev3pad

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val W = 360f
private const val H = 250f

sealed class KShape
class RectS(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : KShape()
class CircS(val x: Float, val y: Float, val r: Float) : KShape()

/** Posisi tiap tombol di kanvas 360x250 (urutan gambar; hit-test dari belakang). */
private val KEYS: List<Triple<Pad, KShape, String>> = listOf(
    Triple(Pad.L2, RectS(48f, 14f, 64f, 20f, 8f), "L2"),
    Triple(Pad.R2, RectS(248f, 14f, 64f, 20f, 8f), "R2"),
    Triple(Pad.L1, RectS(48f, 38f, 64f, 16f, 8f), "L1"),
    Triple(Pad.R1, RectS(248f, 38f, 64f, 16f, 8f), "R1"),
    Triple(Pad.TP, RectS(135f, 62f, 90f, 46f, 10f), "Touchpad"),
    Triple(Pad.SHARE, RectS(108f, 68f, 18f, 12f, 5f), ""),
    Triple(Pad.OPT, RectS(234f, 68f, 18f, 12f, 5f), ""),
    Triple(Pad.UP, RectS(82f, 76f, 26f, 26f, 6f), "▲"),
    Triple(Pad.DOWN, RectS(82f, 126f, 26f, 26f, 6f), "▼"),
    Triple(Pad.LEFT, RectS(57f, 101f, 26f, 26f, 6f), "◀"),
    Triple(Pad.RIGHT, RectS(107f, 101f, 26f, 26f, 6f), "▶"),
    Triple(Pad.TRI, CircS(265f, 88f, 14f), "△"),
    Triple(Pad.CRS, CircS(265f, 140f, 14f), "✕"),
    Triple(Pad.SQR, CircS(239f, 114f, 14f), "□"),
    Triple(Pad.CIR, CircS(291f, 114f, 14f), "○"),
    Triple(Pad.PS, CircS(180f, 135f, 9f), ""),
    Triple(Pad.LS, CircS(130f, 180f, 28f), ""),
    Triple(Pad.RS, CircS(230f, 180f, 28f), ""),
    Triple(Pad.L3, CircS(130f, 180f, 10f), "L3"),
    Triple(Pad.R3, CircS(230f, 180f, 10f), "R3"),
)

private fun hit(s: KShape, x: Float, y: Float): Boolean = when (s) {
    is RectS -> x in (s.x - 6f)..(s.x + s.w + 6f) && y in (s.y - 6f)..(s.y + s.h + 6f)
    is CircS -> (x - s.x) * (x - s.x) + (y - s.y) * (y - s.y) <= (s.r + 6f) * (s.r + 6f)
}

@Composable
fun MainScreen(vm: MainVm) {
    val pal = LocalPal.current
    var sheet by remember { mutableStateOf<Pad?>(null) }
    var picker by remember { mutableStateOf(false) }
    var profilesOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        Modifier.fillMaxSize().background(pal.bg).safeDrawingPadding().padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        if (land) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopPanel(vm, { picker = true }, { profilesOpen = true }, { settingsOpen = true },
                    Modifier.weight(0.42f).verticalScroll(rememberScrollState()))
                PadCard(vm, Modifier.weight(0.58f).fillMaxHeight()) { sheet = it }
            }
        } else {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopPanel(vm, { picker = true }, { profilesOpen = true }, { settingsOpen = true }, Modifier.fillMaxWidth())
                PadCard(vm, Modifier.fillMaxWidth().weight(1f)) { sheet = it }
            }
        }
    }

    if (picker) DevicePicker(vm.bonded(), onPick = { picker = false; vm.connect(it) }, onClose = { picker = false })
    if (profilesOpen) ProfilesSheet(vm) { profilesOpen = false }
    if (settingsOpen) SettingsDialog(vm) { settingsOpen = false }
    sheet?.let { p ->
        KeySheet(p, vm.map[p], vm.motorPorts, vm.ev3 == LinkState.ON,
            onSave = { vm.saveKey(p, it); sheet = null },
            onReset = { vm.saveKey(p, null); sheet = null },
            onClose = { sheet = null })
    }
    vm.error?.let { msg ->
        AlertDialog(onDismissRequest = { vm.error = null }, title = { Text(tr("Perhatian", "Notice")) },
            text = { Text(tr(msg.id, msg.en)) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } })
    }
}

@Composable
fun TopPanel(vm: MainVm, onConnect: () -> Unit, onProfiles: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val on = vm.ev3 == LinkState.ON
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = pal.card),
        border = BorderStroke(1.dp, pal.line)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EV3 Pad", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val c = when (vm.ev3) { LinkState.ON -> pal.ok; LinkState.WAIT -> Accent; else -> pal.bad }
                        Box(Modifier.size(9.dp).background(c, CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text(when (vm.ev3) {
                            LinkState.ON -> tr("Terhubung ke ", "Connected to ") + vm.ev3Name
                            LinkState.WAIT -> tr("Menghubungkan…", "Connecting…")
                            else -> tr("Belum terhubung", "Not connected")
                        }, fontSize = 13.sp, color = pal.mute)
                    }
                }
                if (on) OutlinedButton(onClick = { vm.disconnect() }) { Text(tr("Putuskan", "Disconnect")) }
                else Button(onClick = onConnect, enabled = vm.ev3 == LinkState.OFF,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) {
                    Text(tr("Hubungkan EV3", "Connect EV3"))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onProfiles, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(tr("Profil controller", "Controller profiles"), fontSize = 13.sp, maxLines = 1)
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(tr("Pengaturan", "Settings"), fontSize = 13.sp, maxLines = 1)
                }
            }

            Text(tr("Port motor", "Motor ports"), fontSize = 12.sp, color = pal.mute, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("A", "B", "C", "D").forEachIndexed { i, n ->
                    val d = if (on) vm.motorPorts[i] else null
                    PortChip(Modifier.weight(1f), n, if (!on) "–" else d?.label() ?: tr("kosong", "empty"), null, d != null, null)
                }
            }

            Text(tr("Port sensor", "Sensor ports"), fontSize = 12.sp, color = pal.mute, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..3) {
                    val d = if (on) vm.sensorPorts[i] else null
                    val modes = if (d != null) modesOf(d) else emptyList()
                    val m = if (modes.isEmpty()) null else modes[vm.sensorModes[i] % modes.size]
                    val l1 = when {
                        !on -> "–"
                        d == null -> tr("kosong", "empty")
                        m != null -> fmtSensor(m, vm.sensorVals[i])
                        else -> d.label()
                    }
                    PortChip(Modifier.weight(1f), "${i + 1}", l1, m?.label(), d != null,
                        if (modes.size > 1) ({ vm.cycleSensor(i) }) else null)
                }
            }
            if (on && vm.sensorPorts.any { it != null && modesOf(it).size > 1 }) {
                Text(tr("Ketuk port sensor untuk mengganti jenis pengukuran", "Tap a sensor port to change what it measures"),
                    fontSize = 11.sp, color = pal.mute, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun PortChip(modifier: Modifier, name: String, line1: String, line2: String?, has: Boolean, onClick: (() -> Unit)?) {
    val pal = LocalPal.current
    val sh = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(sh).border(1.dp, if (has) pal.ok else pal.line, sh)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(if (has) pal.ok else pal.line, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(line1, fontSize = 12.sp, fontWeight = if (line2 != null) FontWeight.Bold else FontWeight.Normal,
            color = if (line2 != null) pal.ink else pal.mute, textAlign = TextAlign.Center, maxLines = 2)
        if (line2 != null) Text(line2, fontSize = 11.sp, color = pal.mute, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
fun PadCard(vm: MainVm, modifier: Modifier, onTap: (Pad) -> Unit) {
    val pal = LocalPal.current
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = pal.card),
        border = BorderStroke(1.dp, pal.line)) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            vm.active?.let { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Profil: ", "Profile: ") + a + (if (vm.dirty) tr(" (belum disimpan)", " (unsaved)") else ""),
                        fontSize = 13.sp, color = pal.mute, modifier = Modifier.weight(1f), maxLines = 1)
                    if (vm.dirty) TextButton(onClick = { vm.saveProfile(a) }) { Text(tr("Simpan perubahan", "Save changes")) }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val w = minOf(maxWidth, maxHeight * (W / H))
                Box(Modifier.width(w), contentAlignment = Alignment.Center) {
                    ControllerView(Modifier.fillMaxWidth(), vm.padOn, { vm.map[it]?.motors?.isNotEmpty() == true }, onTap)
                    if (!vm.padOn) NoPad()
                }
            }
            val n = vm.map.count { it.value.motors.isNotEmpty() }
            Text(
                if (vm.padOn) (if (n > 0) "$n " + tr("tombol sudah diatur", "buttons configured") else tr("Ketuk tombol untuk mengatur", "Tap a button to configure"))
                else tr("Hubungkan controller melalui Bluetooth", "Connect the controller via Bluetooth"),
                fontSize = 13.sp, color = pal.mute, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoPad() {
    val pal = LocalPal.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(96.dp)) {
            drawLine(pal.bad, Offset(8f, 8f), Offset(size.width - 8f, size.height - 8f), strokeWidth = 18f, cap = StrokeCap.Round)
            drawLine(pal.bad, Offset(size.width - 8f, 8f), Offset(8f, size.height - 8f), strokeWidth = 18f, cap = StrokeCap.Round)
        }
        Text(tr("Controller PS4 belum terhubung", "PS4 controller not connected"), fontSize = 14.sp,
            modifier = Modifier.background(pal.card, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
fun ControllerView(modifier: Modifier, enabled: Boolean, active: (Pad) -> Boolean, onTap: (Pad) -> Unit) {
    val pal = LocalPal.current
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
        }
    }
    Canvas(
        modifier.aspectRatio(W / H).alpha(if (enabled) 1f else 0.35f).pointerInput(enabled) {
            detectTapGestures { o ->
                if (!enabled) return@detectTapGestures
                val s = size.width / W
                val x = o.x / s; val y = o.y / s
                KEYS.asReversed().firstOrNull { hit(it.second, x, y) }?.let { onTap(it.first) }
            }
        }
    ) {
        scale(size.width / W, size.width / W, pivot = Offset.Zero) {
            val body = Path().apply {
                moveTo(70f, 70f); cubicTo(90f, 52f, 270f, 52f, 290f, 70f)
                cubicTo(330f, 88f, 345f, 175f, 330f, 215f); cubicTo(320f, 240f, 290f, 238f, 275f, 215f)
                cubicTo(262f, 195f, 248f, 185f, 225f, 185f); lineTo(135f, 185f)
                cubicTo(112f, 185f, 98f, 195f, 85f, 215f); cubicTo(70f, 238f, 40f, 240f, 30f, 215f)
                cubicTo(15f, 175f, 30f, 88f, 70f, 70f); close()
            }
            drawPath(body, pal.body)
            for ((pad, sh, label) in KEYS) {
                val on = active(pad)
                val fill = if (on) Accent else pal.key
                val cx: Float; val cy: Float
                when (sh) {
                    is RectS -> {
                        drawRoundRect(fill, Offset(sh.x, sh.y), Size(sh.w, sh.h), CornerRadius(sh.r))
                        drawRoundRect(pal.body, Offset(sh.x, sh.y), Size(sh.w, sh.h), CornerRadius(sh.r), style = Stroke(2f))
                        cx = sh.x + sh.w / 2; cy = sh.y + sh.h / 2
                    }
                    is CircS -> {
                        drawCircle(fill, radius = sh.r, center = Offset(sh.x, sh.y))
                        drawCircle(pal.body, radius = sh.r, center = Offset(sh.x, sh.y), style = Stroke(2f))
                        cx = sh.x; cy = sh.y
                    }
                }
                if (label.isNotEmpty()) drawIntoCanvas { c ->
                    paint.color = (if (on) OnAccent else pal.ink).toArgb()
                    paint.textSize = if (pad == Pad.L3 || pad == Pad.R3) 9f else if (pad == Pad.TP) 10f else 11f
                    c.nativeCanvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
        }
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
fun SettingsDialog(vm: MainVm, onClose: () -> Unit) {
    val pal = LocalPal.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Pengaturan", "Settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Bahasa", "Language"), fontSize = 12.sp, color = pal.mute)
                Seg(listOf("id" to "Indonesia", "en" to "English"), Lang.code) { vm.setLang(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Tutup", "Close")) } }
    )
}

// ------------------------------------------------------------------ profil controller

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(vm: MainVm, onClose: () -> Unit) {
    val pal = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveName by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameErr by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(tr("Profil controller", "Controller profiles"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(tr("Simpan setelan tombol dengan nama, lalu muat kembali kapan saja. Untuk mengedit: muat profil, ubah tombolnya, lalu ketuk Simpan perubahan.",
                "Save your button settings under a name and load them any time. To edit: load a profile, change the buttons, then tap Save changes."),
                fontSize = 13.sp, color = pal.mute)
            Button(onClick = { saveName = vm.active ?: "" }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) {
                Text(tr("Simpan setelan saat ini", "Save current settings"))
            }
            if (vm.profiles.isEmpty()) Text(tr("Belum ada profil tersimpan.", "No saved profiles yet."), color = pal.mute, fontSize = 13.sp)
            for ((name, m) in vm.profiles) {
                val cnt = m.count { it.value.motors.isNotEmpty() }
                Column(Modifier.fillMaxWidth().border(1.dp, if (name == vm.active) Accent else pal.line, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, fontWeight = FontWeight.Bold)
                    Text("$cnt " + tr("tombol diatur", "buttons configured") + (if (name == vm.active) tr(" • aktif", " • active") else ""),
                        fontSize = 12.sp, color = pal.mute)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { vm.loadProfile(name); onClose() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) { Text(tr("Muat", "Load"), fontSize = 13.sp, maxLines = 1) }
                        OutlinedButton(onClick = { renaming = name; renameText = name; renameErr = false }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)) { Text(tr("Ganti nama", "Rename"), fontSize = 13.sp, maxLines = 1) }
                        OutlinedButton(onClick = { deleting = name }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)) { Text(tr("Hapus", "Delete"), fontSize = 13.sp, color = pal.bad, maxLines = 1) }
                    }
                }
            }
        }
    }

    saveName?.let { txt ->
        AlertDialog(
            onDismissRequest = { saveName = null },
            title = { Text(tr("Simpan profil", "Save profile")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = txt, onValueChange = { saveName = it }, singleLine = true,
                        label = { Text(tr("Nama profil", "Profile name")) })
                    if (txt.trim() in vm.profiles) Text(tr("Nama sudah ada, profil akan ditimpa.", "Name already exists; the profile will be overwritten."),
                        fontSize = 12.sp, color = pal.mute)
                }
            },
            confirmButton = { TextButton(enabled = txt.isNotBlank(), onClick = { vm.saveProfile(txt); saveName = null }) { Text(tr("Simpan", "Save")) } },
            dismissButton = { TextButton(onClick = { saveName = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    renaming?.let { old ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Ganti nama profil", "Rename profile")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it; renameErr = false }, singleLine = true,
                        label = { Text(tr("Nama baru", "New name")) })
                    if (renameErr) Text(tr("Nama kosong atau sudah dipakai profil lain.", "Name is empty or already used by another profile."),
                        fontSize = 12.sp, color = pal.bad)
                }
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { if (vm.renameProfile(old, renameText)) renaming = null else renameErr = true }) { Text(tr("Simpan", "Save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Hapus profil?", "Delete profile?")) },
            text = { Text(tr("Profil \"$n\" akan dihapus permanen.", "Profile \"$n\" will be deleted permanently.")) },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(n); deleting = null }) { Text(tr("Hapus", "Delete"), color = pal.bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}

// ------------------------------------------------------------------ panel pengaturan tombol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySheet(
    pad: Pad, initial: KeyCfg?, ports: List<Dev?>, ev3On: Boolean,
    onSave: (KeyCfg) -> Unit, onReset: () -> Unit, onClose: () -> Unit
) {
    val pal = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var toggle by remember { mutableStateOf(initial?.toggle ?: false) }
    val motors = remember { mutableStateListOf<Motor>().also { it.addAll(initial?.motors ?: listOf(Motor())) } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = pal.card) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(pad.label, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(when (pad.kind) {
                Kind.BTN -> tr("Tombol digital", "Digital button")
                Kind.TRIG -> tr("Trigger analog, kecepatan mengikuti tekanan", "Analog trigger, speed follows how far it is pressed")
                Kind.STICK -> tr("Stik analog, kecepatan mengikuti sudut", "Analog stick, speed follows how far it is tilted")
            }, fontSize = 13.sp, color = pal.mute)

            if (pad.kind == Kind.BTN) {
                Row(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Mode toggle", "Toggle mode"))
                        Text(tr("Tekan sekali untuk menyala, tekan lagi untuk berhenti", "Press once to start, press again to stop"), fontSize = 12.sp, color = pal.mute)
                    }
                    Switch(checked = toggle, onCheckedChange = { toggle = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = pal.ok, checkedThumbColor = Color.White))
                }
            }

            motors.forEachIndexed { i, m ->
                MotorCard(i, m, pad.kind, ports, ev3On, motors.size > 1, { motors[i] = it }, { motors.removeAt(i) })
            }
            if (motors.size < 4) OutlinedButton(onClick = {
                val used = motors.map { it.port }
                var n = Motor(port = (0..3).firstOrNull { it !in used } ?: 0)
                if (pad.kind == Kind.STICK) n = n.copy(axis = if (motors.last().axis == Axis.V) Axis.H else Axis.V)
                motors.add(n)
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("+ Tambah motor", "+ Add motor")) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text(tr("Hapus pengaturan", "Clear settings")) }
                Button(onClick = { onSave(KeyCfg(toggle && pad.kind == Kind.BTN, motors.toList())) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) { Text(tr("Simpan", "Save")) }
            }
        }
    }
}

@Composable
private fun MotorCard(
    i: Int, m: Motor, kind: Kind, ports: List<Dev?>, ev3On: Boolean, canRemove: Boolean,
    onChange: (Motor) -> Unit, onRemove: () -> Unit
) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Motor ${i + 1}", fontWeight = FontWeight.Bold)
            if (canRemove) TextButton(onClick = onRemove) { Text(tr("Hapus", "Remove"), color = pal.bad) }
        }
        Lbl(tr("Port motor", "Motor port"))
        Seg(listOf(0 to "A", 1 to "B", 2 to "C", 3 to "D"), m.port, { ev3On && ports.getOrNull(it) == null }) { onChange(m.copy(port = it)) }
        if (kind == Kind.STICK) {
            Lbl(tr("Digerakkan oleh sumbu", "Driven by axis"))
            Seg(listOf(Axis.H to tr("Horizontal (kiri-kanan)", "Horizontal (left-right)"), Axis.V to tr("Vertikal (atas-bawah)", "Vertical (up-down)")), m.axis) { onChange(m.copy(axis = it)) }
            Lbl(tr("Tiap motor punya sumbu sendiri. Tambah motor lain untuk sumbu yang berbeda.", "Each motor has its own axis. Add another motor for a different axis."))
        }
        Lbl(tr("Arah putaran", "Rotation direction"))
        Seg(listOf(true to tr("↻ Searah jarum jam", "↻ Clockwise"), false to tr("↺ Berlawanan", "↺ Counterclockwise")), m.cw) { onChange(m.copy(cw = it)) }
        if (kind == Kind.STICK) Lbl(tr("Berlaku saat stik ke kanan atau atas. Ke kiri atau bawah otomatis berlawanan.",
            "Applies when the stick moves right or up. Left or down reverses automatically."))
        if (kind != Kind.BTN) {
            Lbl(tr("Mode kecepatan", "Speed mode"))
            Seg(listOf(SpeedMode.FIXED to tr("Tetap", "Fixed"), SpeedMode.PROP to tr("Ikuti gerakan", "Follow movement")), m.speed) { onChange(m.copy(speed = it)) }
            Lbl(if (m.speed == SpeedMode.PROP) tr("Makin jauh digerakkan, makin cepat. Power di bawah jadi batas maksimum.",
                "The further it moves, the faster the motor. Power below becomes the maximum.")
            else tr("Begitu digerakkan, motor langsung berputar di power yang ditentukan.", "As soon as it moves, the motor runs at the power you set."))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Lbl(if (kind != Kind.BTN && m.speed == SpeedMode.PROP) tr("Power maksimum", "Maximum power") else "Power")
            Text("${m.power}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(value = m.power.toFloat(), onValueChange = { onChange(m.copy(power = it.roundToInt())) }, valueRange = 1f..100f)
        Lbl(tr("Saat dilepas", "When released"))
        Seg(listOf(StopMode.HOLD to "Hold", StopMode.BRAKE to "Brake", StopMode.FLOAT to "Float"), m.stop) { onChange(m.copy(stop = it)) }
    }
}

@Composable private fun Lbl(t: String) = Text(t, fontSize = 12.sp, color = LocalPal.current.mute)

@Composable
private fun <T> Seg(opts: List<Pair<T, String>>, sel: T, dim: (T) -> Boolean = { false }, onSel: (T) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { (v, label) ->
            val a = v == sel
            val sh = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).alpha(if (dim(v) && !a) 0.45f else 1f).clip(sh)
                    .background(if (a) Accent else Color.Transparent)
                    .border(1.dp, if (a) Accent else pal.line, sh)
                    .clickable { onSel(v) }.padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, textAlign = TextAlign.Center,
                    color = if (a) OnAccent else pal.ink, fontWeight = if (a) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
