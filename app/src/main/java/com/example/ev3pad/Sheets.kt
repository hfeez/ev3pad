package com.example.ev3pad

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ------------------------------------------------------------------ pengaturan satu tombol / elemen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeySheet(
    title: String, kind: Kind, initial: KeyCfg?, vm: MainVm, nameInit: String?, deleteLabel: String,
    onSave: (KeyCfg, String) -> Unit, onDelete: () -> Unit, onClose: () -> Unit
) {
    val pal = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var toggle by remember { mutableStateOf(initial?.toggle ?: false) }
    var spring by remember { mutableStateOf(initial?.spring ?: true) }
    var name by remember { mutableStateOf(nameInit ?: "") }
    val motors = remember { mutableStateListOf<Motor>().also { it.addAll(initial?.motors ?: listOf(Motor(speed = if (kind == Kind.BTN) SpeedMode.FIXED else SpeedMode.PROP))) } }
    val conds = remember { mutableStateListOf<Cond>().also { it.addAll(initial?.conds ?: emptyList()) } }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = pal.bg2) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (nameInit != null) OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(tr("Nama elemen", "Element name")) }, modifier = Modifier.fillMaxWidth())
            else Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Lbl(when (kind) {
                Kind.BTN -> tr("Tombol digital", "Digital button")
                Kind.TRIG -> tr("Trigger analog, kecepatan mengikuti tekanan", "Analog trigger, speed follows how far it is pressed")
                Kind.STICK -> tr("Stik analog, kecepatan mengikuti sudut", "Analog stick, speed follows how far it is tilted")
                Kind.SLIDER -> tr("Slider virtual", "Virtual slider")
                Kind.ANALOG -> tr("Analog virtual", "Virtual analog")
            })

            if (kind == Kind.BTN) SwitchRow(tr("Mode toggle", "Toggle mode"), tr("Tekan sekali untuk menyala, tekan lagi untuk berhenti", "Press once to start, press again to stop"), toggle) { toggle = it }
            if (kind == Kind.SLIDER) SwitchRow(tr("Kembali ke tengah", "Return to center"), tr("Slider kembali ke 0 saat dilepas", "Slider returns to 0 when released"), spring) { spring = it }

            motors.forEachIndexed { i, m ->
                MotorCard(i, m, kind, vm, motors.size > 1, { motors[i] = it }, { motors.removeAt(i) })
            }
            if (motors.size < 4) OutlinedButton(onClick = {
                val used = motors.map { it.port }
                var n = Motor(port = (0..3).firstOrNull { it !in used } ?: 0, speed = motors.last().speed)
                if (kind == Kind.STICK || kind == Kind.ANALOG) n = n.copy(axis = if (motors.last().axis == Axis.V) Axis.H else Axis.V)
                motors.add(n)
            }, modifier = Modifier.fillMaxWidth()) { Text(tr("+ Tambah motor", "+ Add motor"), color = pal.ink) }

            CondCard(conds, kind, vm)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(deleteLabel, color = pal.ink, maxLines = 1) }
                PriButton(tr("Simpan", "Save"), Modifier.weight(1f)) {
                    onSave(KeyCfg(toggle && kind == Kind.BTN, motors.toList(), conds.toList(), spring), name)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(t: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(t); Lbl(sub) }
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = pal.ok, checkedThumbColor = Color.White))
    }
}

