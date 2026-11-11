package com.example.ev3pad

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Ukuran panggung virtual (satuan logis). Semua posisi elemen virtual memakai satuan ini, lalu diskalakan seragam ke layar. */
/** Tinggi panggung virtual (satuan logis). Lebar menyesuaikan rasio layar (minimal MIN_STAGE_W), jadi panggung selalu memenuhi area. */
const val STAGE_H = 360f
const val MIN_STAGE_W = 400f
/** Lebar panggung versi lama, hanya dipakai untuk migrasi layout lama. */
const val STAGE_W = 640f

enum class Kind { BTN, TRIG, STICK, SLIDER, ANALOG }

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

enum class Cmp(val sym: String) { GT(">"), LT("<"), EQ("="), LE("≤"), GE("≥"), NE("≠") }

/** Syarat sensor: enable=true -> elemen aktif hanya jika syarat benar; false -> elemen nonaktif jika syarat benar. */
data class Cond(
    val enable: Boolean = true,
    val sensor: Int = 0,
    val field: String = "dist",
    val op: Cmp = Cmp.GT,
    val value: Float = 20f
)

data class KeyCfg(
    val toggle: Boolean = false,
    val motors: List<Motor> = listOf(Motor()),
    val conds: List<Cond> = emptyList(),
    val spring: Boolean = true
)

enum class VType { BTN, SLIDER, ANALOG }

data class VItem(val id: Long, val type: VType, val name: String, val x: Float, val y: Float, val w: Float, val h: Float, val cfg: KeyCfg,
                 /** x = jarak dari tepi (kiri, atau kanan jika ar=true) agar layout menyesuaikan lebar layar. */
                 val ar: Boolean = false) {
    val kind: Kind get() = when (type) { VType.BTN -> Kind.BTN; VType.SLIDER -> Kind.SLIDER; VType.ANALOG -> Kind.ANALOG }
}

/** Masukan satu elemen virtual: down = disentuh, x/y = -1..1. */
data class VIn(val down: Boolean = false, val x: Float = 0f, val y: Float = 0f)

/** Posisi stik dan trigger (untuk menggambar controller). */
data class Axes(val lx: Float = 0f, val ly: Float = 0f, val rx: Float = 0f, val ry: Float = 0f, val l2: Float = 0f, val r2: Float = 0f)

/** Nama tombol sesuai jenis controller (posisi sama, nama berbeda). */
fun Pad.labelFor(t: CType): String = when (t) {
    CType.XBOX -> when (this) {
        Pad.L2 -> "LT"; Pad.R2 -> "RT"; Pad.L1 -> "LB"; Pad.R1 -> "RB"
        Pad.TRI -> "Y"; Pad.CRS -> "A"; Pad.SQR -> "X"; Pad.CIR -> "B"
        Pad.SHARE -> "View"; Pad.OPT -> "Menu"; Pad.PS -> tr("Tombol Xbox", "Xbox button"); Pad.TP -> "Share"
        else -> label
    }
    CType.JOYCON -> when (this) {
        Pad.L2 -> "ZL"; Pad.R2 -> "ZR"; Pad.L1 -> "L"; Pad.R1 -> "R"
        Pad.TRI -> "X"; Pad.CRS -> "B"; Pad.SQR -> "Y"; Pad.CIR -> "A"
        Pad.SHARE -> "Minus (−)"; Pad.OPT -> "Plus (+)"; Pad.PS -> "Home"; Pad.TP -> "Capture"
        else -> label
    }
    else -> label
}

