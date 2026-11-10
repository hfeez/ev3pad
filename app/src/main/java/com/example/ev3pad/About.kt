package com.example.ev3pad

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val IG_URL = "https://www.instagram.com/hfeez_ap"
private const val SAWERIA_URL = "https://saweria.co/feexxyz"
private const val TRAKTEER_URL = "https://trakteer.id/hfeez_ap"

/** Popup credits: tampil saat aplikasi dibuka dan dari Pengaturan > About this app. */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    val pal = LocalPal.current
    val shape = RoundedCornerShape(24.dp)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.padding(16.dp).widthIn(max = 460.dp).heightIn(max = 660.dp).clip(shape)
                .background(pal.card).border(1.dp, pal.grad(), shape)
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(pal.acc.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 0.15f, 0f), radius = size.width * 0.8f))
                    drawRect(Brush.radialGradient(listOf(pal.acc2.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.7f))
                }
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(painterResource(R.drawable.about_icon), contentDescription = null,
                    modifier = Modifier.size(76.dp).clip(CircleShape).border(1.dp, pal.line, CircleShape))
                Text(buildAnnotatedString {
                    append("EV3 ")
                    withStyle(SpanStyle(fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = pal.acc, fontSize = 26.sp)) { append("Direct Control") }
                }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                MonoLbl("About this app")

                Text(
                    "EV3 Direct Control is an open-source project created to bridge the gap between your favorite controller and the LEGO Mindstorms EV3.",
                    fontSize = 14.sp, color = pal.ink, textAlign = TextAlign.Center
                )

                AboutBlock {
                    MonoLbl("Developed by")
                    Text("Hafiz Ari Putra", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = pal.acc)
                    Text("Proudly made in 🇮🇩 Indonesia with passion for robotics and gaming.", fontSize = 13.5.sp, color = pal.ink)
                }

                Text(
                    "If you find this app helpful for your projects, creations, or classrooms, please consider supporting its development!",
                    fontSize = 13.5.sp, color = pal.mute, textAlign = TextAlign.Center
                )

                AboutBlock {
                    Text("💬 Let's Connect:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Follow me on Instagram for updates and more projects:", fontSize = 13.5.sp, color = pal.mute)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👉 ", fontSize = 14.sp)
                        AboutLink("@hfeez_ap", IG_URL)
                    }
                }

                AboutBlock {
                    Text("☕ Support My Work:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("If you love this app and want to buy me a coffee, you can donate here:", fontSize = 13.5.sp, color = pal.mute)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👉 Donate via ", fontSize = 14.sp)
                        AboutLink("Saweria", SAWERIA_URL)
                        Text(" / ", fontSize = 14.sp, color = pal.mute)
                        AboutLink("Trakteer", TRAKTEER_URL)
                    }
                }

                Text("Thank you for using the app, and happy building! 🤖🎮", fontSize = 13.5.sp, color = pal.ink, textAlign = TextAlign.Center)
                PriButton("Close", Modifier.fillMaxWidth(), onClick = onClose)
            }
        }
    }
}

@Composable
private fun AboutBlock(content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPal.current
    val sh = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(sh).background(pal.bg2.copy(alpha = 0.7f)).border(1.dp, pal.line, sh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp), content = content
    )
}

@Composable
private fun AboutLink(text: String, url: String) {
    val pal = LocalPal.current
    val uri = LocalUriHandler.current
    Text(text, color = pal.acc, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { try { uri.openUri(url) } catch (e: Exception) { } })
}
