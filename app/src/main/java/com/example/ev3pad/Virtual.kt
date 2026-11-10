package com.example.ev3pad

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.hypot

/**
 * Panggung virtual berukuran logis STAGE_W x STAGE_H, diskalakan seragam agar muat di area yang tersedia.
 * Karena skala seragam, layout yang sama tampil identik di HP maupun tablet, dan analog tetap lingkaran.
 */
@Composable
fun VirtualStage(vm: MainVm, edit: Boolean, modifier: Modifier, onTap: (Long) -> Unit) {
    val pal = LocalPal.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / STAGE_W, maxHeight.value / STAGE_H).coerceAtLeast(0.1f)
        val shape = RoundedCornerShape(14.dp)
        Box(
            Modifier.size((STAGE_W * scale).dp, (STAGE_H * scale).dp).clip(shape).border(1.dp, pal.line, shape)
                .then(if (edit) Modifier.drawBehind {
                    val st = 16f * scale * density
                    var x = st
                    while (x < size.width) {
                        var y = st
                        while (y < size.height) { drawCircle(pal.line, radius = 1.2f * density, center = Offset(x, y)); y += st }
                        x += st
                    }
                } else Modifier)
        ) {
            vm.items.forEach { item -> key(item.id) { VItemView(vm, item, scale, edit, onTap) } }
            if (vm.items.isEmpty()) Text(
                tr("Belum ada elemen. Ketuk + Tombol, + Slider, atau + Analog.", "No elements yet. Tap + Button, + Slider, or + Analog."),
                color = pal.mute, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
    }
}

