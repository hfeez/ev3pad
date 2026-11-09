package com.example.ev3pad

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Kind { BTN, TRIG, STICK }

enum class Pad(private val idl: String, private val en: String, val kind: Kind = Kind.BTN) {
    L2("L2 (trigger kiri)", "L2 (left trigger)", Kind.TRIG), R2("R2 (trigger kanan)", "R2 (right trigger)", Kind.TRIG),
    L1("L1", "L1"), R1("R1", "R1"),
    UP("D-pad atas", "D-pad up"), DOWN("D-pad bawah", "D-pad down"), LEFT("D-pad kiri", "D-pad left"), RIGHT("D-pad kanan", "D-pad right"),
    TRI("Segitiga", "Triangle"), CRS("Silang", "Cross"), SQR("Kotak", "Square"), CIR("Lingkaran", "Circle"),
    LS("Stik analog kiri", "Left analog stick", Kind.STICK), RS("Stik analog kanan", "Right analog stick", Kind.STICK),
    L3("L3 (tekan stik kiri)", "L3 (press left stick)"), R3("R3 (tekan stik kanan)", "R3 (press right stick)"),
    SHARE("Share", "Share"), OPT("Options", "Options"), PS("Tombol PS", "PS button"), TP("Touchpad (tekan)", "Touchpad (press)");

    val label: String get() = tr(idl, en)
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
    private fun toJson(m: Map<Pad, KeyCfg>): JSONObject {
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
        return o
    }

    private fun fromJson(o: JSONObject): Map<Pad, KeyCfg> {
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
        return r
    }

    fun save(p: SharedPreferences, m: Map<Pad, KeyCfg>) {
        p.edit().putString("map", toJson(m).toString()).apply()
    }

    fun load(p: SharedPreferences): Map<Pad, KeyCfg> = try {
        fromJson(JSONObject(p.getString("map", "{}") ?: "{}"))
    } catch (e: Exception) { emptyMap() }

    fun saveProfiles(p: SharedPreferences, pr: Map<String, Map<Pad, KeyCfg>>) {
        val a = JSONArray()
        pr.forEach { (n, m) -> a.put(JSONObject().put("n", n).put("m", toJson(m))) }
        p.edit().putString("profiles", a.toString()).apply()
    }

    fun loadProfiles(p: SharedPreferences): Map<String, Map<Pad, KeyCfg>> = try {
        val a = JSONArray(p.getString("profiles", "[]") ?: "[]")
        val r = LinkedHashMap<String, Map<Pad, KeyCfg>>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            r[o.getString("n")] = fromJson(o.getJSONObject("m"))
        }
        r
    } catch (e: Exception) { LinkedHashMap() }
}