@Composable
private fun MotorCard(i: Int, m: Motor, kind: Kind, vm: MainVm, canRemove: Boolean, onChange: (Motor) -> Unit, onRemove: () -> Unit) {
    val pal = LocalPal.current
    val an = kind != Kind.BTN
    val ev3On = vm.ev3 == LinkState.ON
    Column(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Motor ${i + 1}", fontWeight = FontWeight.SemiBold)
            if (canRemove) TextButton(onClick = onRemove) { Text(tr("Hapus", "Remove"), color = pal.bad) }
        }
        Lbl(tr("Port motor", "Motor port"))
        Seg(listOf(0 to "A", 1 to "B", 2 to "C", 3 to "D"), m.port, { ev3On && vm.motorPorts.getOrNull(it) == null }) { onChange(m.copy(port = it)) }
        if (kind == Kind.STICK || kind == Kind.ANALOG) {
            Lbl(tr("Digerakkan oleh sumbu", "Driven by axis"))
            Seg(listOf(Axis.H to tr("Horizontal (kiri-kanan)", "Horizontal (left-right)"), Axis.V to tr("Vertikal (atas-bawah)", "Vertical (up-down)")), m.axis) { onChange(m.copy(axis = it)) }
            Lbl(tr("Tiap motor punya sumbu sendiri. Tambah motor lain untuk sumbu yang berbeda.", "Each motor has its own axis. Add another motor for a different axis."))
        }
        Lbl(tr("Arah putaran", "Rotation direction"))
        Seg(listOf(true to tr("↻ Searah jarum jam", "↻ Clockwise"), false to tr("↺ Berlawanan", "↺ Counterclockwise")), m.cw) { onChange(m.copy(cw = it)) }
        if (an && kind != Kind.TRIG) Lbl(tr("Berlaku saat bergerak ke kanan atau atas. Ke kiri atau bawah otomatis berlawanan.", "Applies when moved right or up. Left or down reverses automatically."))
        if (an) {
            Lbl(tr("Mode kecepatan", "Speed mode"))
            Seg(listOf(SpeedMode.FIXED to tr("Tetap", "Fixed"), SpeedMode.PROP to tr("Ikuti gerakan", "Follow movement")), m.speed) { onChange(m.copy(speed = it)) }
            Lbl(if (m.speed == SpeedMode.PROP) tr("Makin jauh digerakkan, makin cepat. Power di bawah jadi batas maksimum.", "The further it moves, the faster the motor. Power below becomes the maximum.")
            else tr("Begitu digerakkan, motor langsung berputar di power yang ditentukan.", "As soon as it moves, the motor runs at the power you set."))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Lbl(if (an && m.speed == SpeedMode.PROP) tr("Power maksimum", "Maximum power") else "Power")
            Text("${m.power}%", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Slider(value = m.power.toFloat(), onValueChange = { onChange(m.copy(power = it.roundToInt())) }, valueRange = 1f..100f)
        Lbl(tr("Saat dilepas", "When released"))
        Seg(listOf(StopMode.HOLD to "Hold", StopMode.BRAKE to "Brake", StopMode.FLOAT to "Float"), m.stop) { onChange(m.copy(stop = it)) }
    }
}

// ------------------------------------------------------------------ syarat sensor

private fun defaultCond(vm: MainVm, sensor: Int, keep: Cond? = null): Cond {
    val ms = vm.sensorPorts[sensor]?.let { modesOf(it) }.orEmpty()
    val m = ms.firstOrNull() ?: return Cond(sensor = sensor)
    return condFor(sensor, m, keep?.enable ?: true)
}

private fun condFor(sensor: Int, m: SMode, enable: Boolean) = Cond(
    enable, sensor, m.key,
    if (m.type == 0) Cmp.GT else Cmp.EQ,
    when (m.key) { "dist" -> 20f; "dist_in" -> 8f; "refl", "amb", "prox" -> 50f; "ang", "rate" -> 0f; else -> 1f }
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CondCard(conds: SnapshotStateList<Cond>, kind: Kind, vm: MainVm) {
    val pal = LocalPal.current
    val sens = (0..3).filter { i -> vm.sensorPorts[i]?.let { modesOf(it).isNotEmpty() } == true }
    if (vm.ev3 != LinkState.ON || sens.isEmpty()) { if (conds.isEmpty()) return }
    val kw = when (kind) {
        Kind.BTN -> tr("tombol", "button"); Kind.SLIDER -> "slider"; Kind.ANALOG -> "analog"
        Kind.TRIG -> "trigger"; Kind.STICK -> tr("stik", "stick")
    }
    Column(Modifier.fillMaxWidth().border(1.dp, pal.line, RoundedCornerShape(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Syarat sensor", "Sensor condition"), fontWeight = FontWeight.SemiBold)
        Lbl(tr("Kontrol hanya aktif atau nonaktif tergantung nilai sensor.", "Control is enabled or disabled depending on a sensor value."))
        conds.forEachIndexed { idx, c ->
            val dev = vm.sensorPorts[c.sensor]
            val ms = dev?.let { modesOf(it) }.orEmpty()
            val m = ms.firstOrNull { it.key == c.field } ?: ms.firstOrNull()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dev == null || m == null) {
                    Text(tr("Sensor ${c.sensor + 1} tidak terhubung", "Sensor ${c.sensor + 1} not connected"), color = pal.mute, modifier = Modifier.padding(vertical = 9.dp))
                } else {
                    Drop(listOf(true to tr("Aktifkan", "Enable"), false to tr("Nonaktifkan", "Disable")), c.enable) { conds[idx] = c.copy(enable = it) }
                    Text("$kw " + tr("jika sensor", "if sensor"), modifier = Modifier.padding(vertical = 9.dp), fontSize = 13.sp)
                    Drop(sens.map { it to "${it + 1} · ${vm.sensorPorts[it]!!.label()}" }, c.sensor) { s -> conds[idx] = defaultCond(vm, s, c) }
                    if (ms.size > 1) Drop(ms.map { it.key to it.label() }, m.key) { k -> conds[idx] = condFor(c.sensor, ms.first { it.key == k }, c.enable) }
                    else Text(m.label(), modifier = Modifier.padding(vertical = 9.dp), fontSize = 13.sp)
                    val ops = if (m.type == 0) listOf(Cmp.GT, Cmp.LT, Cmp.EQ, Cmp.LE, Cmp.GE) else listOf(Cmp.EQ, Cmp.NE)
                    Drop(ops.map { it to it.sym }, c.op) { conds[idx] = c.copy(op = it) }
                    when (m.type) {
                        0 -> {
                            NumField(m.key + idx, c.value) { conds[idx] = c.copy(value = it) }
                            if (m.unit.isNotEmpty()) Text(m.unit, modifier = Modifier.padding(vertical = 9.dp), fontSize = 13.sp)
                        }
                        1 -> Drop((1..7).map { it.toFloat() to colorName(it) }, c.value) { conds[idx] = c.copy(value = it) }
                        else -> Drop(listOf(1f to tr("ditekan", "pressed"), 0f to tr("dilepas", "released")), c.value) { conds[idx] = c.copy(value = it) }
                    }
                }
                TextButton(onClick = { conds.removeAt(idx) }) { Text("×", color = pal.bad, fontSize = 18.sp) }
            }
        }
        if (sens.isNotEmpty()) OutlinedButton(onClick = { conds.add(defaultCond(vm, sens[0])) }) {
            Text(tr("+ Tambah syarat sensor", "+ Add sensor condition"), color = pal.ink, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NumField(key: String, value: Float, onChange: (Float) -> Unit) {
    var txt by remember(key) { mutableStateOf(fmtNum(value)) }
    OutlinedTextField(
        value = txt, onValueChange = { txt = it; it.toFloatOrNull()?.let(onChange) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), textStyle = TextStyle(fontSize = 14.sp),
        modifier = Modifier.width(96.dp)
    )
}

// ------------------------------------------------------------------ profil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesSheet(vm: MainVm, kind: String, onClose: () -> Unit) {
    val pal = LocalPal.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var saveName by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameErr by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val list = vm.profiles[kind] ?: emptyMap()
    val virt = kind == "virt"

    ModalBottomSheet(onDismissRequest = onClose, sheetState = state, containerColor = pal.bg2) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(if (virt) tr("Profil virtual", "Virtual profiles") else tr("Profil controller", "Controller profiles"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Lbl(
                if (virt) tr("Simpan layout dan setelan elemen dengan nama, lalu muat kembali kapan saja. Untuk mengedit: muat profil, ubah, lalu ketuk Simpan perubahan.",
                    "Save the layout and element settings under a name and load them any time. To edit: load a profile, change it, then tap Save changes.")
                else tr("Simpan setelan tombol dengan nama, lalu muat kembali kapan saja. Untuk mengedit: muat profil, ubah tombolnya, lalu ketuk Simpan perubahan.",
                    "Save your button settings under a name and load them any time. To edit: load a profile, change the buttons, then tap Save changes.")
            )
            PriButton(tr("Simpan setelan saat ini", "Save current settings"), Modifier.fillMaxWidth()) { saveName = vm.active[kind] ?: "" }
            if (list.isEmpty()) Lbl(tr("Belum ada profil tersimpan.", "No saved profiles yet."))
            for ((name, json) in list) {
                val cnt = vm.countOf(kind, json)
                Column(Modifier.fillMaxWidth().border(1.dp, if (name == vm.active[kind]) pal.acc else pal.line, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(name, fontWeight = FontWeight.SemiBold)
                    Lbl("$cnt " + (if (virt) tr("elemen", "elements") else tr("tombol diatur", "buttons configured")) + (if (name == vm.active[kind]) tr(" • aktif", " • active") else ""))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PriButton(tr("Muat", "Load"), Modifier.weight(1f)) { vm.loadProfile(kind, name); onClose() }
                        OutlinedButton(onClick = { renaming = name; renameText = name; renameErr = false }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(tr("Ganti nama", "Rename"), fontSize = 13.sp, maxLines = 1, color = pal.ink)
                        }
                        OutlinedButton(onClick = { deleting = name }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(tr("Hapus", "Delete"), fontSize = 13.sp, maxLines = 1, color = pal.bad)
                        }
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
                    OutlinedTextField(value = txt, onValueChange = { saveName = it }, singleLine = true, label = { Text(tr("Nama profil", "Profile name")) })
                    if (txt.trim() in list) Lbl(tr("Nama sudah ada, profil akan ditimpa.", "Name already exists; the profile will be overwritten."))
                }
            },
            confirmButton = { TextButton(enabled = txt.isNotBlank(), onClick = { vm.saveProfile(kind, txt); saveName = null }) { Text(tr("Simpan", "Save")) } },
            dismissButton = { TextButton(onClick = { saveName = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    renaming?.let { old ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(tr("Ganti nama profil", "Rename profile")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = renameText, onValueChange = { renameText = it; renameErr = false }, singleLine = true, label = { Text(tr("Nama baru", "New name")) })
                    if (renameErr) Text(tr("Nama kosong atau sudah dipakai profil lain.", "Name is empty or already used by another profile."), fontSize = 12.sp, color = pal.bad)
                }
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { if (vm.renameProfile(kind, old, renameText)) renaming = null else renameErr = true }) { Text(tr("Simpan", "Save")) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
    deleting?.let { n ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(tr("Hapus profil?", "Delete profile?")) },
            text = { Text(tr("Profil \"$n\" akan dihapus permanen.", "Profile \"$n\" will be deleted permanently.")) },
            confirmButton = { TextButton(onClick = { vm.deleteProfile(kind, n); deleting = null }) { Text(tr("Hapus", "Delete"), color = pal.bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Batal", "Cancel")) } }
        )
    }
}
