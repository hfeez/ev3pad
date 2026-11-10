package com.example.ev3pad

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OnGrad = Color(0xFF04121A)

@Composable
fun Lbl(t: String, modifier: Modifier = Modifier) = Text(t, fontSize = 12.sp, color = LocalPal.current.mute, modifier = modifier)

@Composable
fun MonoLbl(t: String, modifier: Modifier = Modifier) =
    Text(t.uppercase(), fontSize = 10.5.sp, letterSpacing = 1.4.sp, fontFamily = FontFamily.Monospace, color = LocalPal.current.mute, modifier = modifier)

@Composable
fun PriButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val pal = LocalPal.current
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = pal.acc, contentColor = pal.onAcc)) { Text(text, maxLines = 1) }
}

@Composable
fun SmallBtn(text: String, color: Color? = null, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(34.dp)) {
        Text(text, fontSize = 12.sp, maxLines = 1, color = color ?: LocalPal.current.ink)
    }
}

/** Pilihan segmen; segmen terpilih memakai gradasi teal-ungu seperti di prototipe. */
@Composable
fun <T> Seg(opts: List<Pair<T, String>>, sel: T, dim: (T) -> Boolean = { false }, onSel: (T) -> Unit) {
    val pal = LocalPal.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        opts.forEach { (v, label) ->
            val a = v == sel
            val sh = RoundedCornerShape(10.dp)
            Box(
                Modifier.weight(1f).alpha(if (dim(v) && !a) 0.45f else 1f).clip(sh)
                    .then(if (a) Modifier.background(pal.grad(), sh) else Modifier.border(1.dp, pal.line, sh))
                    .clickable { onSel(v) }.padding(horizontal = 4.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontSize = 13.sp, textAlign = TextAlign.Center,
                    color = if (a) OnGrad else pal.ink, fontWeight = if (a) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** Dropdown kecil. */
@Composable
fun <T> Drop(opts: List<Pair<T, String>>, sel: T, onSel: (T) -> Unit) {
    val pal = LocalPal.current
    var open by remember { mutableStateOf(false) }
    val sh = RoundedCornerShape(8.dp)
    Box {
        Box(Modifier.clip(sh).border(1.dp, pal.line, sh).clickable { open = true }.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text((opts.firstOrNull { it.first == sel }?.second ?: "–") + " ▾", fontSize = 13.sp, color = pal.ink)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            opts.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onSel(v) }) }
        }
    }
}
