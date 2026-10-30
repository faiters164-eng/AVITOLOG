package ru.avitolog.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AvitologApp(
                openExternal = { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            )
        }
    }
}

private fun regionSlug(value: String): String {
    val map = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
        'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
        'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
        'э' to "e", 'ю' to "yu", 'я' to "ya"
    )
    return value.trim().lowercase()
        .map { map[it] ?: if (it.isLetterOrDigit()) it.toString() else "-" }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvitologApp(openExternal: (String) -> Unit) {
    val context = LocalContext.current
    val preferences = remember {
        context.getSharedPreferences("avitolog", android.content.Context.MODE_PRIVATE)
    }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("По умолчанию") }
    var url by remember { mutableStateOf("https://www.avito.ru/") }
    var searched by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val favorites = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("favorites", emptySet()) ?: emptySet())
        }
    }

    fun makeSearch() {
        val searchText = query.trim().ifBlank { "товары" }
        val location = regionSlug(region)
        val base = if (location.isBlank()) "https://www.avito.ru/all" else "https://www.avito.ru/$location"
        val params = StringBuilder(base + "?q=" + URLEncoder.encode(searchText, "UTF-8"))
        if (minPrice.isNotBlank()) params.append("&pmin=").append(minPrice)
        if (maxPrice.isNotBlank()) params.append("&pmax=").append(maxPrice)
        when (sort) {
            "Дешевле" -> params.append("&s=1")
            "Дороже" -> params.append("&s=2")
        }
        url = params.toString()
        searched = true
        webView?.loadUrl(url)
    }

    fun saveCurrentSearch() {
        if (!searched) return
        val entry = (query.ifBlank { "Все товары" } + " • " +
            region.ifBlank { "Весь Avito" } + " • " + url)
        if (!favorites.contains(entry)) {
            favorites.add(0, entry)
            while (favorites.size > 12) favorites.removeAt(favorites.lastIndex)
            preferences.edit().putStringSet("favorites", favorites.toSet()).apply()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF7652C8))
    ) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("АвитолоG")
                        Text("Умный поиск выгодных объявлений", fontSize = 12.sp)
                    }
                })
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Что ищем?") },
                    placeholder = { Text("Например, iPhone 15") }, singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = region, onValueChange = { region = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Город или регион") },
                    placeholder = { Text("Например, Самара") }, singleLine = true
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minPrice, onValueChange = { minPrice = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f), label = { Text("Цена от") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = maxPrice, onValueChange = { maxPrice = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f), label = { Text("Цена до") }, singleLine = true
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Сортировка", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("По умолчанию", "Дешевле", "Дороже").forEach {
                        FilterChip(
                            selected = sort == it, onClick = { sort = it },
                            label = { Text(it, fontSize = 11.sp) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { makeSearch() }, modifier = Modifier.weight(1f)) {
                        Text("НАЙТИ", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = { showSafety = true }) {
                        Text("Анти-обман", fontSize = 12.sp)
                    }
                }

                if (favorites.isNotEmpty()) {
                    Text("Избранные поиски", style = MaterialTheme.typography.labelLarge)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favorites.forEach { item ->
                            val parts = item.split(" • ", limit = 3)
                            AssistChip(
                                onClick = {
                                    if (parts.size == 3) {
                                        query = parts[0]
                                        region = if (parts[1] == "Весь Avito") "" else parts[1]
                                        url = parts[2]
                                        searched = true
                                        webView?.loadUrl(url)
                                    }
                                },
                                label = { Text(parts.firstOrNull() ?: "Поиск", maxLines = 1) }
                            )
                        }
                    }
                }

                if (searched) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Результаты Avito", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { saveCurrentSearch() }) { Text("☆ В избранное") }
                    }
                    AndroidView(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        factory = { ctx ->
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
                        },
                        update = { webView = it }
                    )
                    TextButton(onClick = { openExternal(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть выдачу в Avito")
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Ищи выгодные предложения", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "АвитолоG показывает настоящую выдачу Avito с фотографиями и ценами. " +
                                    "Укажи запрос, регион и диапазон стоимости."
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Сохраняй нужные поиски в избранное и возвращайся к ним одним нажатием."
                            )
                        }
                    }
                }
            }
        }

        if (showSafety) {
            AlertDialog(
                onDismissRequest = { showSafety = false },
                title = { Text("Защита от мошенников") },
                text = {
                    Column {
                        Text("Перед покупкой проверь:")
                        Spacer(Modifier.height(8.dp))
                        listOf(
                            "Не переходи по ссылкам на оплату и доставку из чатов.",
                            "Не сообщай коды из SMS, данные карты и пароли.",
                            "Не переводи предоплату незнакомому продавцу.",
                            "Проверяй профиль, отзывы и дату регистрации.",
                            "Осматривай товар и проверяй его до подтверждения получения."
                        ).forEach { tip ->
                            Text("• $tip")
                            Spacer(Modifier.height(5.dp))
                        }
                        Text(
                            "АвитолоG не может гарантировать надёжность продавца: проверяй сделку самостоятельно.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSafety = false }) { Text("Понятно") }
                }
            )
        }
    }
}
