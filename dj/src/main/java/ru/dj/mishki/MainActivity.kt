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
import kotlinx.coroutines.delay

private fun timeText(ms: Int): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}
private data class Track(val title: String, val uri: Uri)
private class Deck {
    var player: MediaPlayer? = null
    var trackIndex by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var position by mutableIntStateOf(0)
    var duration by mutableIntStateOf(0)
    var gain by mutableFloatStateOf(1f)
    fun stopPlayback() {
        runCatching { player?.stop(); player?.release() }
        player = null
        playing = false
        position = 0
    }
    fun release() {
        stopPlayback()
        trackIndex = -1
        duration = 0
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { DjMishkiApp() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DjMishkiApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("dj_mishki", 0) }
    val tracks = remember { mutableStateListOf<Track>() }
    val effects = remember { mutableStateListOf<Track>() }
    var section by remember { mutableStateOf("Пульт") }
    var selected by remember { mutableIntStateOf(-1) }
    var cross by remember { mutableFloatStateOf(0f) }
    var master by remember { mutableFloatStateOf(0.85f) }
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Добавь музыку с телефона") }
    val deckA = remember { Deck() }
    val deckB = remember { Deck() }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Музыкальный файл"
            if (tracks.none { it.uri == uri }) tracks.add(Track(name, uri))
        }
        prefs.edit().putString("tracks", tracks.joinToString("|") { it.uri.toString() }).apply()
        message = if (tracks.isEmpty()) "Добавь музыку с телефона" else "Добавлено песен: ${tracks.size}"
    }
    val effectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Звуковой эффект"
            if (effects.none { it.uri == uri }) effects.add(Track(name, uri))
        }
    }
    fun applyMix() {
        val a = master * (1f - cross) * deckA.gain
        val b = master * cross * deckB.gain
        runCatching { deckA.player?.setVolume(a, a) }
        runCatching { deckB.player?.setVolume(b, b) }
    }
    fun loadDeck(deck: Deck, index: Int) {
        if (index !in tracks.indices) return
        deck.release()
        runCatching {
            deck.trackIndex = index
            deck.player = MediaPlayer().apply {
                setDataSource(context, tracks[index].uri)
                setOnCompletionListener { deck.playing = false }
                prepare()
                deck.duration = duration
                start()
            }
            deck.duration = deck.player?.duration ?: 0
            deck.position = 0
            deck.playing = true
            applyMix()
            message = "Загружено: ${tracks[index].title}"
        }.onFailure { deck.release(); message = "Не удалось открыть файл. Попробуй другой формат." }
    }
    fun stopAll() { deckA.release(); deckB.release(); message = "Воспроизведение остановлено" }
    fun playEffect(track: Track) {
        runCatching {
            val p = MediaPlayer()
            p.setDataSource(context, track.uri)
            p.setOnCompletionListener { it.release() }
            p.prepare()
            p.start()
        }.onFailure { message = "Не удалось воспроизвести эффект" }
    }
    LaunchedEffect(Unit) {
        prefs.getString("tracks", null)?.split("|")?.filter { it.isNotBlank() }?.forEach { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Музыкальный файл"
                tracks.add(Track(name, uri))
            }
        }
        while (true) {
            delay(400)
            tick++
            runCatching { if (deckA.playing) deckA.position = deckA.player?.currentPosition ?: 0 }
            runCatching { if (deckB.playing) deckB.position = deckB.player?.currentPosition ?: 0 }
        }
    }
    DisposableEffect(Unit) { onDispose { deckA.release(); deckB.release(); tone.release() } }
    val bg = Color(0xFF10121A); val panel = Color(0xFF1C2030); val neon = Color(0xFF55E6A5)
    MaterialTheme(colorScheme = darkColorScheme(primary = neon, background = bg, surface = panel)) {
        Scaffold(containerColor = bg, topBar = {
            TopAppBar(title = { Column {
                Text("DJ MISHKI", fontWeight = FontWeight.Black, color = neon, letterSpacing = 2.sp)
                Text("Пульт управления мероприятием", fontSize = 11.sp, color = Color.LightGray)
            } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = bg))
        }, bottomBar = {
            NavigationBar(containerColor = panel) {
                listOf("Пульт", "Музыка", "Эффекты", "Игры").forEach { label ->
                    NavigationBarItem(selected = section == label, onClick = { section = label }, icon = { Text(when(label) { "Пульт" -> "🎧"; "Музыка" -> "♫"; "Эффекты" -> "🔊"; else -> "🎮" }) }, label = { Text(label) })
                }
            }
        }) { pad ->
            Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp, vertical = 10.dp)) {
                when(section) {
                    "Пульт" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("A" to deckA, "B" to deckB).forEach { (label, deck) ->
                                Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ПЛЕЕР $label", color = neon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(tracks.getOrNull(deck.trackIndex)?.title ?: "Не загружен", maxLines = 1, fontSize = 11.sp)
                                        Text("${timeText(deck.position)} / ${timeText(deck.duration)}", fontSize = 10.sp, color = Color.LightGray)
                                        Slider(
                                            value = if (deck.duration > 0) deck.position.toFloat().coerceIn(0f, deck.duration.toFloat()) else 0f,
                                            onValueChange = { deck.position = it.toInt() },
                                            onValueChangeFinished = { runCatching { deck.player?.seekTo(deck.position) } },
                                            valueRange = 0f..deck.duration.coerceAtLeast(1).toFloat(),
                                            enabled = deck.trackIndex >= 0 && deck.duration > 0
                                        )
                                        Text("Громкость ${(deck.gain * 100).toInt()}%", fontSize = 10.sp, color = Color.LightGray)
                                        Slider(value = deck.gain, onValueChange = { deck.gain = it; applyMix() })
                                        FilledTonalButton(
                                            onClick = {
                                                when {
                                                    deck.playing -> {
                                                        deck.player?.pause()
                                                        deck.playing = false
                                                    }
                                                    deck.player != null -> {
                                                        deck.player?.start()
                                                        deck.playing = true
                                                    }
                                                    selected >= 0 -> loadDeck(deck, selected)
                                                }
                                            },
                                            enabled = deck.trackIndex >= 0 || selected >= 0,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Text(if (deck.playing) "Ⅱ  Пауза" else "▶  Плей", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("КРОССФЕЙДЕР", color = neon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("A", fontSize = 11.sp); Text("B", fontSize = 11.sp) }
                                Slider(value = cross, onValueChange = { cross = it; applyMix() })
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Общая громкость", fontSize = 11.sp, color = Color.LightGray, modifier = Modifier.weight(1f))
                                    Text("${(master * 100).toInt()}%", fontSize = 11.sp, color = neon)
                                }
                                Slider(value = master, onValueChange = { master = it; applyMix() })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { section = "Музыка" }, modifier = Modifier.fillMaxWidth()) {
                            Text("♫  ОТКРЫТЬ МУЗЫКАЛЬНУЮ БИБЛИОТЕКУ")
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { stopAll() }, modifier = Modifier.weight(1f)) { Text("Остановить всё") }
                            OutlinedButton(onClick = { section = "Эффекты" }, modifier = Modifier.weight(1f)) { Text("Звуковые эффекты") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(message, color = Color.LightGray, fontSize = 11.sp)
                    }
                    "Музыка" -> {
                        Button(onClick = { musicPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить песни с телефона") }
                        Spacer(Modifier.height(8.dp))
                        Text("Песен в библиотеке: ${tracks.size}", color = neon)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(tracks) { i, t ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(t.title, Modifier.weight(1f), maxLines = 2)
                                        TextButton(onClick = { selected = i; loadDeck(deckA, i) }) { Text("В A") }
                                        TextButton(onClick = { selected = i; loadDeck(deckB, i) }) { Text("В B") }
                                        TextButton(onClick = { tracks.removeAt(i); prefs.edit().putString("tracks", tracks.joinToString("|") { it.uri.toString() }).apply(); if (deckA.trackIndex == i) deckA.release(); if (deckB.trackIndex == i) deckB.release() }) { Text("Удалить") }
                                    }
                                }
                            }
                        }
                    }
                    "Эффекты" -> {
                        Text("ЗВУКОВАЯ ПАНЕЛЬ", color = neon, fontWeight = FontWeight.Bold)
                        Text("Встроенные сигналы", color = Color.LightGray, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        val built = listOf("Старт" to ToneGenerator.TONE_PROP_BEEP, "Внимание" to ToneGenerator.TONE_PROP_PROMPT, "Подтверждение" to ToneGenerator.TONE_PROP_ACK, "Сигнал" to ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, "Оповещение" to ToneGenerator.TONE_CDMA_NETWORK_BUSY, "Победа" to ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE)
                        built.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (title, id) -> Button(onClick = { tone.startTone(id, 650) }, modifier = Modifier.weight(1f).height(62.dp)) { Text(title) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        Button(onClick = { effectPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить свои звуковые эффекты") }
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(effects) { i, effect ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(effect.title, Modifier.weight(1f))
                                        Button(onClick = { playEffect(effect) }) { Text("▶ Играть") }
                                        TextButton(onClick = { effects.removeAt(i) }) { Text("Убрать") }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text("ИГРЫ И ИНТЕРАКТИВЫ", color = neon, fontWeight = FontWeight.Bold)
                        Text("Выбери конкурс — приложение запустит таймер и поможет вести раунд.", color = Color.LightGray, fontSize = 12.sp)
                        var seconds by remember { mutableIntStateOf(15) }
                        var remaining by remember { mutableIntStateOf(15) }
                        var running by remember { mutableStateOf(false) }
                        LaunchedEffect(running, tick) { if (running && tick % 2 == 0 && remaining > 0) remaining--; if (remaining == 0) running = false }
                        Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ТАЙМЕР КОНКУРСА", color = neon, fontWeight = FontWeight.Bold)
                                Text("$remaining сек.", fontSize = 38.sp, fontWeight = FontWeight.Black)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(onClick = { seconds = (seconds - 5).coerceAtLeast(5); remaining = seconds }) { Text("−5") }
                                    Text("$seconds секунд")
                                    TextButton(onClick = { seconds = (seconds + 5).coerceAtMost(120); remaining = seconds }) { Text("+5") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { if (remaining == 0) remaining = seconds; running = true }) { Text("Старт") }
                                    OutlinedButton(onClick = { running = false; remaining = seconds }) { Text("Сброс") }
                                }
                            }
                        }
                        listOf("Угадай мелодию", "Музыкальная пауза", "Танцевальный батл", "Замри под музыку", "Повтори движение").forEach { title ->
                            Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth().padding(top = 7.dp).clickable { remaining = seconds; running = false; message = "Конкурс: $title" }) {
                                Text(title, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}
