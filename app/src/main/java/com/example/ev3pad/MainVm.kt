package com.example.ev3pad

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.BatteryState
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

enum class LinkState { OFF, WAIT, ON }

class MainVm(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("ev3pad", Context.MODE_PRIVATE)

    // ---- konfigurasi ----
    var map by mutableStateOf(Store.loadMap(prefs)); private set                 // mode controller
    var items by mutableStateOf(Store.loadItems(prefs)); private set             // mode virtual
    var profiles by mutableStateOf(Store.loadProfiles(prefs)); private set
    var active by mutableStateOf(Store.loadActive(prefs)); private set

    // ---- UI ----
    var tab by mutableStateOf(0); private set          // 0 controller, 1 virtual
    var edit by mutableStateOf(true); private set
    var fullscreen by mutableStateOf(false)
    var error by mutableStateOf<Msg?>(null)

    // ---- EV3 ----
    var ev3 by mutableStateOf(LinkState.OFF); private set
    var ev3Name by mutableStateOf(""); private set
    var motorPorts by mutableStateOf<List<Dev?>>(List(4) { null }); private set
    var sensorPorts by mutableStateOf<List<Dev?>>(List(4) { null }); private set
    var sensorModes by mutableStateOf<List<Int>>(List(4) { 0 }); private set          // jenis ukuran yang ditampilkan
    var sensorData by mutableStateOf<List<Map<String, Float>>>(List(4) { emptyMap() }); private set
    var speeds by mutableStateOf<List<Int>>(List(4) { 0 }); private set               // persen kecepatan terkirim
    var battery by mutableStateOf<Int?>(null); private set                             // persen baterai EV3

    // ---- input ----
    var padOn by mutableStateOf(false); private set
    var ctype by mutableStateOf(CType.DS4); private set                                // jenis controller aktif
    var ctypePref by mutableStateOf(prefs.getString("ctype", "auto") ?: "auto"); private set
    var padBat by mutableStateOf<Int?>(null); private set                              // baterai controller (Android 12+)
    var padChg by mutableStateOf(false); private set
    var padKeys by mutableStateOf<Set<Pad>>(emptySet()); private set                   // tombol yang sedang ditekan (untuk gambar)
    var padAx by mutableStateOf(Axes()); private set
    var testMode by mutableStateOf(false); private set
    private var detected = CType.DS4
    private var padDevId = -1
    var vin by mutableStateOf<Map<Long, VIn>>(emptyMap()); private set
    var vToggle by mutableStateOf<Set<Long>>(emptySet()); private set
    @Volatile private var snap = PadSnap()
    /** Lebar panggung virtual saat ini; diisi oleh UI (VirtualStage). */
    @Volatile var stageW: Float = STAGE_W

    private var link: Ev3Link? = null
    private var loop: Job? = null

    init {
        prefs.getString("lang", null)?.let { Lang.code = it }
        active = active.mapValues { (k, v) -> if (v != null && profiles[k]?.containsKey(v) == true) v else null }
    }

    fun setLang(c: String) { Lang.code = c; prefs.edit().putString("lang", c).apply() }

    // ===================== navigasi =====================
    private fun clearInputs() { vin = emptyMap(); vToggle = emptySet() }
    fun selectTab(t: Int) { tab = t; clearInputs() }
    fun changeEdit(e: Boolean) { edit = e; clearInputs() }
    fun setVin(id: Long, v: VIn) { vin = vin + (id to v) }

    // ===================== mode controller =====================
    fun saveKey(p: Pad, cfg: KeyCfg?) {
        map = map.toMutableMap().also { if (cfg == null || cfg.motors.isEmpty()) it.remove(p) else it[p] = cfg }
        Store.saveMap(prefs, map)
    }

    // ===================== mode virtual =====================
    private fun saveItems() = Store.saveItems(prefs, items)

    fun addItem(t: VType): Long {
        val ws = stageW
        val id = (items.maxOfOrNull { it.id } ?: 0L) + 1
        val n = items.size
        val (w, h) = when (t) { VType.BTN -> 90f to 90f; VType.SLIDER -> 240f to 64f; VType.ANALOG -> 150f to 150f }
        val base = when (t) { VType.BTN -> tr("Tombol", "Button"); VType.SLIDER -> "Slider"; VType.ANALOG -> "Analog" }
        val off = 24f + 16f * (n % 6)
        val cfg = KeyCfg(motors = listOf(Motor(speed = if (t == VType.BTN) SpeedMode.FIXED else SpeedMode.PROP)))
        items = items + VItem(id, t, "$base ${n + 1}", off.coerceAtMost(maxOf(0f, ws - w)), off.coerceAtMost(STAGE_H - h), w, h, cfg)
        saveItems()
        return id
    }

    fun moveItem(id: Long, dx: Float, dy: Float) {
        val ws = stageW
        items = items.map {
            if (it.id != id) it else it.copy(
                x = (it.x + (if (it.ar) -dx else dx)).coerceIn(0f, maxOf(0f, ws - it.w)),
                y = (it.y + dy).coerceIn(0f, maxOf(0f, STAGE_H - it.h))
            )
        }
    }

    fun resizeItem(id: Long, dw: Float, dh: Float) {
        val ws = stageW
        items = items.map {
            if (it.id != id) it else {
                val left = if (it.ar) ws - it.x - it.w else it.x
                val maxW = maxOf(56f, ws - left); val maxH = maxOf(56f, STAGE_H - it.y)
                if (it.type == VType.ANALOG) {      // analog dipertahankan lingkaran
                    val s = (it.w + (dw + dh) / 2f).coerceIn(72f, maxOf(72f, minOf(maxW, maxH)))
                    it.copy(w = s, h = s, x = if (it.ar) ws - left - s else it.x)
                } else {
                    val w = (it.w + dw).coerceIn(56f, maxW); val h = (it.h + dh).coerceIn(56f, maxH)
                    it.copy(w = w, h = h, x = if (it.ar) ws - left - w else it.x)
                }
            }
        }
    }

    /** Rapikan ke grid 8 satuan setelah geser/resize, pilih jangkar kiri/kanan terdekat, lalu simpan. */
    fun snapItem(id: Long) {
        val ws = stageW
        fun g(v: Float) = (v / 8f).roundToInt() * 8f
        items = items.map {
            if (it.id != id) it else {
                val w = g(it.w).coerceAtLeast(if (it.type == VType.ANALOG) 72f else 56f)
                val h = if (it.type == VType.ANALOG) w else g(it.h).coerceAtLeast(56f)
                val left0 = if (it.ar) ws - it.x - it.w else it.x
                val left = g(left0).coerceIn(0f, maxOf(0f, ws - w)); val top = g(it.y).coerceIn(0f, maxOf(0f, STAGE_H - h))
                val ar = left + w / 2f > ws / 2f
                it.copy(w = w, h = h, y = top, ar = ar, x = if (ar) ws - left - w else left)
            }
        }
        saveItems()
    }

    fun updateItem(id: Long, name: String, cfg: KeyCfg) {
        items = items.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }, cfg = cfg) else it }
        saveItems()
    }

    fun removeItem(id: Long) { items = items.filter { it.id != id }; saveItems() }

    // ===================== profil (controller & virtual) =====================
    fun snapOf(kind: String): String = if (kind == "ctl") Store.mapToJson(map).toString() else Store.itemsToJson(items).toString()

    fun dirty(kind: String): Boolean {
        val a = active[kind] ?: return false
        return profiles[kind]?.get(a) != snapOf(kind)
    }

    fun countOf(kind: String, json: String): Int = try {
        if (kind == "ctl") JSONObject(json).length() else JSONArray(json).length()
    } catch (e: Exception) { 0 }

    private fun persistProfiles() = Store.saveProfiles(prefs, profiles, active)

    fun saveProfile(kind: String, name: String) {
        val n = name.trim(); if (n.isEmpty()) return
        val m = LinkedHashMap(profiles[kind] ?: emptyMap()); m[n] = snapOf(kind)
        profiles = profiles + (kind to m)
        active = active + (kind to n)
        persistProfiles()
    }

    fun loadProfile(kind: String, name: String) {
        val j = profiles[kind]?.get(name) ?: return
        try {
            if (kind == "ctl") { map = Store.mapFromJson(JSONObject(j)); Store.saveMap(prefs, map) }
            else { items = Store.itemsFromJson(JSONArray(j)); saveItems(); clearInputs() }
            active = active + (kind to name)
            persistProfiles()
        } catch (e: Exception) { }
    }

    fun renameProfile(kind: String, old: String, new: String): Boolean {
        val n = new.trim()
        val cur = profiles[kind] ?: return false
        if (n.isEmpty() || (n != old && n in cur)) return false
        val r = LinkedHashMap<String, String>()
        for ((k, v) in cur) r[if (k == old) n else k] = v
        profiles = profiles + (kind to r)
        if (active[kind] == old) active = active + (kind to n)
        persistProfiles(); return true
    }

    fun deleteProfile(kind: String, name: String) {
        val r = LinkedHashMap(profiles[kind] ?: emptyMap()); r.remove(name)
        profiles = profiles + (kind to r)
        if (active[kind] == name) active = active + (kind to null)
        persistProfiles()
    }

    // ===================== syarat sensor =====================
    fun condOk(c: Cond): Boolean {
        if (ev3 != LinkState.ON) return true
        val d = sensorPorts.getOrNull(c.sensor) ?: return true
        if (modesOf(d).none { it.key == c.field }) return true
        val v = sensorData.getOrNull(c.sensor)?.get(c.field) ?: return false   // belum terbaca: aman = tidak aktif
        val r = when (c.op) {
            Cmp.GT -> v > c.value; Cmp.LT -> v < c.value; Cmp.EQ -> abs(v - c.value) < 0.5f
            Cmp.LE -> v <= c.value; Cmp.GE -> v >= c.value; Cmp.NE -> abs(v - c.value) >= 0.5f
        }
        return if (c.enable) r else !r
    }

    fun passes(cfg: KeyCfg): Boolean = cfg.conds.all { condOk(it) }

    fun sensorMode(i: Int): SMode? {
        val d = sensorPorts.getOrNull(i) ?: return null
        val ms = modesOf(d)
        return if (ms.isEmpty()) null else ms[sensorModes[i] % ms.size]
    }

    fun sensorValue(i: Int): Float? = sensorMode(i)?.let { sensorData[i][it.key] }

    fun cycleSensor(i: Int) {
        val d = sensorPorts[i] ?: return
        val n = modesOf(d).size
        if (n < 2) return
        sensorModes = sensorModes.toMutableList().also { it[i] = (it[i] + 1) % n }
    }

    // ===================== input controller fisik =====================
    private fun pushPad() {
        val s = snap
        if (padKeys != s.pressed) padKeys = s.pressed
        val a = Axes(s.lx, s.ly, s.rx, s.ry, s.l2, s.r2)
        if (padAx != a) padAx = a
    }

    fun setPad(on: Boolean, dev: InputDevice? = null) {
        padOn = on
        if (!on) {
            snap = PadSnap(); pushPad(); padBat = null; padDevId = -1; testMode = false
        } else if (dev != null) {
            padDevId = dev.id; detected = ctypeOf(dev); applyType(); refreshBattery()
        }
    }

    private fun applyType() {
        ctype = when (ctypePref) { "ds4" -> CType.DS4; "xbox" -> CType.XBOX; "joycon" -> CType.JOYCON; else -> detected }
    }

    fun chooseCtype(p: String) { ctypePref = p; prefs.edit().putString("ctype", p).apply(); applyType() }

    fun changeTestMode(b: Boolean) { testMode = b; if (!b) { snap = PadSnap(); pushPad() } }

    /** Posisi stik dari mode uji (sentuhan pada gambar). */
    fun testStick(pad: Pad, x: Float, y: Float) {
        snap = if (pad == Pad.LS) snap.copy(lx = x, ly = y) else snap.copy(rx = x, ry = y)
        pushPad()
    }

    /** Baterai controller: tersedia lewat InputDevice.getBatteryState() di Android 12 (API 31) ke atas. */
    fun refreshBattery() {
        if (Build.VERSION.SDK_INT < 31 || padDevId < 0) { padBat = null; return }
        val bs = InputDevice.getDevice(padDevId)?.batteryState
        if (bs == null || !bs.isPresent) { padBat = null; return }
        val c = bs.capacity
        padBat = if (c.isNaN()) null else (c * 100f).roundToInt().coerceIn(0, 100)
        padChg = bs.status == BatteryState.STATUS_CHARGING
    }

    fun key(p: Pad, down: Boolean) { snap = snap.copy(keys = if (down) snap.keys + p else snap.keys - p); pushPad() }

    fun motion(e: MotionEvent) {
        fun ax(a: Int) = e.getAxisValue(a)
        val hx = ax(MotionEvent.AXIS_HAT_X); val hy = ax(MotionEvent.AXIS_HAT_Y)
        val hat = buildSet {
            if (hx < -0.5f) add(Pad.LEFT); if (hx > 0.5f) add(Pad.RIGHT)
            if (hy < -0.5f) add(Pad.UP); if (hy > 0.5f) add(Pad.DOWN)
        }
        snap = snap.copy(
            hat = hat,
            lx = ax(MotionEvent.AXIS_X), ly = -ax(MotionEvent.AXIS_Y),
            rx = ax(MotionEvent.AXIS_Z), ry = -ax(MotionEvent.AXIS_RZ),
            l2 = maxOf(ax(MotionEvent.AXIS_LTRIGGER), ax(MotionEvent.AXIS_BRAKE), ax(MotionEvent.AXIS_RX)).coerceIn(0f, 1f),
            r2 = maxOf(ax(MotionEvent.AXIS_RTRIGGER), ax(MotionEvent.AXIS_GAS), ax(MotionEvent.AXIS_RY)).coerceIn(0f, 1f)
        )
        pushPad()
    }

    // ===================== koneksi EV3 =====================
    @SuppressLint("MissingPermission")
    fun bonded(): List<BluetoothDevice> = try {
        val ad = app.getSystemService(BluetoothManager::class.java)?.adapter
        (ad?.bondedDevices ?: emptySet()).sortedBy { !it.safeName().startsWith("EV3", true) }
    } catch (e: SecurityException) { emptyList() }

    fun connect(d: BluetoothDevice) {
        if (ev3 != LinkState.OFF) return
        ev3 = LinkState.WAIT
        viewModelScope.launch(Dispatchers.IO) {
            val l = Ev3Link(d)
            try {
                l.open(); link = l; ev3Name = d.safeName()
                applyTypes(l.readTypes())
                ev3 = LinkState.ON
                startLoop(l)
            } catch (e: Exception) {
                try { l.close() } catch (_: Exception) {}
                ev3 = LinkState.OFF
                error = Msg(
                    "Gagal terhubung ke EV3. Pastikan EV3 menyala, Bluetooth EV3 aktif, dan sudah dipasangkan.",
                    "Could not connect to EV3. Make sure the EV3 is on, its Bluetooth is enabled, and it is paired."
                )
            }
        }
    }

    private fun resetPorts() {
        motorPorts = List(4) { null }; sensorPorts = List(4) { null }
        sensorData = List(4) { emptyMap() }; speeds = List(4) { 0 }; battery = null
    }

    fun disconnect() {
        loop?.cancel()
        val l = link; link = null
        ev3 = LinkState.OFF
        resetPorts()
        if (l != null) viewModelScope.launch(Dispatchers.IO) { try { l.close() } catch (_: Exception) {} }
    }

    private fun fail(msg: Msg) {
        loop?.cancel()
        try { link?.close() } catch (_: Exception) {}
        link = null; ev3 = LinkState.OFF
        resetPorts()
        error = msg
    }

    override fun onCleared() {
        val l = link; loop?.cancel()
        if (l != null) Thread { try { l.close() } catch (_: Exception) {} }.start()
    }

    private fun applyTypes(raw: List<Int>) {
        val newS = raw.subList(0, 4).map { devOf(it) }
        val modes = sensorModes.toMutableList(); val data = sensorData.toMutableList()
        for (i in 0..3) if (newS[i] != sensorPorts[i]) { modes[i] = 0; data[i] = emptyMap() }
        sensorModes = modes; sensorData = data; sensorPorts = newS
        motorPorts = raw.subList(4, 8).map { devOf(it) }
    }

    // ===================== pembacaan sensor =====================
    private val sIdx = IntArray(4)
    private val sDwell = IntArray(4)

    /** Mode yang perlu dibaca di satu port: mode tampilan + mode yang dipakai syarat sensor. */
    private fun neededModes(i: Int): List<SMode> {
        val d = sensorPorts[i] ?: return emptyList()
        val ms = modesOf(d)
        if (ms.isEmpty()) return emptyList()
        val set = LinkedHashMap<String, SMode>()
        ms[sensorModes[i] % ms.size].let { set[it.key] = it }
        fun scan(c: KeyCfg) { c.conds.forEach { cd -> if (cd.sensor == i) ms.firstOrNull { m -> m.key == cd.field }?.let { m -> set[m.key] = m } } }
        map.values.forEach { scan(it) }
        items.forEach { scan(it.cfg) }
        return set.values.toList()
    }

    private fun readSens(l: Ev3Link) {
        val req = ArrayList<Triple<Int, SMode, Boolean>>()
        for (i in 0..3) {
            val ms = neededModes(i)
            if (ms.isEmpty()) continue
            if (ms.size > 1) { if (sDwell[i] >= 3) { sIdx[i] = (sIdx[i] + 1) % ms.size; sDwell[i] = 0 } } else sIdx[i] = 0
            val m = ms[sIdx[i] % ms.size]
            req.add(Triple(i, m, sDwell[i] >= 1 || ms.size == 1))   // sampel pertama setelah ganti mode dibuang
        }
        if (req.isEmpty()) return
        val v = l.readSensors(req.map { Pair(it.first, it.second.mode) })
        val d = sensorData.map { it.toMutableMap() }
        req.forEachIndexed { k, r ->
            if (r.third && !v[k].isNaN()) d[r.first][r.second.key] = v[k]
            sDwell[r.first]++
        }
        sensorData = d
    }

    // ===================== loop kontrol =====================
    private fun startLoop(l: Ev3Link) {
        loop = viewModelScope.launch(Dispatchers.IO) {
            val sent = IntArray(4)
            val stopB = IntArray(4) { 1 }
            val toggles = mutableMapOf<Pad, Boolean>()
            var prev = emptySet<Pad>()
            var prevV = emptySet<Long>()
            var lastPorts = System.currentTimeMillis()
            var lastSens = 0L
            var lastBatt = 0L
            try {
                while (isActive) {
                    val s = snap; val cfg = map; val pressed = s.pressed
                    val virt = tab == 1 && !edit
                    val vi = vin; val list = items

                    // toggle controller
                    if (!padOn) toggles.clear()
                    for (p in pressed - prev) if (cfg[p]?.toggle == true) toggles[p] = !(toggles[p] ?: false)
                    prev = pressed
                    // toggle elemen virtual
                    val downNow = if (virt) vi.filter { it.value.down }.keys else emptySet()
                    if (virt) {
                        for (id in downNow - prevV) {
                            val vit = list.firstOrNull { x -> x.id == id }
                            if (vit != null && vit.type == VType.BTN && vit.cfg.toggle) vToggle = if (id in vToggle) vToggle - id else vToggle + id
                        }
                    } else if (vToggle.isNotEmpty()) vToggle = emptySet()
                    prevV = downNow

                    val target = IntArray(4)
                    for ((pad, k) in cfg) for (m in k.motors) {
                        stopB[m.port] = if (m.stop == StopMode.FLOAT) 0 else 1   // firmware EV3: Hold ikut brake
                        if (padOn && passes(k)) target[m.port] += (frac(pad, k, m, s, pressed, toggles) * m.power * (if (m.cw) 1 else -1)).roundToInt()
                    }
                    for (vit in list) for (m in vit.cfg.motors) {
                        stopB[m.port] = if (m.stop == StopMode.FLOAT) 0 else 1
                        if (virt && passes(vit.cfg)) target[m.port] += (vfrac(vit, m, vi) * m.power * (if (m.cw) 1 else -1)).roundToInt()
                    }

                    val ops = ByteArrayOutputStream()
                    val clamped = IntArray(4)
                    for (p in 0..3) {
                        val t = target[p].coerceIn(-100, 100); clamped[p] = t
                        if (t != sent[p]) {
                            ops.write(if (t == 0) Ev3Link.opStop(p, stopB[p]) else Ev3Link.opSpeed(p, t))
                            sent[p] = t
                        }
                    }
                    if (ops.size() > 0) { l.send(ops.toByteArray()); speeds = clamped.toList() }

                    val now = System.currentTimeMillis()
                    if (now - lastPorts > 3000) { applyTypes(l.readTypes()); lastPorts = now }
                    if (now - lastSens > 250) { readSens(l); lastSens = now }
                    if (now - lastBatt > 10000) { battery = l.readBattery(); lastBatt = now }
                    delay(40)
                }
            } catch (e: Exception) {
                if (isActive) fail(Msg("Koneksi EV3 terputus.", "EV3 connection lost."))
            }
        }
    }

    private fun vfrac(it: VItem, m: Motor, vi: Map<Long, VIn>): Float {
        val i = vi[it.id] ?: VIn()
        return when (it.type) {
            VType.BTN -> if (if (it.cfg.toggle) it.id in vToggle else i.down) 1f else 0f
            VType.SLIDER -> shape(i.x, 0.12f, m.speed)
            VType.ANALOG -> shape(if (m.axis == Axis.H) i.x else i.y, 0.12f, m.speed)
        }
    }

    /** Hasil -1..1 untuk satu motor dari satu tombol/stik/trigger controller. */
    private fun frac(pad: Pad, k: KeyCfg, m: Motor, s: PadSnap, pressed: Set<Pad>, tg: Map<Pad, Boolean>): Float =
        when (pad.kind) {
            Kind.TRIG -> {
                val axis = if (pad == Pad.L2) s.l2 else s.r2
                shape(max(axis, if (pad in s.keys) 1f else 0f), 0.1f, m.speed)
            }
            Kind.STICK -> {
                val v = when {
                    pad == Pad.LS && m.axis == Axis.H -> s.lx
                    pad == Pad.LS -> s.ly
                    m.axis == Axis.H -> s.rx
                    else -> s.ry
                }
                shape(v, 0.15f, m.speed)
            }
            else -> if (if (k.toggle) tg[pad] == true else pad in pressed) 1f else 0f
        }

    /** deadzone + mode kecepatan (tetap / ikuti gerakan). */
    private fun shape(v: Float, dz: Float, sp: SpeedMode): Float {
        val a = abs(v)
        if (a < dz) return 0f
        val o = if (sp == SpeedMode.PROP) ((a - dz) / (1f - dz)).coerceAtMost(1f) else 1f
        return o * sign(v)
    }
}

/** Deteksi jenis controller dari ID produsen dan nama perangkat. */
fun ctypeOf(d: InputDevice): CType {
    val n = (d.name ?: "").lowercase()
    return when {
        d.vendorId == 0x045E || "xbox" in n || "microsoft" in n -> CType.XBOX
        d.vendorId == 0x057E || "joy-con" in n || "joycon" in n || "nintendo" in n || "pro controller" in n -> CType.JOYCON
        else -> CType.DS4   // Sony (0x054C), "Wireless Controller", dan controller lain
    }
}
