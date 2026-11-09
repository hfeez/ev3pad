package com.example.ev3pad

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
import android.bluetooth.BluetoothDevice
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
    val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        Modifier.fillMaxSize().background(pal.bg).safeDrawingPadding().padding(12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        if (land) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TopPanel(vm, { picker = true }, Modifier.weight(0.42f).verticalScroll(rememberScrollState()))
                PadCard(vm, Modifier.weight(0.58f).fillMaxHeight()) { sheet = it }
            }
        } else {
            Column(Modifier.widthIn(max = 720.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TopPanel(vm, { picker = true }, Modifier.fillMaxWidth())
                PadCard(vm, Modifier.fillMaxWidth().weight(1f)) { sheet = it }
            }
        }
    }

    if (picker) DevicePicker(vm.bonded(), onPick = { picker = false; vm.connect(it) }, onClose = { picker = false })
    sheet?.let { p ->
        KeySheet(p, vm.map[p], vm.motorPorts, vm.ev3 == LinkState.ON,
            onSave = { vm.saveKey(p, it); sheet = null },
            onReset = { vm.saveKey(p, null); sheet = null },
            onClose = { sheet = null })
    }
    vm.error?.let { msg ->
        AlertDialog(onDismissRequest = { vm.error = null }, title = { Text("Perhatian") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.error = null }) { Text("OK") } })
    }
}

@Composable
fun TopPanel(vm: MainVm, onConnect: () -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
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
                            LinkState.ON -> "Terhubung ke ${vm.ev3Name}"
                            LinkState.WAIT -> "Menghubungkan…"
                            else -> "Belum terhubung"
                        }, fontSize = 13.sp, color = pal.mute)
                    }
                }
                if (vm.ev3 == LinkState.ON) OutlinedButton(onClick = { vm.disconnect() }) { Text("Putuskan") }
                else Button(onClick = onConnect, enabled = vm.ev3 == LinkState.OFF,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) { Text("Hubungkan EV3") }
            }
            val on = vm.ev3 == LinkState.ON
            PortRow("Port motor", listOf("A", "B", "C", "D"), vm.motorPorts, on)
            PortRow("Port sensor", listOf("1", "2", "3", "4"), vm.sensorPorts, on)
        }
    }
}

@Composable
private fun PortRow(title: String, names: List<String>, types: List<String?>, on: Boolean) {
    val pal = LocalPal.current
    Text(title, fontSize = 12.sp, color = pal.mute, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEachIndexed { i, n ->
            val t = if (on) types.getOrNull(i) else null
            Column(
                Modifier.weight(1f).border(1.dp, if (t != null) pal.ok else pal.line, RoundedCornerShape(10.dp)).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (t != null) pal.ok else pal.line, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(n, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Text(if (!on) "–" else t ?: "kosong", fontSize = 11.sp, color = pal.mute, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}

@Composable
fun PadCard(vm: MainVm, modifier: Modifier, onTap: (Pad) -> Unit) {
    val pal = LocalPal.current
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = pal.card),
        border = BorderStroke(1.dp, pal.line)) {
        Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val w = minOf(maxWidth, maxHeight * (W / H))
                Box(Modifier.width(w), contentAlignment = Alignment.Center) {
                    ControllerView(Modifier.fillMaxWidth(), vm.padOn, { vm.map[it]?.motors?.isNotEmpty() == true }, onTap)
                    if (!vm.padOn) NoPad()
                }
            }
            val n = vm.map.count { it.value.motors.isNotEmpty() }
            Text(
                if (vm.padOn) (if (n > 0) "$n tombol sudah diatur" else "Ketuk tombol untuk mengatur")
                else "Pasangkan PS4 lewat Bluetooth (tahan PS + Share sampai lampu berkedip)",
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
        Text("Controller PS4 belum terhubung", fontSize = 14.sp, modifier = Modifier.background(pal.card, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp))
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
        title = { Text("Pilih EV3") },
        text = {
            if (devs.isEmpty()) Text("Belum ada perangkat Bluetooth yang dipasangkan. Pasangkan EV3 dulu di Pengaturan Bluetooth (PIN default 1234), lalu kembali ke sini.")
            else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                devs.forEach { d -> OutlinedButton(onClick = { onPick(d) }, modifier = Modifier.fillMaxWidth()) { Text(d.safeName()) } }
            }
        },
        confirmButton = { TextButton(onClick = { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Pengaturan Bluetooth") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Batal") } }
    )
}

// ------------------------------------------------------------------ panel pengaturan tombol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySheet(
    pad: Pad, initial: KeyCfg?, ports: List<String?>, ev3On: Boolean,
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
                Kind.BTN -> "Tombol digital"
                Kind.TRIG -> "Trigger analog, kecepatan mengikuti tekanan"
                Kind.STICK -> "Stik analog, kecepatan mengikuti sudut"
            }, fontSize = 13.sp, color = pal.mute)

            if (pad.kind == Kind.BTN) {
                Row(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Mode toggle")
                        Text("Tekan sekali untuk menyala, tekan lagi untuk berhenti", fontSize = 12.sp, color = pal.mute)
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
            }, modifier = Modifier.fillMaxWidth()) { Text("+ Tambah motor") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Hapus pengaturan") }
                Button(onClick = { onSave(KeyCfg(toggle && pad.kind == Kind.BTN, motors.toList())) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent)) { Text("Simpan") }
            }
        }
    }
}

