package com.example.ev3pad

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/** Bahasa aktif: "id" atau "en". Dibaca dari state Compose, jadi UI otomatis ter-update saat diganti. */
object Lang {
    var code by mutableStateOf(if (java.util.Locale.getDefault().language in listOf("in", "id")) "id" else "en")
}

fun tr(id: String, en: String): String = if (Lang.code == "en") en else id

class Msg(val id: String, val en: String)

/** Perangkat yang terdeteksi di port EV3. */
enum class Dev(val code: Int, private val id: String, private val en: String) {
    LARGE(7, "Motor besar", "Large motor"),
    MEDIUM(8, "Motor sedang", "Medium motor"),
    TOUCH(16, "Sensor sentuh", "Touch sensor"),
    COLOR(29, "Sensor warna", "Color sensor"),
    US(30, "Sensor ultrasonik", "Ultrasonic sensor"),
    GYRO(32, "Sensor gyro", "Gyro sensor"),
    IR(33, "Sensor IR", "IR sensor"),
    OTHER(-1, "Perangkat", "Device");

    fun label(): String = tr(id, en)
}

fun devOf(code: Int): Dev? = when (code) {
    0, 125, 126, 127, 255 -> null
    else -> Dev.values().firstOrNull { it.code == code } ?: Dev.OTHER
}

/** type: 0 angka, 1 nama warna, 2 sentuh. mode = nomor mode sensor di EV3. */
class SMode(private val id: String, private val en: String, val mode: Int, val unit: String, val type: Int, val dec: Int) {
    fun label(): String = tr(id, en)
}

fun modesOf(d: Dev): List<SMode> = when (d) {
    Dev.US -> listOf(SMode("Jarak", "Distance", 0, "cm", 0, 1), SMode("Jarak (inci)", "Distance (inch)", 1, "in", 0, 1))
    Dev.COLOR -> listOf(
        SMode("Warna", "Color", 2, "", 1, 0),
        SMode("Cahaya pantul", "Reflected light", 0, "%", 0, 0),
        SMode("Cahaya sekitar", "Ambient light", 1, "%", 0, 0)
    )
    Dev.TOUCH -> listOf(SMode("Sentuhan", "Touch", 0, "", 2, 0))
    Dev.GYRO -> listOf(SMode("Sudut", "Angle", 0, "°", 0, 0), SMode("Kecepatan sudut", "Rate", 1, "°/s", 0, 0))
    Dev.IR -> listOf(SMode("Kedekatan", "Proximity", 0, "%", 0, 0))
    else -> emptyList()
}

private val COLORS_ID = listOf("–", "Hitam", "Biru", "Hijau", "Kuning", "Merah", "Putih", "Coklat")
private val COLORS_EN = listOf("–", "Black", "Blue", "Green", "Yellow", "Red", "White", "Brown")

fun fmtSensor(m: SMode, v: Float): String {
    if (v.isNaN()) return "–"
    return when (m.type) {
        1 -> { val i = v.roundToInt().coerceIn(0, 7); if (Lang.code == "en") COLORS_EN[i] else COLORS_ID[i] }
        2 -> if (v > 0.5f) tr("Ditekan", "Pressed") else tr("Lepas", "Released")
        else -> String.format(java.util.Locale.US, "%." + m.dec + "f", v) + (if (m.unit.isEmpty()) "" else " " + m.unit)
    }
}
