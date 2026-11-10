package com.example.ev3pad

import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.activity.compose.BackHandler
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

private const val PW = 360f
private const val PH = 250f

sealed class KShape
class RectS(val x: Float, val y: Float, val w: Float, val h: Float, val r: Float) : KShape()
class CircS(val x: Float, val y: Float, val r: Float) : KShape()

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
            title = p.label, kind = p.kind, initial = vm.map[p], vm = vm, nameInit = null,
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
                SmallBtn(tr("Profil", "Profiles")) { onProfiles(kind) }
            }
            if (vm.tab == 0) PadSection(vm, Modifier.weight(1f).fillMaxWidth(), onKey)
            else VirtualSection(vm, Modifier.weight(1f).fillMaxWidth(), onItem)
        }
    }
}

@Composable
private fun PadSection(vm: MainVm, modifier: Modifier, onKey: (Pad) -> Unit) {
    val pal = LocalPal.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val w = minOf(maxWidth, maxHeight * (PW / PH))
            Box(Modifier.width(w), contentAlignment = Alignment.Center) {
                ControllerView(Modifier.fillMaxWidth(), vm.padOn, { vm.map[it]?.motors?.isNotEmpty() == true }, onKey)
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
                SmallBtn(tr("⛶ Layar penuh", "⛶ Fullscreen")) { vm.fullscreen = true }
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
fun ControllerView(modifier: Modifier, enabled: Boolean, active: (Pad) -> Boolean, onTap: (Pad) -> Unit) {
    val pal = LocalPal.current
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
        }
    }
    Canvas(
        modifier.aspectRatio(PW / PH).alpha(if (enabled) 1f else 0.35f).pointerInput(enabled) {
            detectTapGestures { o ->
                if (!enabled) return@detectTapGestures
                val s = size.width / PW
                val x = o.x / s; val y = o.y / s
                KEYS.asReversed().firstOrNull { hit(it.second, x, y) }?.let { onTap(it.first) }
            }
        }
    ) {
        scale(size.width / PW, size.width / PW, pivot = Offset.Zero) {
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
                val fill = if (on) pal.acc else pal.key
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
                    paint.color = (if (on) pal.onAcc else pal.ink).toArgb()
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
fun SettingsDialog(vm: MainVm, onAbout: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(tr("Pengaturan", "Settings")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Lbl(tr("Bahasa", "Language"))
                Seg(listOf("id" to "Indonesia", "en" to "English"), Lang.code) { vm.setLang(it) }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text("About this app", color = LocalPal.current.ink) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(tr("Tutup", "Close")) } }
    )
}
