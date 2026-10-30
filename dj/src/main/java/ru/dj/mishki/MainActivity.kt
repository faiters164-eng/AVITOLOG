package ru.dj.mishki

import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class Track(val title: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DjMishkiApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DjMishkiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tracks = remember { mutableStateListOf<Track>() }
    var current by remember { mutableIntStateOf(-1) }
    var playing by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("Пульт") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var volume by remember { mutableFloatStateOf(0.8f) }
    val tones = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 85) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Музыкальный файл"
            if (tracks.none { it.uri == uri }) tracks.add(Track(name, uri))
        }
    }
    fun stop() { player?.runCatching { stop(); release() }; player = null; playing = false }
    fun play(index: Int) {
        if (index !in tracks.indices) return
        stop()
        current = index
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(context, tracks[index].uri)
                setOnCompletionListener {
                    if (current + 1 < tracks.size) play(current + 1) else playing = false
                }
                prepare()
                setVolume(volume, volume)
                start()
            }
            playing = true
        }.onFailure { playing = false }
    }
    DisposableEffect(Unit) { onDispose { stop(); tones.release() } }
    val bg = Color(0xFF10121A)
    val panel = Color(0xFF1C2030)
    val neon = Color(0xFF55E6A5)
    MaterialTheme(colorScheme = darkColorScheme(primary = neon, background = bg, surface = panel)) {
        Scaffold(containerColor = bg, topBar = {
            TopAppBar(title = { Column {
                Text("DJ MISHKI", fontWeight = FontWeight.Black, color = neon, letterSpacing = 2.sp)
                Text("Виртуальный диджей мероприятий", fontSize = 11.sp, color = Color.LightGray)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = bg))
        }, bottomBar = {
            NavigationBar(containerColor = panel) {
                listOf("Пульт", "Музыка", "Эффекты", "Игры").forEach { label ->
                    NavigationBarItem(selected = section == label, onClick = { section = label }, icon = { Text(when(label) { "Пульт" -> "🎧"; "Музыка" -> "♫"; "Эффекты" -> "🔊"; else -> "🎮" }) }, label = { Text(label) })
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
                when(section) {
                    "Пульт" -> {
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("СЕЙЧАС ИГРАЕТ", color = neon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text(tracks.getOrNull(current)?.title ?: "Выбери музыку", fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Spacer(Modifier.height(24.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { if (current > 0) play(current - 1) }, enabled = current > 0) { Text("⏮") }
                                    Button(onClick = {
                                        if (playing) { player?.pause(); playing = false }
                                        else if (current >= 0 && player != null) { player?.start(); playing = true }
                                        else if (tracks.isNotEmpty()) play(if (current >= 0) current else 0)
                                        else picker.launch(arrayOf("audio/*"))
                                    }, modifier = Modifier.size(76.dp), shape = RoundedCornerShape(24.dp)) { Text(if (playing) "Ⅱ" else "▶", fontSize = 26.sp) }
                                    FilledTonalButton(onClick = { if (current + 1 < tracks.size) play(current + 1) }, enabled = current + 1 < tracks.size) { Text("⏭") }
                                }
                                Spacer(Modifier.height(20.dp))
                                Text("Громкость", color = Color.LightGray)
                                Slider(value = volume, onValueChange = { volume = it; player?.setVolume(it,it) })
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ ДОБАВИТЬ МУЗЫКУ С ТЕЛЕФОНА") }
                        Spacer(Modifier.height(10.dp))
                        Text("Быстрые действия", color = Color.White, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { stop() }, modifier = Modifier.weight(1f)) { Text("СТОП") }
                            OutlinedButton(onClick = { tones.startTone(ToneGenerator.TONE_PROP_BEEP, 350) }, modifier = Modifier.weight(1f)) { Text("СИГНАЛ") }
                            OutlinedButton(onClick = { tones.startTone(ToneGenerator.TONE_PROP_ACK, 350) }, modifier = Modifier.weight(1f)) { Text("ГОТОВО") }
                        }
                    }
                    "Музыка" -> {
                        Button(onClick = { picker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить песни") }
                        Spacer(Modifier.height(12.dp))
                        if (tracks.isEmpty()) Text("Здесь появятся песни, которые ты добавишь с телефона.", color = Color.LightGray)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(tracks) { index, track ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth().clickable { play(index) }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (index == current) "▶  " else "♫  ", color = neon)
                                        Text(track.title, modifier = Modifier.weight(1f), maxLines = 2)
                                        TextButton(onClick = { tracks.removeAt(index); if (index == current) stop(); if (index < current) current-- }) { Text("Убрать") }
                                    }
                                }
                            }
                        }
                    }
                    "Эффекты" -> {
                        Text("ЗВУКОВЫЕ ЭФФЕКТЫ", color = neon, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        val effects = listOf("Фанфары" to ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, "Аплодисменты" to ToneGenerator.TONE_PROP_ACK, "Барабанная дробь" to ToneGenerator.TONE_CDMA_NETWORK_BUSY, "Сигнал старта" to ToneGenerator.TONE_PROP_BEEP, "Внимание" to ToneGenerator.TONE_PROP_PROMPT, "Победа" to ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE)
                        effects.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { (name, tone) ->
                                    Button(onClick = { tones.startTone(tone, 700) }, modifier = Modifier.weight(1f).height(76.dp), shape = RoundedCornerShape(16.dp)) { Text(name) }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                        Text("Это встроенные сигналы-подсказки. Позже сюда можно добавить собственные аудиофайлы эффектов.", color = Color.LightGray, fontSize = 12.sp)
                    }
                    else -> {
                        Text("ИГРЫ И ИНТЕРАКТИВЫ", color = neon, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text("Музыкальные конкурсы появятся в следующем этапе.", color = Color.LightGray)
                        listOf("Угадай мелодию", "Музыкальная пауза", "Танцевальный батл", "Замри под музыку").forEach {
                            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(it, Modifier.padding(16.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
