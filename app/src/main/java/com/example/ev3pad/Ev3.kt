package com.example.ev3pad

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@SuppressLint("MissingPermission")
fun BluetoothDevice.safeName(): String = try { name ?: address } catch (e: SecurityException) { address }

/**
 * Komunikasi EV3 lewat Bluetooth klasik (RFCOMM/SPP) memakai "direct command" firmware LEGO asli.
 * EV3 harus sudah dipasangkan (pair) lewat Pengaturan Bluetooth Android (PIN default 1234).
 */
class Ev3Link(private val dev: BluetoothDevice) {
    private var sock: BluetoothSocket? = null
    private lateinit var inp: DataInputStream
    private lateinit var out: OutputStream
    private var counter = 0

    @SuppressLint("MissingPermission")
    fun open() {
        val s = dev.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        s.connect()
        sock = s; inp = DataInputStream(s.inputStream); out = s.outputStream
    }

    fun close() {
        try { send(byteArrayOf(0xA3.toByte(), 0, 0x0F, 0)) } catch (e: Exception) {} // float semua port
        try { sock?.close() } catch (e: Exception) {}
        sock = null
    }

    private fun packet(ops: ByteArray, globals: Int, reply: Boolean): ByteArray {
        counter = (counter + 1) and 0xFFFF
        val b = ByteArrayOutputStream()
        b.write(counter and 0xFF); b.write(counter shr 8)
        b.write(if (reply) 0x00 else 0x80)
        b.write(globals and 0xFF); b.write((globals shr 8) and 0x03)
        b.write(ops)
        val body = b.toByteArray()
        return byteArrayOf((body.size and 0xFF).toByte(), (body.size shr 8).toByte()) + body
    }

    @Synchronized
    fun send(ops: ByteArray) { out.write(packet(ops, 0, false)); out.flush() }

    @Synchronized
    private fun query(ops: ByteArray, globals: Int): ByteArray {
        out.write(packet(ops, globals, true)); out.flush()
        val h = ByteArray(2); inp.readFully(h)
        val len = (h[0].toInt() and 0xFF) or ((h[1].toInt() and 0xFF) shl 8)
        val d = ByteArray(len); inp.readFully(d)
        return d // [counter(2), type(1), globals...]
    }

    /** Kode tipe mentah 8 port: sensor 1..4 lalu motor A..D. */
    fun readTypes(): List<Int> {
        val nums = intArrayOf(0, 1, 2, 3, 16, 17, 18, 19)
        val ops = ByteArrayOutputStream()
        nums.forEachIndexed { k, n ->
            // opInput_Device(GET_TYPEMODE, layer 0, port, GV(type), GV(mode))
            ops.write(byteArrayOf(0x99.toByte(), 0x05, 0x00, n.toByte(), (0x60 or (2 * k)).toByte(), (0x60 or (2 * k + 1)).toByte()))
        }
        val d = query(ops.toByteArray(), 16)
        return nums.indices.map { k -> if (d.size > 3 + 2 * k) d[3 + 2 * k].toInt() and 0xFF else 126 }
    }

    /** Baca nilai sensor (satuan SI). req = daftar (port 0..3, mode). Hasil NaN jika gagal. */
    fun readSensors(req: List<Pair<Int, Int>>): List<Float> {
        val ops = ByteArrayOutputStream()
        req.forEachIndexed { k, (port, mode) ->
            // opInput_ReadSI(layer 0, port, type 0 = tetap, mode, GV float)
            ops.write(byteArrayOf(0x9D.toByte(), 0x00, port.toByte(), 0x00, mode.toByte(), (0x60 or (4 * k)).toByte()))
        }
        val d = query(ops.toByteArray(), 4 * req.size)
        return req.indices.map { k ->
            if (d.size >= 3 + 4 * (k + 1) && d[2].toInt() == 2)
                ByteBuffer.wrap(d, 3 + 4 * k, 4).order(ByteOrder.LITTLE_ENDIAN).float
            else Float.NaN
        }
    }

    /** Level baterai EV3 dalam persen (opUI_Read GET_LBATT), atau null jika gagal. */
    fun readBattery(): Int? {
        val d = query(byteArrayOf(0x81.toByte(), 0x12, 0x60), 1)
        return if (d.size > 3 && d[2].toInt() == 2) (d[3].toInt() and 0xFF).coerceIn(0, 100) else null
    }

    companion object {
        private fun lc(v: Int): ByteArray =
            if (v in -32..31) byteArrayOf((v and 0x3F).toByte()) else byteArrayOf(0x81.toByte(), v.toByte())

        /** Atur kecepatan terregulasi (-100..100) lalu start. */
        fun opSpeed(port: Int, speed: Int): ByteArray {
            val m = (1 shl port).toByte()
            return byteArrayOf(0xA5.toByte(), 0, m) + lc(speed) + byteArrayOf(0xA6.toByte(), 0, m)
        }

        /** brake: 1 = rem (tahan), 0 = float (lepas bebas). */
        fun opStop(port: Int, brake: Int): ByteArray =
            byteArrayOf(0xA3.toByte(), 0, (1 shl port).toByte(), brake.toByte())
    }
}
