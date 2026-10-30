package ru.avitolog.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.net.URLEncoder

data class Listing(
    val title: String,
    val price: Int,
    val category: String,
    val description: String,
    val image: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AvitologApp { query ->
                val q = URLEncoder.encode(query.ifBlank { "товары" }, "UTF-8")
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.avito.ru/all?q=$q")))
            }
        }
        selectedProduct?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedProduct = null },
                title = { Text(item.title) },
                text = {
                    Column {
                        AsyncImage(
                            model = item.image,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text("%,d ₽".format(item.price), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(item.description)
                        Text("Демонстрационный товар, не реальное объявление Avito.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSearch(item.title); selectedProduct = null }) {
                        Text("Найти на Avito")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedProduct = null }) {
                        Text("Закрыть")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvitologApp(onSearch: (String) -> Unit) {
    val products = listOf(
        Listing("Samsung Galaxy", 12000, "Электроника", "Хорошее состояние",
            "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=500"),
        Listing("Ноутбук", 25000, "Электроника", "Для работы и учёбы",
            "https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=500"),
        Listing("Детская коляска", 4500, "Детское", "Бережное использование",
            "https://images.unsplash.com/photo-1591348278863-a8fb3887e2aa?w=500"),
        Listing("Велосипед", 8000, "Спорт", "Хорошее состояние",
            "https://images.unsplash.com/photo-1485965120184-e220f721d03e?w=500")
    )

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var tab by remember { mutableIntStateOf(0) }
    var selectedProduct by remember { mutableStateOf<Listing?>(null) }

    val filtered = products.filter {
        (category == "Все" || it.category == category) &&
        (it.title.contains(query, true) || it.description.contains(query, true)) &&
        (tab != 1 || it.title in favorites)
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF7652C8))) {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("АвитолоG", fontWeight = FontWeight.Bold)
                        Text("Умный помощник покупателя", fontSize = 12.sp)
                    }
                })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Search, null) }, label = { Text("Поиск") })
                    NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Favorite, null) }, label = { Text("Избранное") })
                    NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Security, null) }, label = { Text("Проверка") })
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(14.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Находи выгоднее!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Сравнивай товары и сохраняй интересные предложения")
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Что ищем? Например, iPhone") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSearch(query) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Искать реальные объявления на Avito")
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Все", "Электроника", "Детское", "Спорт").forEach {
                        FilterChip(category == it, { category = it }, label = { Text(it, fontSize = 11.sp) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                if (tab == 2) {
                    Text("Защита от мошенников", fontWeight = FontWeight.Bold)
                    Text("Осторожно: подозрительно низкая цена, просьбы о предоплате и сторонние ссылки. Эта памятка не гарантирует безопасность сделки.")
                } else {
                    Text(if (tab == 1) "Избранное" else "Примеры товаров", fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        items(filtered) { item ->
                            Card(Modifier.fillMaxWidth().clickable { selectedProduct = item }) {
                                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(
                                        model = item.image,
                                        contentDescription = item.title,
                                        modifier = Modifier.size(105.dp, 100.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, fontWeight = FontWeight.Bold)
                                        Text(item.description, fontSize = 12.sp)
                                        Text("%,d ₽".format(item.price), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = {
                                        favorites = if (item.title in favorites) favorites - item.title else favorites + item.title
                                    }) {
                                        Icon(if (item.title in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
