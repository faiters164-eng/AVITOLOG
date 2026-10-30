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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
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

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvitologApp(openExternal: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("По умолчанию") }
    var url by remember { mutableStateOf("https://www.avito.ru/") }
    var searched by remember { mutableStateOf(false) }

    var webView by remember { mutableStateOf<WebView?>(null) }

    fun makeSearch() {
        val searchText = listOf(query.trim(), region.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "товары" }

        val params = StringBuilder(
            "https://www.avito.ru/all?q=" +
                URLEncoder.encode(searchText, "UTF-8")
        )

        if (minPrice.isNotBlank()) {
            params.append("&pmin=").append(URLEncoder.encode(minPrice, "UTF-8"))
        }
        if (maxPrice.isNotBlank()) {
            params.append("&pmax=").append(URLEncoder.encode(maxPrice, "UTF-8"))
        }

        when (sort) {
            "Дешевле" -> params.append("&s=1")
            "Дороже" -> params.append("&s=2")
        }

        url = params.toString()
        searched = true
        webView?.loadUrl(url)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF7652C8)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("АвитолоG")
                            Text(
                                "Поиск выгодных объявлений",
                                fontSize = 12.sp
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Что ищем?") },
                    placeholder = { Text("Например, iPhone 15") },
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Город или регион") },
                    placeholder = { Text("Например, Самара") },
                    singleLine = true
                )

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minPrice,
                        onValueChange = { minPrice = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Цена от") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = it.filter(Char::isDigit) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Цена до") },
                        singleLine = true
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Сортировка")

                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("По умолчанию", "Дешевле", "Дороже").forEach {
                        FilterChip(
                            selected = sort == it,
                            onClick = { sort = it },
                            label = { Text(it, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { makeSearch() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("НАЙТИ ОБЪЯВЛЕНИЯ НА AVITO")
                }

                Spacer(Modifier.height(8.dp))

                if (searched) {
                    Text(
                        "Настоящая выдача Avito",
                        style = MaterialTheme.typography.titleMedium
                    )
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = WebViewClient()
                                loadUrl(url)
                                webView = this
                            }
                        },
                        update = {
                            webView = it
                        }
                    )

                    TextButton(
                        onClick = { openExternal(url) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Открыть эту выдачу в приложении или браузере Avito")
                    }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "Ищи выгодные предложения",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                "Настоящие объявления, фотографии и цены " +
                                    "загружаются с сайта Avito."
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Поиск работает бесплатно. Регион передаётся " +
                                    "в поисковый запрос; точность регионального " +
                                    "фильтра зависит от возможностей Avito."
                            )
                        }
                    }
                }
            }
        }
    }
}
