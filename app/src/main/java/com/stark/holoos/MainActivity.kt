package com.stark.holoos

import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class HoloTheme(val name: String, val color: Color)
private val holoThemes = listOf(
    HoloTheme("Arc Ciano", Color(0xFF00F3FF)),
    HoloTheme("Titânio Dourado", Color(0xFFFFB020)),
    HoloTheme("Mark Vermelho", Color(0xFFFF3C48)),
    HoloTheme("Magenta Neon", Color(0xFFE646FF)),
    HoloTheme("Verde Matrix", Color(0xFF28FF8C)),
    HoloTheme("Azul Ultra", Color(0xFF508CFF))
)
private data class InstalledApp(val label: String, val packageName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(1, 4, 9)
        window.navigationBarColor = android.graphics.Color.rgb(1, 4, 9)
        setContent { HoloOS(this) }
    }
}

@Composable
private fun HoloOS(activity: ComponentActivity) {
    var themeIndex by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("J.A.R.V.I.S. online • toque no reator para falar") }
    var now by remember { mutableStateOf(Date()) }
    var voiceCommand by remember { mutableStateOf("") }
    val accent = holoThemes[themeIndex].color
    val bg = Color(0xFF010409)
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
        voiceCommand = spoken.lowercase(Locale.ROOT)
        when {
            voiceCommand.contains("calcul") -> { status = "Calculadora pronta"; openPackage(activity, "com.google.android.calculator") }
            voiceCommand.contains("whats") -> { status = "Abrindo transmissão WhatsApp"; openPackage(activity, "com.whatsapp") }
            voiceCommand.contains("youtube") -> { status = "Abrindo YouTube"; openPackage(activity, "com.google.android.youtube") }
            voiceCommand.contains("tema") || voiceCommand.contains("cor") -> { themeIndex = (themeIndex + 1) % holoThemes.size; status = "Frequência de cor alterada" }
            voiceCommand.contains("nota") -> { showNotes = true; status = "Notas holográficas" }
            voiceCommand.contains("hora") -> { status = "Senhor, agora são " + SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()) }
            else -> status = if (spoken.isNotBlank()) "Comando recebido: $spoken" else "Nenhum comando reconhecido"
        }
    }

    LaunchedEffect(Unit) {
        while (true) { now = Date(); delay(1000) }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = Color(0xFF061426))) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF071A2E), bg)))) {
            StarField(accent)
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    Modifier.fillMaxWidth().border(1.dp, accent.copy(alpha = .55f), RoundedCornerShape(14.dp))
                        .background(Color(0x88001628), RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("STARK HOLO-OS", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("SISTEMA PESSOAL • v13", color = Color.White.copy(.55f), fontSize = 9.sp, letterSpacing = 1.2.sp)
                    }
                    val bm = remember { activity.getSystemService(BATTERY_SERVICE) as BatteryManager }
                    val battery = remember { bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BatteryFull, null, tint = accent, modifier = Modifier.size(16.dp))
                        Text("$battery%", color = Color.White, fontSize = 12.sp)
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    QuickChip("◉  ALERTA", accent) { status = "Sistemas operacionais • nenhuma falha detectada"; Toast.makeText(activity, "HoloOS online", Toast.LENGTH_SHORT).show() }
                    QuickChip("⌘  TEMA", accent) { themeIndex = (themeIndex + 1) % holoThemes.size }
                    QuickChip("⚙  AJUSTES", accent) { showSettings = true }
                }

                Spacer(Modifier.height(12.dp))
                Text(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(now), color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 4.sp)
                Text(SimpleDateFormat("EEEE, dd 'DE' MMMM", Locale("pt", "BR")).format(now).uppercase(Locale("pt", "BR")), color = accent, fontSize = 10.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(2.dp))
                Reactor(accent, Modifier.size(178.dp).clickable {
                    status = "Escutando comando de voz…"
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga um comando para J.A.R.V.I.S.")
                    }
                    try { speechLauncher.launch(intent) } catch (_: Exception) {
                        status = "Reconhecimento de voz indisponível neste dispositivo"
                    }
                })
                Text("J.A.R.V.I.S.", color = Color.White, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Medium)
                Text(status, color = accent.copy(alpha = .9f), fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))

                Text("MÓDULOS DO SISTEMA", color = accent.copy(.75f), fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                val apps = remember { loadApps(activity) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { HoloTile("📞", "Telefone", accent) { openIntent(activity, Intent(Intent.ACTION_DIAL)) } }
                    item { HoloTile("💬", "WhatsApp", accent) { if (!openPackage(activity, "com.whatsapp")) status = "WhatsApp não encontrado" } }
                    item { HoloTile("🌐", "HoloWeb", accent) { openIntent(activity, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com"))) } }
                    item { HoloTile("🧮", "Calc", accent) { if (!openPackage(activity, "com.google.android.calculator")) status = "Calculadora não encontrada" } }
                    item { HoloTile("📝", "Notas", accent) { showNotes = true } }
                    item { HoloTile("▶️", "YouTube", accent) { if (!openPackage(activity, "com.google.android.youtube")) openIntent(activity, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com"))) } }
                    item { HoloTile("🎵", "Spotify", accent) { if (!openPackage(activity, "com.spotify.music")) openIntent(activity, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com"))) } }
                    item { HoloTile("🛰️", "Mapas", accent) { openIntent(activity, Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://maps.google.com"))) } }
                    item { HoloTile("⚙️", "Ajustes", accent) { showSettings = true } }
                    item { HoloTile("📦", "Aplicativos", accent) { status = "Toque em um aplicativo abaixo para abri-lo"; showSettings = true } }
                    items(apps.take(20)) { app ->
                        HoloTile("◇", app.label, accent) {
                            try {
                                val launch = activity.packageManager.getLaunchIntentForPackage(app.packageName)
                                if (launch != null) activity.startActivity(launch)
                            } catch (_: Exception) { status = "Não foi possível abrir ${app.label}" }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(18.dp)).border(1.dp, accent.copy(.55f), RoundedCornerShape(18.dp))
                        .background(Color(0xCC001628), RoundedCornerShape(18.dp)).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DockButton("📡", "Alertas", accent) { status = "Canal de alertas HoloOS ativo" }
                    DockButton("📞", "Telefone", accent) { openIntent(activity, Intent(Intent.ACTION_DIAL)) }
                    DockButton("⌂", "Início", accent) { showSettings = false; showNotes = false; status = "J.A.R.V.I.S. online" }
                    DockButton("🎙", "Voz", accent) {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                        }
                        try { speechLauncher.launch(intent) } catch (_: Exception) { status = "Voz indisponível" }
                    }
                    DockButton("✧", "Tema", accent) { themeIndex = (themeIndex + 1) % holoThemes.size }
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                containerColor = Color(0xFF061426),
                title = { Text("AJUSTES HOLO-OS", color = accent, letterSpacing = 2.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Frequência do cristal", color = Color.White)
                        holoThemes.forEachIndexed { index, theme ->
                            Row(
                                Modifier.fillMaxWidth().clickable { themeIndex = index }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(22.dp).background(theme.color, CircleShape))
                                Text(theme.name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 12.dp))
                                if (themeIndex == index) Text("ATIVO", color = theme.color, fontSize = 10.sp)
                            }
                        }
                        Text("Os efeitos visuais são locais. Permissões especiais são solicitadas separadamente quando necessárias.", color = Color.White.copy(.6f), fontSize = 11.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("CONCLUÍDO", color = accent) } }
            )
        }
        if (showNotes) {
            AlertDialog(
                onDismissRequest = { showNotes = false },
                containerColor = Color(0xFF061426),
                title = { Text("NOTAS HOLOGRÁFICAS", color = accent, letterSpacing = 1.5.sp) },
                text = {
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp),
                        label = { Text("Sua nota") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accent, unfocusedBorderColor = accent.copy(.4f),
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedLabelColor = accent, unfocusedLabelColor = accent.copy(.7f)
                        )
                    )
                },
                confirmButton = { TextButton(onClick = { showNotes = false; status = "Nota mantida nesta sessão" }) { Text("SALVAR", color = accent) } },
                dismissButton = { TextButton(onClick = { notes = ""; status = "Notas limpas" }) { Text("LIMPAR", color = accent) } }
            )
        }
    }
}

@Composable
private fun StarField(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stepX = size.width / 11f
        val stepY = size.height / 18f
        for (i in 0..10) for (j in 0..17) {
            val x = i * stepX + ((i * 37 + j * 13) % 23)
            val y = j * stepY + ((j * 19 + i * 7) % 29)
            drawCircle(accent.copy(alpha = .12f + ((i + j) % 5) * .035f), radius = if ((i + j) % 4 == 0) 1.8f else 1f, center = Offset(x, y))
        }
        val horizon = size.height * .78f
        for (i in 0..12) {
            val y = horizon + i * i * 1.15f
            drawLine(accent.copy(alpha = .08f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        for (i in -10..10) drawLine(accent.copy(alpha = .06f), Offset(size.width / 2f, horizon), Offset(size.width / 2f + i * size.width / 10f, size.height), 1f)
    }
}

@Composable
private fun Reactor(accent: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "reactor")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(9000), RepeatMode.Restart), label = "rotation")
    val pulse by transition.animateFloat(.72f, 1f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension * .43f
        drawCircle(accent.copy(alpha = .08f * pulse), radius * 1.25f, c)
        drawCircle(accent.copy(alpha = .18f), radius * .78f, c)
        drawCircle(accent.copy(alpha = .85f), radius, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawCircle(accent.copy(alpha = .55f), radius * .84f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), rotation)))
        for (i in 0 until 8) {
            val a = Math.toRadians((rotation + i * 45.0))
            val inner = radius * .65f
            val outer = radius * .83f
            drawLine(accent, Offset(c.x + kotlin.math.cos(a).toFloat() * inner, c.y + kotlin.math.sin(a).toFloat() * inner), Offset(c.x + kotlin.math.cos(a).toFloat() * outer, c.y + kotlin.math.sin(a).toFloat() * outer), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .85f), accent.copy(alpha = .65f), accent.copy(alpha = .05f)), c, radius * .58f), radius * .58f, c)
        drawCircle(accent, radius * .24f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun RowScope.QuickChip(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).border(1.dp, accent.copy(.45f), RoundedCornerShape(10.dp))
            .background(accent.copy(.09f), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 9.sp, letterSpacing = .4.sp, textAlign = TextAlign.Center) }
}

@Composable
private fun HoloTile(icon: String, label: String, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier.size(54.dp).shadow(7.dp, RoundedCornerShape(15.dp))
                .border(1.dp, accent.copy(.9f), RoundedCornerShape(15.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(.23f), accent.copy(.06f))), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 23.sp) }
        Text(label, color = Color(0xFFD8FCFF), fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DockButton(icon: String, label: String, accent: Color, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick).padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, color = accent, fontSize = 21.sp, fontWeight = FontWeight.Light)
        Text(label, color = Color.White.copy(.65f), fontSize = 8.sp)
    }
}

private fun loadApps(activity: ComponentActivity): List<InstalledApp> {
    return try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        activity.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .map { InstalledApp(it.loadLabel(activity.packageManager).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != activity.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    } catch (_: Exception) { emptyList() }
}

private fun openPackage(activity: ComponentActivity, packageName: String): Boolean {
    return try {
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        activity.startActivity(intent)
        true
    } catch (_: Exception) { false }
}

private fun openIntent(activity: ComponentActivity, intent: Intent) {
    try { activity.startActivity(intent) }
    catch (_: Exception) { Toast.makeText(activity, "Aplicativo não disponível", Toast.LENGTH_SHORT).show() }
}
