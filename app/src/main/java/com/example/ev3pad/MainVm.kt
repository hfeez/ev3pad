package com.example.ev3pad

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
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
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sign

enum class LinkState { OFF, WAIT, ON }

class MainVm(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("ev3pad", Context.MODE_PRIVATE)

    var map by mutableStateOf(Store.load(prefs)); private set
    var profiles by mutableStateOf(Store.loadProfiles(prefs)); private set
    var active by mutableStateOf<String?>(prefs.getString("active", null)); private set
    var ev3 by mutableStateOf(LinkState.OFF); private set
    var ev3Name by mutableStateOf(""); private set
    var motorPorts by mutableStateOf<List<Dev?>>(List(4) { null }); private set
    var sensorPorts by mutableStateOf<List<Dev?>>(List(4) { null }); private set
    var sensorVals by mutableStateOf<List<Float>>(List(4) { Float.NaN }); private set
    var sensorModes by mutableStateOf<List<Int>>(List(4) { 0 }); private set
    var padOn by mutableStateOf(false); private set
    var error by mutableStateOf<Msg?>(null)

    @Volatile private var snap = PadSnap()
    private var link: Ev3Link? = null
    private var loop: Job? = null

    init {
        prefs.getString("lang", null)?.let { Lang.code = it }
        if (active !in profiles) active = null
    }

    fun setLang(c: String) { Lang.code = c; prefs.edit().putString("lang", c).apply() }

    // ---------- pengaturan tombol ----------
    fun saveKey(p: Pad, cfg: KeyCfg?) {
        map = map.toMutableMap().also { if (cfg == null) it.remove(p) else it[p] = cfg }
        Store.save(prefs, map)
    }

    // ---------- profil controller ----------
    val dirty: Boolean get() = active?.let { profiles[it] != map } ?: false

    private fun persistProfiles() {
        Store.saveProfiles(prefs, profiles)
        prefs.edit().putString("active", active).apply()
    }

    fun saveProfile(name: String) {
        val n = name.trim(); if (n.isEmpty()) return
        profiles = LinkedHashMap(profiles).also { it[n] = map }
        active = n; persistProfiles()
    }

    fun loadProfile(name: String) {
        val m = profiles[name] ?: return
        map = m; active = name
        Store.save(prefs, map); persistProfiles()
    }

    fun renameProfile(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty() || (n != old && n in profiles)) return false
        val r = LinkedHashMap<String, Map<Pad, KeyCfg>>()
        for ((k, v) in profiles) r[if (k == old) n else k] = v
        profiles = r
        if (active == old) active = n
        persistProfiles(); return true
    }

    fun deleteProfile(name: String) {
        profiles = LinkedHashMap(profiles).also { it.remove(name) }
        if (active == name) active = null
        persistProfiles()
    }

    // ---------- input controller ----------
    fun setPad(on: Boolean) { padOn = on; if (!on) snap = PadSnap() }

    fun key(p: Pad, down: Boolean) { snap = snap.copy(keys = if (down) snap.keys + p else snap.keys - p) }

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
    }

    // ---------- sensor ----------
    fun cycleSensor(i: Int) {
        val d = sensorPorts[i] ?: return
        val n = modesOf(d).size
        if (n < 2) return
        sensorModes = sensorModes.toMutableList().also { it[i] = (it[i] + 1) % n }
        sensorVals = sensorVals.toMutableList().also { it[i] = Float.NaN }
    }

    private fun applyTypes(raw: List<Int>) {
        val newS = raw.subList(0, 4).map { devOf(it) }
        val modes = sensorModes.toMutableList()
        for (i in 0..3) if (newS[i] != sensorPorts[i]) modes[i] = 0
        sensorModes = modes
        sensorPorts = newS
        motorPorts = raw.subList(4, 8).map { devOf(it) }
    }

    private fun readSens(l: Ev3Link) {
        val devs = sensorPorts; val modes = sensorModes
        val req = (0..3).mapNotNull { i ->
            val d = devs[i] ?: return@mapNotNull null
            val ms = modesOf(d)
            if (ms.isEmpty()) null else Pair(i, ms[modes[i] % ms.size].mode)
        }
        if (req.isEmpty()) return
        val v = l.readSensors(req)
        val out = MutableList(4) { Float.NaN }
        req.forEachIndexed { k, r -> out[r.first] = v[k] }
        sensorVals = out
    }

    // ---------- koneksi EV3 ----------
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
        sensorVals = List(4) { Float.NaN }
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

    // ---------- loop kontrol: baca controller -> kirim perintah motor ----------
    private fun startLoop(l: Ev3Link) {
        loop = viewModelScope.launch(Dispatchers.IO) {
            val sent = IntArray(4)
            val stopB = IntArray(4) { 1 }
            val toggles = mutableMapOf<Pad, Boolean>()
            var prev = emptySet<Pad>()
            var lastPorts = System.currentTimeMillis()
            var lastSens = 0L
            try {
                while (isActive) {
                    val s = snap; val cfg = map; val pressed = s.pressed
                    if (!padOn) toggles.clear()
                    for (p in pressed - prev) if (cfg[p]?.toggle == true) toggles[p] = !(toggles[p] ?: false)
                    prev = pressed

                    val target = IntArray(4)
                    if (padOn) for ((pad, k) in cfg) for (m in k.motors) {
                        val f = frac(pad, k, m, s, pressed, toggles)
                        target[m.port] += (f * m.power * (if (m.cw) 1 else -1)).roundToInt()
                        // Catatan: firmware EV3 asli hanya punya brake (1) dan float (0); Hold ikut brake.
                        stopB[m.port] = if (m.stop == StopMode.FLOAT) 0 else 1
                    }

                    val ops = ByteArrayOutputStream()
                    for (p in 0..3) {
                        val t = target[p].coerceIn(-100, 100)
                        if (t != sent[p]) {
                            ops.write(if (t == 0) Ev3Link.opStop(p, stopB[p]) else Ev3Link.opSpeed(p, t))
                            sent[p] = t
                        }
                    }
                    if (ops.size() > 0) l.send(ops.toByteArray())

                    val now = System.currentTimeMillis()
                    if (now - lastPorts > 3000) { applyTypes(l.readTypes()); lastPorts = now }
                    if (now - lastSens > 250) { readSens(l); lastSens = now }
                    delay(40)
                }
            } catch (e: Exception) {
                if (isActive) fail(Msg("Koneksi EV3 terputus.", "EV3 connection lost."))
            }
        }
    }

    /** Hasil -1..1 untuk satu motor dari satu tombol/stik/trigger. */
    private fun frac(pad: Pad, k: KeyCfg, m: Motor, s: PadSnap, pressed: Set<Pad>, tg: Map<Pad, Boolean>): Float =
        when (pad.kind) {
            Kind.BTN -> if (if (k.toggle) tg[pad] == true else pad in pressed) 1f else 0f
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
        }

    /** deadzone + mode kecepatan (tetap / ikuti gerakan). */
    private fun shape(v: Float, dz: Float, sp: SpeedMode): Float {
        val a = abs(v)
        if (a < dz) return 0f
        val o = if (sp == SpeedMode.PROP) ((a - dz) / (1f - dz)).coerceAtMost(1f) else 1f
        return o * sign(v)
    }
}
