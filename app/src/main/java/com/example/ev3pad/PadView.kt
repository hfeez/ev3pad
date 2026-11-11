package com.example.ev3pad

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import kotlin.math.hypot

private fun DrawScope.fillS(s: Shp, c: Color) {
    when (s.kind) {
        0 -> drawPath(s.path, c)
        1 -> drawCircle(c, s.r, Offset(s.x, s.y))
        2 -> drawRoundRect(c, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(s.r))
    }
}

private fun DrawScope.strokeS(s: Shp, c: Color, w: Float) {
    val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (s.kind) {
        0 -> drawPath(s.path, c, style = st)
        1 -> drawCircle(c, s.r, Offset(s.x, s.y), style = st)
        2 -> drawRoundRect(c, Offset(s.x, s.y), Size(s.w, s.h), CornerRadius(s.r), style = st)
    }
}

private fun DrawScope.drawText(paint: android.graphics.Paint, t: String, x: Float, y: Float, size: Float, color: Color) {
    drawIntoCanvas { c ->
        paint.color = color.toArgb(); paint.textSize = size
        c.nativeCanvas.drawText(t, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}

/** Menggambar controller: tombol yang ditekan menyala, tutup stik bergeser mengikuti sumbu stik. */
fun DrawScope.drawSkin(skin: Skin, pal: Pal, pressed: Set<Pad>, mapped: (Pad) -> Boolean, ax: Axes, paint: android.graphics.Paint) {
    val sc = size.width / skin.vw
    withTransform({ scale(sc, sc, Offset.Zero); translate(-skin.vx, -skin.vy) }) {
        for (it in skin.items) {
            val cp = it.cap
            if (cp != null) {
                val ox = if (cp == Pad.LS) ax.lx else ax.rx
                val oy = if (cp == Pad.LS) ax.ly else ax.ry
                translate(ox * skin.travel, -oy * skin.travel) { drawItem(it, skin, pal, pressed, mapped, ax, paint) }
            } else drawItem(it, skin, pal, pressed, mapped, ax, paint)
        }
    }
}

private fun DrawScope.drawItem(it: SItem, skin: Skin, pal: Pal, pressed: Set<Pad>, mapped: (Pad) -> Boolean, ax: Axes, paint: android.graphics.Paint) {
    val s = it.shp
    val pad = it.pad
    val on = pad != null && (pad in pressed ||
        (pad == Pad.LS && hypot(ax.lx, ax.ly) > 0.15f) || (pad == Pad.RS && hypot(ax.rx, ax.ry) > 0.15f))
    val map = pad != null && mapped(pad)
    when (it.st) {
        St.BODY -> { fillS(s, pal.bdf); strokeS(s, pal.olc, 12f) }
        St.REC -> fillS(s, pal.rec)
        St.REC2 -> fillS(s, lerp(pal.rec, pal.bdf, 0.55f))
        St.LINE -> strokeS(s, pal.rec.copy(alpha = 0.7f), 8f)
        St.DEC -> fillS(s, pal.keyf.copy(alpha = 0.85f))
        St.DEC2 -> fillS(s, pal.keyf.copy(alpha = 0.55f))
        St.DOTS -> {
            var y = s.y + 10f
            while (y < s.y + s.h) {
                var x = s.x + 10f
                while (x < s.x + s.w) { drawCircle(pal.olc.copy(alpha = 0.55f), 3f, Offset(x, y)); x += 20f }
                y += 20f
            }
        }
        St.LABEL -> {}
        else -> {   // KEY, RING, FACE, XB: bisa menyala / ditandai terpasang
            val base = when (it.st) {
                St.RING -> pal.bdf
                St.FACE -> pal.ink.copy(alpha = 0.10f)
                St.XB -> pal.ink.copy(alpha = 0.90f)
                else -> pal.keyf
            }
            val face = it.st == St.FACE && it.color != null
            if (on) {
                val gc = if (face) it.color!! else pal.acc
                strokeS(s, gc.copy(alpha = 0.35f), 46f)                       // cahaya
                if (it.st == St.RING) { fillS(s, pal.bdf); strokeS(s, pal.acc, 18f) }
                else { fillS(s, if (face) it.color!!.copy(alpha = 0.55f) else pal.acc); strokeS(s, gc, 9f) }
            } else if (map) {
                fillS(s, if (face) base else pal.mapf); strokeS(s, pal.acc, 14f)
            } else {
                fillS(s, base); strokeS(s, pal.rec, if (it.st == St.RING) 12f else 9f)
            }
        }
    }
    it.sym?.let { sy ->
        val col = when (it.symSt) {
            St.FACE -> if (on) Color.White else (it.color ?: pal.mute)
            St.ICONI -> pal.ink
            St.MK -> if (on) pal.onAcc else pal.rec
            else -> pal.mute
        }
        strokeS(sy, col, when (it.symSt) { St.FACE -> 13f; St.MK -> 16f; else -> 8f })
    }
    it.label?.let { t ->
        val col = when {
            it.st == St.LABEL -> pal.mute
            it.st == St.FACE && it.color != null -> if (on) Color.White else it.color
            on -> pal.onAcc
            else -> pal.ink
        }
        drawText(paint, t, it.lx, it.ly, it.ls, col)
    }
}

private fun hitItem(skin: Skin, o: Offset, sc: Float): SItem? {
    val x = o.x / sc + skin.vx; val y = o.y / sc + skin.vy
    return skin.keys.firstOrNull { it.shp.contains(x, y) }
}

/**
 * Gambar controller. Mode biasa: ketuk tombol untuk mengatur.
 * Mode uji (vm.testMode): tahan tombol / geser stik pada gambar untuk mencoba tanpa controller fisik.
 */
@Composable
fun ControllerView(vm: MainVm, skin: Skin, enabled: Boolean, onTap: (Pad) -> Unit, modifier: Modifier = Modifier) {
    val pal = LocalPal.current
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER; isFakeBoldText = true
        }
    }
    val test = vm.testMode
    Canvas(
        modifier.aspectRatio(skin.vw / skin.vh).alpha(if (enabled) 1f else 0.35f)
            .pointerInput(enabled, test, skin) {
                if (!enabled) return@pointerInput
                val sc = size.width / skin.vw
                if (!test) {
                    detectTapGestures { o -> hitItem(skin, o, sc)?.pad?.let(onTap) }
                } else awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pad = hitItem(skin, down.position, sc)?.pad ?: return@awaitEachGesture
                    val stickPad = when (pad) { Pad.LS, Pad.L3 -> Pad.LS; Pad.RS, Pad.R3 -> Pad.RS; else -> null }
                    if (stickPad != null) {
                        val c = skin.centers[stickPad] ?: return@awaitEachGesture
                        var capDown = pad == Pad.L3 || pad == Pad.R3
                        if (capDown) vm.key(pad, true)
                        fun upd(p: Offset) {
                            var x = (p.x / sc + skin.vx - c.x) / skin.radius
                            var y = -(p.y / sc + skin.vy - c.y) / skin.radius
                            val l = hypot(x, y)
                            if (l > 1f) { x /= l; y /= l }
                            vm.testStick(stickPad, x, y)
                            if (capDown && (p - down.position).getDistance() > 14f) { vm.key(pad, false); capDown = false }
                        }
                        upd(down.position)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (ch.pressed) upd(ch.position)
                            ch.consume()
                        } while (ch.pressed)
                        vm.testStick(stickPad, 0f, 0f)
                        if (capDown) vm.key(pad, false)
                    } else {
                        vm.key(pad, true)
                        do {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            ch.consume()
                        } while (ch.pressed)
                        vm.key(pad, false)
                    }
                }
            }
    ) {
        drawSkin(skin, pal, vm.padKeys, { vm.map[it]?.motors?.isNotEmpty() == true }, vm.padAx, paint)
    }
}
