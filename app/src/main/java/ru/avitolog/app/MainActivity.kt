package ru.avitolog.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class PricePoint(val price: Long, val at: Long)
private data class SavedListing(val id: String, val title: String, val url: String, val currentPrice: Long, val history: List<PricePoint>)

class MainActivity : ComponentActivity() {
    private var incomingShare by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingShare = readSharedText(intent)
        showApp()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShare = readSharedText(intent)
    }
    private fun readSharedText(intent: Intent?): String? {
        if (intent == null) return null
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return null
        return Regex("https?://(?:www\\.)?avito\\.ru/[^\\s]+", RegexOption.IGNORE_CASE)
            .find(raw)?.value?.trimEnd('.', ',', ')', ']')
    }
    private fun showApp() {
        setContent {
            AvitologApp(
                sharedUrl = incomingShare,
                onShareConsumed = { incomingShare = null },
                openExternal = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
            )
        }
    }
}

private fun regionSlug(value: String): String {
    val map = mapOf('а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya")
    return value.trim().lowercase().map { map[it] ?: if (it.isLetterOrDigit()) it.toString() else "-" }.joinToString("").replace(Regex("-+"), "-").trim('-')
}
private fun readListings(raw: String?): List<SavedListing> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val h = o.optJSONArray("history") ?: JSONArray()
            val points = (0 until h.length()).mapNotNull { j ->
                val p = h.optJSONObject(j) ?: return@mapNotNull null
                PricePoint(p.optLong("price"), p.optLong("at"))
            }
            SavedListing(o.optString("id"), o.optString("title"), o.optString("url"), o.optLong("currentPrice"), points)
        }
    } catch (_: Exception) { emptyList() }
}
private fun writeListings(list: List<SavedListing>): String {
    val a = JSONArray()
    list.forEach { item ->
        val h = JSONArray()
        item.history.forEach { h.put(JSONObject().put("price", it.price).put("at", it.at)) }
        a.put(JSONObject().put("id", item.id).put("title", item.title).put("url", item.url).put("currentPrice", item.currentPrice).put("history", h))
    }
    return a.toString()
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvitologApp(sharedUrl: String?, onShareConsumed: () -> Unit, openExternal: (String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("avitolog", android.content.Context.MODE_PRIVATE) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("По умолчанию") }
    var url by remember { mutableStateOf("https://www.avito.ru/") }
    var searched by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var tab by remember { mutableStateOf("Поиск") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var priceInput by remember { mutableStateOf("") }
    var estimatePrice by remember { mutableStateOf("") }
    var marketPrice by remember { mutableStateOf("") }
    val favorites = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("favorites", emptySet()) ?: emptySet()) } }
    val listings = remember { mutableStateListOf<SavedListing>().apply { addAll(readListings(prefs.getString("listings", null))) } }
    fun persist() { prefs.edit().putString("listings", writeListings(listings)).apply() }

    LaunchedEffect(sharedUrl) {
        val incoming = sharedUrl ?: return@LaunchedEffect
        if (listings.none { it.url == incoming }) {
            val label = incoming.substringAfterLast('/').substringBefore('?').replace('-', ' ').take(65).ifBlank { "Объявление Avito" }
            listings.add(0, SavedListing(System.currentTimeMillis().toString(), label, incoming, 0L, emptyList()))
            persist()
        }
        tab = "Находки"
        onShareConsumed()
    }
    fun makeSearch() {
        val location = regionSlug(region)
        val base = if (location.isBlank()) "https://www.avito.ru/all" else "https://www.avito.ru/$location"
        val p = StringBuilder(base + "?q=" + URLEncoder.encode(query.trim().ifBlank { "товары" }, "UTF-8"))
        if (minPrice.isNotBlank()) p.append("&pmin=").append(minPrice)
        if (maxPrice.isNotBlank()) p.append("&pmax=").append(maxPrice)
        when (sort) { "Дешевле" -> p.append("&s=1"); "Дороже" -> p.append("&s=2") }
        url = p.toString()
        searched = true
        webView?.loadUrl(url)
    }
    fun saveSearch() {
        if (!searched) return
        val entry = query.ifBlank { "Все товары" } + " • " + region.ifBlank { "Весь Avito" } + " • " + url
        if (!favorites.contains(entry)) {
            favorites.add(0, entry)
            while (favorites.size > 12) favorites.removeAt(favorites.lastIndex)
            prefs.edit().putStringSet("favorites", favorites.toSet()).apply()
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF7652C8))) {
        Scaffold(topBar = { TopAppBar(title = { Column { Text("АвитолоG"); Text("Охота за выгодой на Avito", fontSize = 12.sp) } }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val tabs = listOf("Поиск", "Находки", "Оценка")
                ScrollableTabRow(selectedTabIndex = tabs.indexOf(tab)) {
                    tabs.forEach { label -> Tab(selected = tab == label, onClick = { tab = label }, text = { Text(label) }) }
                }
                when (tab) {
                    "Поиск" -> Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Что ищем?") }, placeholder = { Text("Например, iPhone 15") }, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(region, { region = it }, Modifier.fillMaxWidth(), label = { Text("Город или регион") }, placeholder = { Text("Например, Самара") }, singleLine = true)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(minPrice, { minPrice = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Цена от") }, singleLine = true)
                            OutlinedTextField(maxPrice, { maxPrice = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Цена до") }, singleLine = true)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Сортировка", fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            listOf("По умолчанию", "Дешевле", "Дороже").forEach { FilterChip(sort == it, { sort = it }, label = { Text(it, fontSize = 11.sp) }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { makeSearch() }, modifier = Modifier.weight(1f)) { Text("НАЙТИ", fontSize = 13.sp) }
                            OutlinedButton(onClick = { showSafety = true }) { Text("Анти-обман", fontSize = 12.sp) }
                        }
                        if (favorites.isNotEmpty()) {
                            Text("Избранные поиски", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                favorites.forEach { item ->
                                    val parts = item.split(" • ", limit = 3)
                                    AssistChip(onClick = {
                                        if (parts.size == 3) { query = parts[0]; region = if (parts[1] == "Весь Avito") "" else parts[1]; url = parts[2]; searched = true; webView?.loadUrl(url) }
                                    }, label = { Text(parts.firstOrNull() ?: "Поиск", maxLines = 1) })
                                }
                            }
                        }
                        if (searched) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Результаты Avito", style = MaterialTheme.typography.titleMedium)
                                TextButton(onClick = { saveSearch() }) { Text("☆ В избранное") }
                            }
                            AndroidView(factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = false
                                    settings.loadWithOverviewMode = false
                                    settings.textZoom = 110
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = WebViewClient()
                                    loadUrl(url)
                                    webView = this
                                }
                            }, modifier = Modifier.weight(1f).fillMaxWidth(), update = { view -> webView = view })
                            TextButton(onClick = { openExternal(url) }, modifier = Modifier.fillMaxWidth()) { Text("Открыть выдачу в Avito") }
                        } else {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("Ищи выгодные предложения", style = MaterialTheme.typography.titleLarge)
                                    Text("Настоящие объявления Avito, фотографии и цены загружаются внутри приложения.")
                                    Spacer(Modifier.height(6.dp))
                                    Text("Чтобы добавить объявление в находки, нажми «Поделиться» в Avito и выбери АвитолоG.")
                                }
                            }
                        }
                    }
                    "Находки" -> {
                        if (listings.isEmpty()) {
                            Column(Modifier.fillMaxSize().padding(20.dp)) {
                                Text("Пока нет сохранённых объявлений", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text("Открой объявление в Avito → «Поделиться» → «АвитолоG». Оно появится здесь.")
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(listings, key = { it.id }) { item ->
                                    Card(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(14.dp)) {
                                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                                            Text("Текущая записанная цена: " + if (item.currentPrice > 0) "%,d ₽".format(item.currentPrice) else "не указана")
                                            if (item.history.size >= 2) {
                                                val first = item.history.first().price
                                                val last = item.history.last().price
                                                val change = if (first > 0) (last - first) * 100.0 / first else 0.0
                                                Text("Изменение с первого замера: " + "%.1f".format(change) + "%")
                                            }
                                            Text("Замеров цены: " + item.history.size, style = MaterialTheme.typography.bodySmall)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(onClick = { editingId = item.id; priceInput = if (item.currentPrice > 0) item.currentPrice.toString() else "" }) { Text("Записать цену") }
                                                OutlinedButton(onClick = { openExternal(item.url) }) { Text("Открыть") }
                                            }
                                            if (item.history.isNotEmpty()) {
                                                val date = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
                                                Text("История: " + item.history.takeLast(5).joinToString(" · ") { "%,d ₽ (%s)".format(it.price, date.format(Date(it.at))) }, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        Text("Оценка покупки", style = MaterialTheme.typography.headlineSmall)
                        Text("Сравни цену объявления со средней ценой похожего товара. Введи значения вручную.")
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(estimatePrice, { estimatePrice = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Цена объявления, ₽") })
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(marketPrice, { marketPrice = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Обычная цена похожего товара, ₽") })
                        val asking = estimatePrice.toLongOrNull() ?: 0L
                        val market = marketPrice.toLongOrNull() ?: 0L
                        if (asking > 0 && market > 0) {
                            val diff = (market - asking) * 100.0 / market
                            Spacer(Modifier.height(12.dp))
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(when { diff >= 20 -> "Выглядит выгодно"; diff >= 5 -> "Цена ниже ориентира"; diff > -10 -> "Примерно рыночная цена"; else -> "Цена выше ориентира" }, style = MaterialTheme.typography.titleLarge)
                                    Text("Разница с ориентиром: " + "%.1f".format(diff) + "%")
                                    Text("Предварительное сравнение, не гарантия качества или честности продавца.")
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Охота за выгодой", style = MaterialTheme.typography.titleMedium)
                        Text("Сравнивай похожие объявления, состояние, комплектацию, доставку и репутацию продавца. Слишком низкая цена может быть признаком риска.")
                    }
                }
            }
        }
        if (showSafety) {
            AlertDialog(onDismissRequest = { showSafety = false }, title = { Text("Защита от мошенников") },
                text = { Column {
                    listOf("Не переходи по ссылкам на оплату и доставку из чатов.", "Не сообщай коды из SMS, данные карты и пароли.", "Не переводи предоплату незнакомому продавцу.", "Проверяй профиль, отзывы и дату регистрации.", "Осматривай товар и проверяй его до подтверждения получения.").forEach { Text("• " + it); Spacer(Modifier.height(5.dp)) }
                    Text("АвитолоG не может гарантировать надёжность продавца.", style = MaterialTheme.typography.bodySmall)
                } },
                confirmButton = { TextButton(onClick = { showSafety = false }) { Text("Понятно") } }
            )
        }
        if (editingId != null) {
            AlertDialog(onDismissRequest = { editingId = null }, title = { Text("Записать цену") },
                text = { OutlinedTextField(priceInput, { priceInput = it.filter(Char::isDigit) }, label = { Text("Цена сейчас, ₽") }, singleLine = true) },
                confirmButton = { TextButton(onClick = {
                    val price = priceInput.toLongOrNull()
                    val index = listings.indexOfFirst { it.id == editingId }
                    if (price != null && price > 0 && index >= 0) {
                        val old = listings[index]
                        listings[index] = old.copy(currentPrice = price, history = old.history + PricePoint(price, System.currentTimeMillis()))
                        persist()
                    }
                    editingId = null
                }) { Text("Сохранить") } },
                dismissButton = { TextButton(onClick = { editingId = null }) { Text("Отмена") } }
            )
        }
    }
}
