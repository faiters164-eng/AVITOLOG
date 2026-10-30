package ru.dj.mishki

import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject
import android.app.DownloadManager
import android.os.Environment

private data class Track(val title: String, val uri: Uri)
private data class OnlineTrack(val id: String, val title: String, val artist: String, val provider: String, val streamUrl: String, val downloadUrl: String?, val artwork: String?)
private fun timeText(ms: Int): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

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
    val prefs = remember { context.getSharedPreferences("dj_mishki", 0) }
    val scope = rememberCoroutineScope()
    val library = remember { mutableStateListOf<Track>() }
    val queue = remember { mutableStateListOf<Track>() }
    val effects = remember { mutableStateListOf<Track>() }
    var section by remember { mutableStateOf("Пульт") }
    var onlineQuery by remember { mutableStateOf("") }
    var onlineResults by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Track?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var master by remember { mutableFloatStateOf(0.88f) }
    var fadeSeconds by remember { mutableIntStateOf(5) }
    var repeatQueue by remember { mutableStateOf(false) }
    var shuffleQueue by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Добавь музыку и сформируй очередь") }
    var tick by remember { mutableIntStateOf(0) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    var advanceAction by remember { mutableStateOf<(Boolean) -> Unit>({}) }

    suspend fun searchOnline(query: String): List<OnlineTrack> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val found = mutableListOf<OnlineTrack>()

        // Audius: open catalog with direct streaming endpoint.
        runCatching {
            val url = URL("https://discoveryprovider.audius.co/v1/tracks/search?query=$encoded&limit=20")
            val connection = url.openConnection()
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONArray("data")
            if (data != null) for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val user = item.optJSONObject("user")
                val artwork = item.optJSONObject("artwork")
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isNotBlank() && title.isNotBlank()) {
                    found.add(OnlineTrack(
                        "audius:$id", title, user?.optString("name")?.takeIf { it.isNotBlank() } ?: "Audius",
                        "Audius", "https://discoveryprovider.audius.co/v1/tracks/$id/stream?app_name=DJ_MISHKI",
                        "https://discoveryprovider.audius.co/v1/tracks/$id/download?app_name=DJ_MISHKI",
                        artwork?.optString("150x150")
                    ))
                }
            }
        }

        // Internet Archive: search openly available audio items and resolve actual audio files.
        runCatching {
            val iaQuery = URLEncoder.encode("mediatype:audio AND ($query)", "UTF-8")
            val searchUrl = URL("https://archive.org/advancedsearch.php?q=$iaQuery&fl[]=identifier,title,creator&rows=12&page=1&output=json")
            val connection = searchUrl.openConnection()
            connection.connectTimeout = 12000
            connection.readTimeout = 18000
            val body = connection.getInputStream().bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONObject("response")?.optJSONArray("docs")
            if (docs != null) for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val identifier = doc.optString("identifier")
                if (identifier.isBlank()) continue
                runCatching {
                    val metaConn = URL("https://archive.org/metadata/$identifier").openConnection()
                    metaConn.connectTimeout = 10000
                    metaConn.readTimeout = 12000
                    val metaBody = metaConn.getInputStream().bufferedReader().use { it.readText() }
                    val files = JSONObject(metaBody).optJSONArray("files") ?: return@runCatching
                    var audioFile: String? = null
                    for (j in 0 until files.length()) {
                        val file = files.optJSONObject(j) ?: continue
                        val name = file.optString("name")
                        val format = file.optString("format").lowercase()
                        if (file.optString("source") == "original" &&
                            (format.contains("mp3") || format.contains("mpeg") || name.endsWith(".mp3", true) || name.endsWith(".ogg", true) || name.endsWith(".flac", true))) {
                            audioFile = name
                            break
                        }
                    }
                    val fileName = audioFile ?: return@runCatching
                    val encodedFile = fileName.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
                    val title = doc.optString("title").takeIf { it.isNotBlank() } ?: identifier
                    val creator = doc.optString("creator").takeIf { it.isNotBlank() } ?: "Internet Archive"
                    val direct = "https://archive.org/download/$identifier/$encodedFile"
                    found.add(OnlineTrack("archive:$identifier", title, creator, "Internet Archive", direct, direct, null))
                }
            }
        }

        found.distinctBy { it.id }.take(35)
    }

    fun onlineAsTrack(track: OnlineTrack): Track = Track(
        "${track.title} — ${track.artist} [${track.provider}]",
        Uri.parse(track.streamUrl)
    )

    fun downloadOnlineTrack(track: OnlineTrack) {
        val downloadUrl = track.downloadUrl
        if (downloadUrl.isNullOrBlank()) {
            message = "Для этой записи скачивание не предоставлено источником."
            return
        }
        runCatching {
            val safeName = track.title.replace(Regex("[^a-zA-Zа-яА-Я0-9 _-]"), "").take(90).ifBlank { "track" }
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle(track.title)
                .setDescription("DJ MISHKI • ${track.artist} • ${track.provider}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "DJ MISHKI/$safeName.mp3")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            message = "Загрузка началась: ${track.title}"
        }.onFailure { message = "Источник не разрешил скачать эту запись." }
    }

    fun releaseCurrent() {
        runCatching { player?.setOnCompletionListener(null); player?.release() }
        player = null
        playing = false
        position = 0
        duration = 0
    }

    fun startTrack(track: Track, useFade: Boolean) {
        scope.launch {
            try {
                val incoming = MediaPlayer()
                incoming.setDataSource(context, track.uri)
                withContext(Dispatchers.IO) { incoming.prepare() }
                incoming.setVolume(if (useFade && player != null) 0f else master, if (useFade && player != null) 0f else master)
                val outgoing = player
                incoming.start()
                player = incoming
                current = track
                duration = incoming.duration
                position = 0
                playing = true
                if (useFade && outgoing != null) {
                    val steps = (fadeSeconds * 10).coerceAtLeast(10)
                    for (step in 0..steps) {
                        val fraction = step.toFloat() / steps
                        runCatching { incoming.setVolume(master * fraction, master * fraction) }
                        runCatching { outgoing.setVolume(master * (1f - fraction), master * (1f - fraction)) }
                        delay(100)
                    }
                    runCatching { outgoing.setOnCompletionListener(null); outgoing.release() }
                } else {
                    runCatching { outgoing?.setOnCompletionListener(null); outgoing?.release() }
                    incoming.setVolume(master, master)
                }
                incoming.setOnCompletionListener { scope.launch { advanceAction(true) } }
                message = "Сейчас играет: ${track.title}"
            } catch (_: Exception) {
                message = "Не удалось воспроизвести файл: ${track.title}"
                playing = false
            }
        }
    }

    advanceAction = { automatic ->
        if (queue.isEmpty() && repeatQueue && library.isNotEmpty()) {
            queue.addAll(library)
        }
        if (queue.isNotEmpty()) {
            val index = if (shuffleQueue) (0 until queue.size).random() else 0
            val next = queue.removeAt(index)
            startTrack(next, automatic || player != null)
        } else {
            playing = false
        }
    }

    fun previousTrack() {
        val active = player
        if (active != null && runCatching { active.currentPosition > 3000 }.getOrDefault(false)) {
            runCatching { active.seekTo(0) }
            position = 0
        } else if (queue.isNotEmpty()) {
            current?.let { queue.add(0, it) }
            val previous = queue.removeAt(queue.lastIndex)
            startTrack(previous, player != null)
        } else if (current != null) {
            startTrack(current!!, false)
        }
    }

    fun togglePlay() {
        if (playing) {
            runCatching { player?.pause() }
            playing = false
        } else if (player != null) {
            runCatching { player?.start() }
            playing = true
        } else {
            advanceAction(false)
        }
    }

    val musicPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Музыкальный файл"
            if (library.none { it.uri == uri }) library.add(Track(name, uri))
        }
        prefs.edit().putString("tracks", library.joinToString("|") { it.uri.toString() }).apply()
        message = "В библиотеке песен: ${library.size}"
    }
    val effectPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: "Звуковой эффект"
            effects.add(Track(name, uri))
        }
    }

    LaunchedEffect(Unit) {
        prefs.getString("tracks", null)?.split("|")?.filter { it.isNotBlank() }?.forEach { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "Музыкальный файл"
                library.add(Track(name, uri))
            }
        }
        while (true) {
            delay(350)
            tick++
            runCatching { if (playing) position = player?.currentPosition ?: 0 }
        }
    }
    DisposableEffect(Unit) {
        onDispose { releaseCurrent(); tone.release() }
    }

    val bg = Color(0xFF070E1B)
    val panel = Color(0xFF101D30)
    val pink = Color(0xFFFF24B8)
    val blue = Color(0xFF55B9FF)
    MaterialTheme(colorScheme = darkColorScheme(primary = pink, secondary = blue, background = bg, surface = panel)) {
        Scaffold(
            containerColor = bg,
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("DJ MISHKI", color = pink, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("Музыка для твоего праздника", color = Color.LightGray, fontSize = 11.sp)
                    } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = panel) {
                    listOf("Пульт", "Музыка", "Онлайн", "Эффекты", "Игры", "Настройки").forEach { label ->
                        NavigationBarItem(
                            selected = section == label,
                            onClick = { section = label },
                            icon = { Text(when (label) { "Пульт" -> "🎧"; "Музыка" -> "♫"; "Онлайн" -> "🌐"; "Эффекты" -> "〰"; "Игры" -> "🎮"; else -> "⚙" }) },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)) {
                when (section) {
                    "Пульт" -> {
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text("СЕЙЧАС ИГРАЕТ", color = pink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.height(5.dp))
                                Text(current?.title ?: "Выбери песню из очереди", fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(if (playing) "DJ MISHKI • Воспроизведение" else "DJ MISHKI • Пауза", color = Color.LightGray, fontSize = 12.sp)
                                Spacer(Modifier.height(12.dp))
                                Slider(
                                    value = if (duration > 0) position.toFloat().coerceIn(0f, duration.toFloat()) else 0f,
                                    onValueChange = { position = it.toInt() },
                                    onValueChangeFinished = { runCatching { player?.seekTo(position) } },
                                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                                    enabled = player != null && duration > 0
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(timeText(position), color = Color.LightGray, fontSize = 12.sp)
                                    Text(timeText(duration), color = Color.LightGray, fontSize = 12.sp)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                    FilledTonalButton(onClick = { previousTrack() }) { Text("⏮") }
                                    Button(onClick = { togglePlay() }, modifier = Modifier.height(58.dp)) {
                                        Text(if (playing) "Ⅱ  ПАУЗА" else "▶  ПЛЕЙ", fontWeight = FontWeight.Bold)
                                    }
                                    FilledTonalButton(onClick = { advanceAction(false) }, enabled = queue.isNotEmpty()) { Text("⏭") }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🔊", fontSize = 16.sp)
                                    Slider(value = master, onValueChange = {
                                        master = it
                                        runCatching { player?.setVolume(master, master) }
                                    }, modifier = Modifier.weight(1f))
                                    Text("${(master * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("ПЛАВНОЕ ПЕРЕКЛЮЧЕНИЕ", color = blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Затухание текущей и нарастание следующей песни", color = Color.LightGray, fontSize = 11.sp)
                                Spacer(Modifier.height(7.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(3, 5, 8, 10).forEach { seconds ->
                                        FilterChip(selected = fadeSeconds == seconds, onClick = { fadeSeconds = seconds }, label = { Text("$seconds сек.") })
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Очередь воспроизведения (${queue.size})", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { shuffleQueue = !shuffleQueue }) { Text(if (shuffleQueue) "🔀 Вкл." else "🔀 Перемешать") }
                            TextButton(onClick = { repeatQueue = !repeatQueue }) { Text(if (repeatQueue) "🔁 Вкл." else "🔁 Повтор") }
                        }
                        if (queue.isEmpty()) {
                            Text("Очередь пока пуста. Добавь песни во вкладке «Музыка».", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                itemsIndexed(queue) { index, track ->
                                    Card(colors = CardDefaults.cardColors(containerColor = if (index == 0) Color(0xFF321638) else panel), shape = RoundedCornerShape(12.dp)) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${index + 1}", color = pink, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.width(10.dp))
                                            Text(track.title, Modifier.weight(1f), maxLines = 2, fontSize = 13.sp)
                                            TextButton(onClick = { queue.removeAt(index) }) { Text("✕") }
                                        }
                                    }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { section = "Музыка" }, modifier = Modifier.weight(1f)) { Text("＋ Добавить песни") }
                            OutlinedButton(onClick = { queue.clear(); message = "Очередь очищена" }, modifier = Modifier.weight(1f)) { Text("Очистить") }
                        }
                        Text(message, color = Color.LightGray, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    "Онлайн" -> {
                        OutlinedTextField(
                            value = onlineQuery,
                            onValueChange = { onlineQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Песня или исполнитель — ищем в Audius и Internet Archive") },
                            singleLine = true
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                if (onlineQuery.isNotBlank()) scope.launch {
                                    onlineLoading = true
                                    onlineError = ""
                                    try {
                                        onlineResults = searchOnline(onlineQuery)
                                        if (onlineResults.isEmpty()) onlineError = "Ничего не найдено в открытых каталогах. Попробуй другое написание или имя исполнителя."
                                    } catch (_: Exception) {
                                        onlineError = "Не удалось выполнить поиск. Проверь интернет."
                                    } finally { onlineLoading = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !onlineLoading
                        ) { Text(if (onlineLoading) "Ищем..." else "⌕ Найти музыку") }
                        if (onlineError.isNotBlank()) Text(onlineError, color = Color.LightGray, modifier = Modifier.padding(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(onlineResults) { _, track ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(track.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                        Text("${track.artist} • ${track.provider}", color = Color.LightGray, fontSize = 12.sp)
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            TextButton(onClick = { startTrack(onlineAsTrack(track), player != null) }) { Text("▶ Слушать") }
                                            TextButton(onClick = { queue.add(onlineAsTrack(track)); message = "Добавлено в очередь" }) { Text("＋ В очередь") }
                                            TextButton(onClick = { downloadOnlineTrack(track) }) { Text("⬇ Скачать") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "Музыка" -> {
                        Button(onClick = { musicPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить песни с телефона") }
                        Spacer(Modifier.height(8.dp))
                        Text("Музыкальная библиотека • ${library.size} песен", color = blue, fontWeight = FontWeight.Bold)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(library) { index, track ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(track.title, Modifier.weight(1f), maxLines = 2, fontSize = 13.sp)
                                        TextButton(onClick = { queue.add(track); message = "Добавлено в очередь: ${track.title}"; section = "Пульт" }) { Text("В очередь") }
                                        TextButton(onClick = {
                                            library.removeAt(index)
                                            prefs.edit().putString("tracks", library.joinToString("|") { it.uri.toString() }).apply()
                                            message = "Удалено из библиотеки"
                                        }) { Text("✕") }
                                    }
                                }
                            }
                        }
                    }
                    "Эффекты" -> {
                        Text("ЗВУКОВЫЕ ЭФФЕКТЫ", color = pink, fontWeight = FontWeight.Bold)
                        val built = listOf("Старт" to ToneGenerator.TONE_PROP_BEEP, "Внимание" to ToneGenerator.TONE_PROP_PROMPT, "Подтверждение" to ToneGenerator.TONE_PROP_ACK, "Сигнал" to ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, "Победа" to ToneGenerator.TONE_CDMA_ALERT_INCALL_LITE)
                        built.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (name, id) -> Button(onClick = { tone.startTone(id, 650) }, modifier = Modifier.weight(1f)) { Text(name) } }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        Button(onClick = { effectPicker.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) { Text("＋ Добавить свои эффекты") }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            itemsIndexed(effects) { index, effect ->
                                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                                    Row(Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(effect.title, Modifier.weight(1f))
                                        Button(onClick = {
                                            runCatching {
                                                MediaPlayer().apply {
                                                    setDataSource(context, effect.uri)
                                                    setOnCompletionListener { it.release() }
                                                    prepare()
                                                    start()
                                                }
                                            }.onFailure { message = "Не удалось воспроизвести эффект" }
                                        }) { Text("▶") }
                                        TextButton(onClick = { effects.removeAt(index) }) { Text("Убрать") }
                                    }
                                }
                            }
                        }
                    }
                    "Игры" -> {
                        var seconds by remember { mutableIntStateOf(15) }
                        var remaining by remember { mutableIntStateOf(15) }
                        var running by remember { mutableStateOf(false) }
                        LaunchedEffect(running, tick) {
                            if (running && tick % 3 == 0 && remaining > 0) remaining--
                            if (remaining == 0) running = false
                        }
                        Text("ИГРЫ И ИНТЕРАКТИВЫ", color = pink, fontWeight = FontWeight.Bold)
                        Card(colors = CardDefaults.cardColors(containerColor = panel), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ТАЙМЕР КОНКУРСА", color = blue, fontWeight = FontWeight.Bold)
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
                    else -> {
                        Text("НАСТРОЙКИ", color = pink, fontWeight = FontWeight.Bold)
                        Text("Плавное переключение: $fadeSeconds секунд", color = Color.LightGray)
                        Text("Повтор очереди: ${if (repeatQueue) "включён" else "выключен"}", color = Color.LightGray)
                        Text("Перемешивание: ${if (shuffleQueue) "включено" else "выключено"}", color = Color.LightGray)
                        OutlinedButton(onClick = { releaseCurrent(); queue.clear(); message = "Плеер очищен" }) { Text("Сбросить плеер") }
                    }
                }
            }
        }
    }
}
