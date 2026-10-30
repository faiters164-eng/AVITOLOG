package ru.avitolog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Listing(
    val title: String,
    val price: Int,
    val category: String,
    val description: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AvitologApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvitologApp() {
    val listings = remember {
        listOf(
            Listing("Смартфон Samsung Galaxy", 12000, "Электроника", "Хорошее состояние, комплект"),
            Listing("Ноутбук для работы", 25000, "Электроника", "Продаю срочно"),
            Listing("Детская коляска", 4500, "Детское", "Бережное использование"),
            Listing("Велосипед горный", 8000, "Спорт", "Есть небольшие следы эксплуатации"),
            Listing("Игровая приставка", 15000, "Электроника", "Цена ниже средней")
        )
    }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Все") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val categories = listOf("Все", "Электроника", "Детское", "Спорт")

    val filtered = listings.filter {
        (category == "Все" || it.category == category) &&
        (it.title.contains(query, true) || it.description.contains(query, true)) &&
        (!onlyFavorites || it.title in favorites)
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF1769AA),
            secondary = androidx.compose.ui.graphics.Color(0xFF16A085)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("АвитолоG", fontWeight = FontWeight.Bold)
                            Text("Умный помощник покупателя", fontSize = 12.sp)
                        }
                    },
                    actions = {
                        IconButton(onClick = { onlyFavorites = !onlyFavorites }) {
                            Icon(Icons.Default.Favorite, "Избранное")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; onlyFavorites = false },
                        icon = { Icon(Icons.Default.Search, null) },
                        label = { Text("Поиск") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; onlyFavorites = true },
                        icon = { Icon(Icons.Default.Favorite, null) },
                        label = { Text("Избранное") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Security, null) },
                        label = { Text("Проверка") }
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Находи выгоднее!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Ищи товары и сохраняй интересные предложения")
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Что ищем? Например, iPhone") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text("Категории", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (selectedTab == 2) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
                            Text("Защита от мошенников", fontWeight = FontWeight.Bold)
                            Text("Проверяй подозрительные признаки: слишком низкую цену, просьбы перейти по ссылкам и предоплату.")
                            Text("Проверка пока демонстрационная: не гарантирует безопасность сделки.")
                        }
                    }
                } else {
                    Text(
                        if (onlyFavorites) "Избранное" else "Найденные предложения",
                        fontWeight = FontWeight.Bold
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(filtered) { listing ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(listing.title, fontWeight = FontWeight.Bold)
                                        Text(listing.category, fontSize = 12.sp)
                                        Text(listing.description, fontSize = 13.sp)
                                        Text(
                                            "%,d ₽".format(listing.price),
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(onClick = {
                                        favorites = if (listing.title in favorites)
                                            favorites - listing.title
                                        else favorites + listing.title
                                    }) {
                                        Icon(
                                            if (listing.title in favorites) Icons.Default.Favorite
                                            else Icons.Default.FavoriteBorder,
                                            contentDescription = "В избранное"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        Text("Ничего не найдено. Измени запрос или категорию.")
                    }
                }
            }
        }
    }
}