/** Snapshot input controller fisik; dibaca oleh loop pengirim ke EV3. */
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
    private fun motorJson(x: Motor) = JSONObject().put("p", x.port).put("cw", x.cw).put("pw", x.power)
        .put("st", x.stop.name).put("ax", x.axis.name).put("sp", x.speed.name)

    private fun motorFrom(x: JSONObject) = Motor(
        x.getInt("p"), x.getBoolean("cw"), x.getInt("pw"),
        StopMode.valueOf(x.getString("st")), Axis.valueOf(x.getString("ax")), SpeedMode.valueOf(x.getString("sp"))
    )

    private fun cfgJson(k: KeyCfg): JSONObject {
        val m = JSONArray(); k.motors.forEach { m.put(motorJson(it)) }
        val c = JSONArray()
        k.conds.forEach {
            c.put(JSONObject().put("e", it.enable).put("s", it.sensor).put("f", it.field).put("o", it.op.name).put("v", it.value.toDouble()))
        }
        return JSONObject().put("t", k.toggle).put("sp", k.spring).put("m", m).put("c", c)
    }

    private fun cfgFrom(j: JSONObject): KeyCfg {
        val a = j.getJSONArray("m"); val c = j.optJSONArray("c")
        return KeyCfg(
            j.getBoolean("t"),
            (0 until a.length()).map { motorFrom(a.getJSONObject(it)) },
            if (c == null) emptyList() else (0 until c.length()).map { i ->
                val o = c.getJSONObject(i)
                Cond(o.getBoolean("e"), o.getInt("s"), o.getString("f"), Cmp.valueOf(o.getString("o")), o.getDouble("v").toFloat())
            },
            j.optBoolean("sp", true)
        )
    }

    fun mapToJson(m: Map<Pad, KeyCfg>): JSONObject {
        val o = JSONObject()
        m.forEach { (pad, k) -> o.put(pad.name, cfgJson(k)) }
        return o
    }

    fun mapFromJson(o: JSONObject): Map<Pad, KeyCfg> {
        val r = mutableMapOf<Pad, KeyCfg>()
        for (key in o.keys()) {
            try { r[Pad.valueOf(key)] = cfgFrom(o.getJSONObject(key)) } catch (e: Exception) { }
        }
        return r
    }

    fun itemsToJson(l: List<VItem>): JSONArray {
        val a = JSONArray()
        l.forEach {
            a.put(JSONObject().put("i", it.id).put("t", it.type.name).put("n", it.name)
                .put("x", it.x.toDouble()).put("y", it.y.toDouble()).put("w", it.w.toDouble()).put("h", it.h.toDouble())
                .put("c", cfgJson(it.cfg)).put("a", it.ar))
        }
        return a
    }

    fun itemsFromJson(a: JSONArray): List<VItem> {
        val r = ArrayList<VItem>()
        for (i in 0 until a.length()) {
            try {
                val o = a.getJSONObject(i)
                val t = VType.valueOf(o.getString("t"))
                var w = o.getDouble("w").toFloat(); var h = o.getDouble("h").toFloat()
                if (t == VType.ANALOG) { val s = minOf(w, h); w = s; h = s }   // analog selalu lingkaran
                var x = o.getDouble("x").toFloat(); var ar = o.optBoolean("a", false)
                if (!o.has("a") && x + w / 2f > STAGE_W / 2f) { ar = true; x = STAGE_W - x - w }   // layout lama -> jangkar sisi terdekat
                r.add(VItem(o.getLong("i"), t, o.getString("n"), x, o.getDouble("y").toFloat(), w, h, cfgFrom(o.getJSONObject("c")), ar))
            } catch (e: Exception) { }
        }
        return r
    }

    fun saveMap(p: SharedPreferences, m: Map<Pad, KeyCfg>) { p.edit().putString("map", mapToJson(m).toString()).apply() }
    fun loadMap(p: SharedPreferences): Map<Pad, KeyCfg> = try { mapFromJson(JSONObject(p.getString("map", "{}") ?: "{}")) } catch (e: Exception) { emptyMap() }

    fun saveItems(p: SharedPreferences, l: List<VItem>) { p.edit().putString("items", itemsToJson(l).toString()).apply() }
    fun loadItems(p: SharedPreferences): List<VItem> = try { itemsFromJson(JSONArray(p.getString("items", "[]") ?: "[]")) } catch (e: Exception) { emptyList() }

    /** profiles[jenis][nama] = JSON string. jenis: "ctl" (controller) atau "virt" (virtual). */
    fun saveProfiles(p: SharedPreferences, pr: Map<String, Map<String, String>>, act: Map<String, String?>) {
        val o = JSONObject()
        pr.forEach { (k, m) ->
            val a = JSONArray()
            m.forEach { (n, j) -> a.put(JSONObject().put("n", n).put("j", j)) }
            o.put(k, a)
        }
        val ao = JSONObject()
        act.forEach { (k, v) -> if (v != null) ao.put(k, v) }
        p.edit().putString("profiles2", o.toString()).putString("active2", ao.toString()).apply()
    }

    fun loadProfiles(p: SharedPreferences): Map<String, Map<String, String>> {
        val r = LinkedHashMap<String, Map<String, String>>()
        r["ctl"] = LinkedHashMap(); r["virt"] = LinkedHashMap()
        try {
            val s = p.getString("profiles2", null)
            if (s != null) {
                val o = JSONObject(s)
                for (k in o.keys()) {
                    val a = o.getJSONArray(k); val m = LinkedHashMap<String, String>()
                    for (i in 0 until a.length()) { val e = a.getJSONObject(i); m[e.getString("n")] = e.getString("j") }
                    if (k == "virt") for (key in m.keys.toList()) {
                        m[key] = try { itemsToJson(itemsFromJson(JSONArray(m[key]))).toString() } catch (e: Exception) { m[key]!! }
                    }
                    r[k] = m
                }
            } else {
                // migrasi profil controller dari versi lama
                val old = p.getString("profiles", null)
                if (old != null) {
                    val a = JSONArray(old); val m = LinkedHashMap<String, String>()
                    for (i in 0 until a.length()) {
                        val e = a.getJSONObject(i)
                        m[e.getString("n")] = mapToJson(mapFromJson(e.getJSONObject("m"))).toString()
                    }
                    r["ctl"] = m
                }
            }
        } catch (e: Exception) { }
        return r
    }

    fun loadActive(p: SharedPreferences): Map<String, String?> = try {
        val s = p.getString("active2", null)
        if (s != null) {
            val o = JSONObject(s)
            mapOf("ctl" to (if (o.has("ctl")) o.getString("ctl") else null), "virt" to (if (o.has("virt")) o.getString("virt") else null))
        } else mapOf("ctl" to p.getString("active", null), "virt" to null)
    } catch (e: Exception) { mapOf("ctl" to null, "virt" to null) }
}