@Composable
private fun VItemView(vm: MainVm, item: VItem, scale: Float, edit: Boolean, onTap: (Long) -> Unit) {
    val pal = LocalPal.current
    val dens = LocalDensity.current.density
    val itemState = rememberUpdatedState(item)
    val input = vm.vin[item.id] ?: VIn()
    val on = item.type == VType.BTN && (if (item.cfg.toggle) item.id in vm.vToggle else input.down)
    val blocked = !vm.passes(item.cfg)

    Box(
        Modifier.offset((item.x * scale).dp, (item.y * scale).dp).size((item.w * scale).dp, (item.h * scale).dp)
            .alpha(if (blocked && !edit) 0.35f else 1f)
            .then(
                if (edit) Modifier
                    .pointerInput(item.id, edit) { detectTapGestures { onTap(item.id) } }
                    .pointerInput(item.id, scale, edit) {
                        detectDragGestures(onDragEnd = { vm.snapItem(item.id) }, onDragCancel = { vm.snapItem(item.id) }) { ch, d ->
                            ch.consume(); vm.moveItem(item.id, d.x / dens / scale, d.y / dens / scale)
                        }
                    }
                    .drawBehind {
                        drawRoundRect(pal.acc, cornerRadius = CornerRadius(if (item.type == VType.ANALOG) size.minDimension else 14f * density),
                            style = Stroke(1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
                    }
                else Modifier.pointerInput(item.id, scale, edit) { controlGesture(vm, itemState) }
            )
    ) {
        when (item.type) {
            VType.BTN -> Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape((18f * scale).dp))
                    .background(if (on) pal.grad() else SolidColor(pal.key))
                    .border(1.dp, pal.line, RoundedCornerShape((18f * scale).dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.name, fontSize = (14f * scale).coerceAtLeast(9f).sp, fontWeight = FontWeight.SemiBold,
                    color = if (on) OnGrad else pal.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
            }
            else -> {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height; val m = minOf(w, h) / 2f
                    val grad = Brush.linearGradient(listOf(pal.acc, pal.acc2))
                    if (item.type == VType.ANALOG) {
                        val r = m * 0.72f
                        drawCircle(pal.key, radius = m)
                        drawCircle(pal.line, radius = r, style = Stroke(2f * density))
                        drawCircle(grad, radius = m * 0.28f, center = Offset(w / 2 + input.x * r, h / 2 - input.y * r))
                    } else {
                        drawRoundRect(pal.key, cornerRadius = CornerRadius(m))
                        val horiz = w >= h
                        val knobR = m * 0.62f
                        val pos = (input.x + 1f) / 2f
                        if (horiz) {
                            drawLine(pal.line, Offset(m, h / 2), Offset(w - m, h / 2), strokeWidth = h * 0.1f, cap = StrokeCap.Round)
                            drawCircle(grad, radius = knobR, center = Offset(m + pos * (w - 2 * m), h / 2))
                        } else {
                            drawLine(pal.line, Offset(w / 2, m), Offset(w / 2, h - m), strokeWidth = w * 0.1f, cap = StrokeCap.Round)
                            drawCircle(grad, radius = knobR, center = Offset(w / 2, h - m - pos * (h - 2 * m)))
                        }
                    }
                }
                Text(item.name, fontSize = (10f * scale).coerceAtLeast(7f).sp, fontFamily = FontFamily.Monospace, color = pal.mute,
                    modifier = Modifier.align(if (item.type == VType.ANALOG) Alignment.BottomCenter else Alignment.TopStart)
                        .padding(start = if (item.type == VType.ANALOG) 0.dp else (12f * scale).dp, top = (4f * scale).dp, bottom = (8f * scale).dp))
            }
        }
        if (edit) Box(
            Modifier.align(Alignment.BottomEnd).size(26.dp).padding(2.dp).background(pal.acc, CircleShape)
                .pointerInput(item.id, scale, edit) {
                    detectDragGestures(onDragEnd = { vm.snapItem(item.id) }, onDragCancel = { vm.snapItem(item.id) }) { ch, d ->
                        ch.consume(); vm.resizeItem(item.id, d.x / dens / scale, d.y / dens / scale)
                    }
                }
        )
    }
}

/** Gestur mode kontrol: tiap elemen punya gestur sendiri, jadi multi-sentuh (beberapa jari sekaligus) bekerja. */
private suspend fun PointerInputScope.controlGesture(vm: MainVm, itemState: State<VItem>) {
    awaitEachGesture {
        val item = itemState.value
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat(); val h = size.height.toFloat(); val m = minOf(w, h) / 2f

        fun calc(p: Offset): VIn = when (item.type) {
            VType.BTN -> VIn(true)
            VType.SLIDER -> VIn(true,
                if (w >= h) ((p.x - m) / (w - 2 * m) * 2f - 1f).coerceIn(-1f, 1f)
                else (1f - (p.y - m) / (h - 2 * m) * 2f).coerceIn(-1f, 1f))
            VType.ANALOG -> {
                val r = m * 0.72f
                var x = (p.x - w / 2f) / r; var y = -(p.y - h / 2f) / r
                val l = hypot(x, y)
                if (l > 1f) { x /= l; y /= l }
                VIn(true, x, y)
            }
        }

        var last = calc(down.position)
        vm.setVin(item.id, last)
        do {
            val ev = awaitPointerEvent()
            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
            if (ch.pressed) { last = calc(ch.position); vm.setVin(item.id, last) }
            ch.consume()
        } while (ch.pressed)

        vm.setVin(item.id, when (item.type) {
            VType.BTN -> VIn(false)
            VType.SLIDER -> if (itemState.value.cfg.spring) VIn(false, 0f) else VIn(false, last.x)
            VType.ANALOG -> VIn(false)
        })
    }
}

// ===================== layar penuh =====================

@Composable
fun FullscreenView(vm: MainVm) {
    val pal = LocalPal.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().background(pal.bg2).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                val on = vm.ev3 == LinkState.ON
                MiniChip("EV3", "", dot = if (on) pal.ok else pal.bad)
                if (on) {
                    for (i in 0..3) if (vm.motorPorts[i] != null) {
                        val sp = vm.speeds[i]
                        MiniChip("ABCD"[i].toString(), (if (sp > 0) "+" else "") + "$sp%", hot = sp != 0)
                    }
                    for (i in 0..3) {
                        val m = vm.sensorMode(i)
                        if (m != null) MiniChip("${i + 1}", fmtSensor(m, vm.sensorValue(i)), sensor = true)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(30.dp).clip(CircleShape).border(1.dp, pal.line, CircleShape).clickable { vm.fullscreen = false }, contentAlignment = Alignment.Center) {
                Text("✕", fontSize = 13.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(pal.line))
        VirtualStage(vm, false, Modifier.weight(1f).fillMaxWidth().padding(8.dp)) { }
    }
}

@Composable
private fun MiniChip(label: String, value: String, dot: androidx.compose.ui.graphics.Color? = null, hot: Boolean = false, sensor: Boolean = false) {
    val pal = LocalPal.current
    val sh = RoundedCornerShape(99.dp)
    val border = when { hot -> pal.acc; sensor -> pal.ok.copy(alpha = 0.4f); else -> pal.line }
    Row(Modifier.clip(sh).border(1.dp, border, sh).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(6.dp).background(dot, CircleShape)); Spacer(Modifier.width(5.dp)) }
        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = pal.mute, maxLines = 1)
        if (value.isNotEmpty()) { Spacer(Modifier.width(5.dp)); Text(value, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = if (hot) pal.acc else pal.ink, maxLines = 1) }
    }
}

/** Saat layar penuh: paksa landscape, sembunyikan bilah sistem (mode imersif), layar tetap menyala. */
@Composable
fun FullscreenEffects(on: Boolean) {
    val view = LocalView.current
    val act = LocalContext.current as? Activity
    DisposableEffect(on) {
        val win = act?.window
        val ctl = if (win != null) WindowCompat.getInsetsController(win, view) else null
        if (on) {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = true
        }
        onDispose {
            if (on) {
                act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                ctl?.show(WindowInsetsCompat.Type.systemBars())
                view.keepScreenOn = false
            }
        }
    }
}
