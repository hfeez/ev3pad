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
    var ev3 by mutableStateOf(LinkState.OFF); private set
    var ev3Name by mutableStateOf(""); private set
    var motorPorts by mutableStateOf<List<String?>>(List(4) { null }); private set
    var sensorPorts by mutableStateOf<List<String?>>(List(4) { null }); private set
    var padOn by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)

    @Volatile private var snap = PadSnap()
    private var link: Ev3Link? = null
    private var loop: Job? = null

    // ---------- pengaturan tombol ----------
    fun saveKey(p: Pad, cfg: KeyCfg?) {
        map = map.toMutableMap().also { if (cfg == null) it.remove(p) else it[p] = cfg }
        Store.save(prefs, map)
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
                val (s, m) = l.readPorts(); sensorPorts = s; motorPorts = m
                ev3 = LinkState.ON
                startLoop(l)
            } catch (e: Exception) {
                try { l.close() } catch (_: Exception) {}
                ev3 = LinkState.OFF
                error = "Gagal terhubung ke EV3. Pastikan EV3 menyala, Bluetooth EV3 aktif, dan sudah dipasangkan."
            }
        }
    }

    fun disconnect() {
        loop?.cancel()
        val l = link; link = null
        ev3 = LinkState.OFF
        motorPorts = List(4) { null }; sensorPorts = List(4) { null }
        if (l != null) viewModelScope.launch(Dispatchers.IO) { try { l.close() } catch (_: Exception) {} }
    }

    private fun fail(msg: String) {
        loop?.cancel()
        try { link?.close() } catch (_: Exception) {}
        link = null; ev3 = LinkState.OFF
        motorPorts = List(4) { null }; sensorPorts = List(4) { null }
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
                    if (now - lastPorts > 3000) {
                        val (se, mo) = l.readPorts(); sensorPorts = se; motorPorts = mo; lastPorts = now
                    }
                    delay(40)
                }
            } catch (e: Exception) {
                if (isActive) fail("Koneksi EV3 terputus.")
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