@Composable
private fun MotorCard(
    i: Int, m: Motor, kind: Kind, ports: List<String?>, ev3On: Boolean, canRemove: Boolean,
    onChange: (Motor) -> Unit, onRemove: () -> Unit
) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Motor ${i + 1}", fontWeight = FontWeight.Bold)
            if (canRemove) TextButton(onClick = onRemove) { Text("Hapus", color = pal.bad) }
        }
        Lbl("Port motor")
        Seg(listOf(0 to "A", 1 to "B", 2 to "C", 3 to "D"), m.port, { ev3On && ports.getOrNull(it) == null }) { onChange(m.copy(port = it)) }
        if (kind == Kind.STICK) {
            Lbl("Digerakkan oleh sumbu")
            Seg(listOf(Axis.H to "Horizontal (kiri-kanan)", Axis.V to "Vertikal (atas-bawah)"), m.axis) { onChange(m.copy(axis = it)) }
            Hint("Tiap motor punya sumbu sendiri. Tambah motor lain untuk sumbu yang berbeda.")
        }
        Lbl("Arah putaran")
        Seg(listOf(true to "↻ Searah jarum jam", false to "↺ Berlawanan"), m.cw) { onChange(m.copy(cw = it)) }
        if (kind == Kind.STICK) Hint("Berlaku saat stik ke kanan atau atas. Ke kiri atau bawah otomatis berlawanan.")
        if (kind != Kind.BTN) {
            Lbl("Mode kecepatan")
            Seg(listOf(SpeedMode.FIXED to "Tetap", SpeedMode.PROP to "Ikuti gerakan"), m.speed) { onChange(m.copy(speed = it)) }
            Hint(if (m.speed == SpeedMode.PROP) "Makin jauh digerakkan, makin cepat. Power di bawah jadi batas maksimum."
            else "Begitu digerakkan, motor langsung berputar di power yang ditentukan.")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Lbl(if (kind != Kind.BTN && m.speed == SpeedMode.PROP) "Power maksimum" else "Power")
            Text("${m.power}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(value = m.power.toFloat(), onValueChange = { onChange(m.copy(power = it.roundToInt())) }, valueRange = 1f..100f)
        Lbl("Saat dilepas")
        Seg(listOf(StopMode.HOLD to "Hold", StopMode.BRAKE to "Brake", StopMode.FLOAT to "Float"), m.stop) { onChange(m.copy(stop = it)) }
    }
}

@Composable private fun Lbl(t: String) = Text(t, fontSize = 12.sp, color = LocalPal.current.mute)
@Composable private fun Hint(t: String) = Text(t, fontSize = 12.sp, color = LocalPal.current.mute)

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
