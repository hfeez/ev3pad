package com.example.ev3pad

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { BTN, TRIG, STICK }

enum class Pad(val label: String, val kind: Kind = Kind.BTN) {
    L2("L2 (trigger kiri)", Kind.TRIG), R2("R2 (trigger kanan)", Kind.TRIG),
    L1("L1"), R1("R1"),
    UP("D-pad atas"), DOWN("D-pad bawah"), LEFT("D-pad kiri"), RIGHT("D-pad kanan"),
    TRI("Segitiga"), CRS("Silang"), SQR("Kotak"), CIR("Lingkaran"),
    LS("Stik analog kiri", Kind.STICK), RS("Stik analog kanan", Kind.STICK),
    L3("L3 (tekan stik kiri)"), R3("R3 (tekan stik kanan)"),
    SHARE("Share"), OPT("Options"), PS("Tombol PS"), TP("Touchpad (tekan)")
}

enum class StopMode { HOLD, BRAKE, FLOAT }
enum class Axis { H, V }
enum class SpeedMode { FIXED, PROP }

/** port: 0..3 = A..D */
data class Motor(
    val port: Int = 0,
    val cw: Boolean = true,
    val power: Int = 70,
    val stop: StopMode = StopMode.BRAKE,
    val axis: Axis = Axis.V,
    val speed: SpeedMode = SpeedMode.FIXED
)

data class KeyCfg(val toggle: Boolean = false, val motors: List<Motor> = listOf(Motor()))

/** Snapshot input controller; dibaca oleh loop pengirim ke EV3. */
data class PadSnap(
    val keys: Set<Pad> = emptySet(),
    val hat: Set<Pad> = emptySet(),
    val lx: Float = 0f, val ly: Float = 0f,
    val rx: Float = 0f, val ry: Float = 0f,
    val l2: Float = 0f, val r2: Float = 0f
) {
    val pressed: Set<Pad> get() = keys + hat
}

object Store {
    fun save(p: SharedPreferences, m: Map<Pad, KeyCfg>) {
        val o = JSONObject()
        m.forEach { (pad, k) ->
            val arr = JSONArray()
            k.motors.forEach { x ->
                arr.put(
                    JSONObject().put("p", x.port).put("cw", x.cw).put("pw", x.power)
                        .put("st", x.stop.name).put("ax", x.axis.name).put("sp", x.speed.name)
                )
            }
            o.put(pad.name, JSONObject().put("t", k.toggle).put("m", arr))
        }
        p.edit().putString("map", o.toString()).apply()
    }

    fun load(p: SharedPreferences): Map<Pad, KeyCfg> = try {
        val o = JSONObject(p.getString("map", "{}") ?: "{}")
        val r = mutableMapOf<Pad, KeyCfg>()
        for (key in o.keys()) {
            val j = o.getJSONObject(key)
            val a = j.getJSONArray("m")
            r[Pad.valueOf(key)] = KeyCfg(j.getBoolean("t"), (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                Motor(
                    x.getInt("p"), x.getBoolean("cw"), x.getInt("pw"),
                    StopMode.valueOf(x.getString("st")), Axis.valueOf(x.getString("ax")),
                    SpeedMode.valueOf(x.getString("sp"))
                )
            })
        }
        r
    } catch (e: Exception) {
        emptyMap()
    }
}
