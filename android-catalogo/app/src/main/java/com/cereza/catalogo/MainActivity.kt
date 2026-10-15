package com.cereza.catalogo

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val RAW = "https://raw.githubusercontent.com/cloudemax18/Proyecto-cereza/main/v1%20web/importar/"
private val CATALOG_SOURCES = listOf("chery_s12.csv")

private data class Part(
    val code: String, val name: String, val qty: Int, val note: String,
    val node: String, val photos: Int, val vehicle: String, val category: String,
    val parentCode: String, val unit: String, val price: Double
) {
    val favoriteKey: String get() = "$vehicle::$code::$parentCode::$node"
}

private fun csvLine(s: String): List<String> {
    val values = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '"') {
            if (quoted && i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ }
            else quoted = !quoted
        } else if (c == ',' && !quoted) { values.add(field.toString()); field.clear() }
        else field.append(c)
        i++
    }
    values.add(field.toString())
    return values
}

private class PartsDb(context: android.content.Context) :
    SQLiteOpenHelper(context, "cereza_catalogo.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE parts(
            code TEXT NOT NULL, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0,
            note TEXT, node TEXT, photos INTEGER NOT NULL DEFAULT 0, vehicle TEXT NOT NULL,
            category TEXT NOT NULL, parentCode TEXT, unit TEXT, price REAL NOT NULL DEFAULT 0,
            PRIMARY KEY(code, vehicle, parentCode, node))""".trimIndent())
        db.execSQL("CREATE TABLE fav(partKey TEXT PRIMARY KEY)")
        db.execSQL("CREATE INDEX idx_parts_vehicle_category ON parts(vehicle, category)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS parts")
            db.execSQL("""CREATE TABLE parts(
                code TEXT NOT NULL, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0,
                note TEXT, node TEXT, photos INTEGER NOT NULL DEFAULT 0, vehicle TEXT NOT NULL,
                category TEXT NOT NULL, parentCode TEXT, unit TEXT, price REAL NOT NULL DEFAULT 0,
                PRIMARY KEY(code, vehicle, parentCode, node))""".trimIndent())
            db.execSQL("CREATE INDEX idx_parts_vehicle_category ON parts(vehicle, category)")
        }
    }

    fun all(): List<Part> {
        val result = mutableListOf<Part>()
        readableDatabase.rawQuery(
            "SELECT code,name,qty,note,node,photos,vehicle,category,parentCode,unit,price FROM parts ORDER BY category,name", null
        ).use { c ->
            while (c.moveToNext()) result.add(Part(
                c.getString(0), c.getString(1), c.getInt(2), c.getString(3) ?: "",
                c.getString(4) ?: "", c.getInt(5), c.getString(6), c.getString(7),
                c.getString(8) ?: "", c.getString(9) ?: "", c.getDouble(10)
            ))
        }
        return result
    }

    fun import(csv: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            csv.lineSequence().drop(1).filter { it.isNotBlank() }.forEach { line ->
                val f = csvLine(line)
                if (f.size >= 11 && f[0].isNotBlank() && f[1].isNotBlank()) {
                    val v = ContentValues().apply {
                        put("code", f[0].trim()); put("name", f[1].trim())
                        put("qty", f[2].toIntOrNull() ?: 0); put("note", f[3]); put("node", f[4])
                        put("photos", f[5].toIntOrNull() ?: 0)
                        put("vehicle", f[6].ifBlank { "Chery Arauca 1.3" })
                        put("category", f[7].ifBlank { "Otros" }); put("parentCode", f[8])
                        put("unit", f[9]); put("price", f[10].replace(",", ".").toDoubleOrNull() ?: 0.0)
                    }
                    db.insertWithOnConflict("parts", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun favorites(): Set<String> {
        val result = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT partKey FROM fav", null).use { c ->
            while (c.moveToNext()) result.add(c.getString(0))
        }
        return result
    }

    fun toggleFavorite(key: String) {
        val db = writableDatabase
        val exists = db.rawQuery("SELECT 1 FROM fav WHERE partKey=?", arrayOf(key)).use { it.moveToFirst() }
        if (exists) db.delete("fav", "partKey=?", arrayOf(key))
        else db.insert("fav", null, ContentValues().apply { put("partKey", key) })
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = PartsDb(this)
        setContent { Catalog(db) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Catalog(db: PartsDb) {
    var parts by remember { mutableStateOf(db.all()) }
    var favorites by remember { mutableStateOf(db.favorites()) }
    var search by remember { mutableStateOf("") }
    var selectedVehicle by remember { mutableStateOf("") }
    var fullScreenPhoto by remember { mutableStateOf<Part?>(null) }
    var selectedCategory by remember { mutableStateOf("Todas las categorías") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Part?>(null) }
    var message by remember { mutableStateOf("") }
    var drawerOpen by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("Español") }
    var languageMenu by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshToken) {
        if (parts.isEmpty() || refreshToken > 0) {
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    CATALOG_SOURCES.forEach { source -> db.import(URL(RAW + source).readText()) }
                }
                parts = db.all()
                message = "Catálogo actualizado"
            } catch (_: Exception) {
                message = if (parts.isEmpty()) "No se pudo cargar el catálogo. Revisa tu conexión e intenta actualizar."
                else "Sin conexión: se muestran los datos guardados en el dispositivo."
            } finally { loading = false }
        }
    }

    val vehicles = parts.map { it.vehicle }.distinct().sorted()
    val vehicleOptions = listOf("Selecciona un auto") + vehicles
    val activeVehicle = selectedVehicle.takeIf { it in vehicles } ?: ""
    val categories = parts.filter { activeVehicle.isNotBlank() && it.vehicle == activeVehicle }
        .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val categoryOptions = listOf("Todas las categorías") + categories
    if (selectedCategory !in categoryOptions) selectedCategory = "Todas las categorías"

    val shown = parts.filter { p ->
        (activeVehicle.isNotBlank() && p.vehicle == activeVehicle) &&
        (selectedCategory == "Todas las categorías" || p.category == selectedCategory) &&
        (!onlyFavorites || p.favoriteKey in favorites) &&
        (search.isBlank() || p.code.contains(search, true) || p.name.contains(search, true) ||
            p.category.contains(search, true) || p.node.contains(search, true) || p.parentCode.contains(search, true))
    }
    val red = Color(0xFFD83A45)
    val ink = Color(0xFF172334)
    val canvas = Color(0xFFF4F6F8)

    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerState.open() else drawerState.close() }
    val english = language == "English"
    MaterialTheme(colorScheme = lightColorScheme(primary = red, onPrimary = Color.White, primaryContainer = Color(0xFFFFE3E5), onPrimaryContainer = Color(0xFF6E1420), secondary = Color(0xFF355C7D), background = canvas, surface = Color.White, surfaceVariant = Color(0xFFF0F2F5), outline = Color(0xFFDCE1E8))) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Text(if (english) "CATALOG FILTERS" else "FILTROS DEL CATÁLOGO", fontWeight = FontWeight.Black, modifier = Modifier.padding(20.dp))
                    Text(if (english) "Vehicle" else "Vehículo", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                    vehicles.forEach { vehicle ->
                        NavigationDrawerItem(label = { Text(vehicle) }, selected = vehicle == activeVehicle,
                            onClick = { selectedVehicle = vehicle; selectedCategory = "Todas las categorías"; search = ""; selectedTab = 0; onlyFavorites = false; drawerOpen = false },
                            modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(if (english) "Language / Idioma" else "Idioma / Language", color = Color.Gray, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                    listOf("Español", "English").forEach { lang ->
                        NavigationDrawerItem(label = { Text(lang) }, selected = language == lang, onClick = { language = lang }, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            },
            gesturesEnabled = true
        ) {
        Scaffold(
            containerColor = canvas,
            bottomBar = {
                if (activeVehicle.isNotBlank() && detail == null) {
                    NavigationBar(
                        containerColor = Color.White,
                        contentColor = ink,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; onlyFavorites = false },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text(if (english) "Catalog" else "Catálogo", maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = red,
                                indicatorColor = red,
                                unselectedIconColor = Color(0xFF7B8492),
                                unselectedTextColor = Color(0xFF7B8492)
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; onlyFavorites = true },
                            icon = { Icon(Icons.Default.Favorite, contentDescription = null) },
                            label = { Text(if (english) "Favorites" else "Favoritos", maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = red,
                                indicatorColor = red,
                                unselectedIconColor = Color(0xFF7B8492),
                                unselectedTextColor = Color(0xFF7B8492)
                            )
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = { drawerOpen = true },
                            icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                            label = { Text(if (english) "Vehicle" else "Vehículo", maxLines = 1) },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = red,
                                indicatorColor = red,
                                unselectedIconColor = Color(0xFF7B8492),
                                unselectedTextColor = Color(0xFF7B8492)
                            )
                        )
                    }
                }
            },
            topBar = {
                TopAppBar(colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = ink, navigationIconContentColor = ink, actionIconContentColor = ink), title = {
                    Column {
                        Text("CEREZA", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("CATÁLOGO DE REPUESTOS", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.2.sp)
                    }
                }, navigationIcon = {
                    if (activeVehicle.isNotBlank() && detail == null) {
                        IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, contentDescription = if (english) "Open vehicle panel" else "Abrir panel de vehículos") }
                    }
                }, actions = {
                    Box {
                        IconButton(onClick = { languageMenu = true }) { Icon(Icons.Default.Translate, contentDescription = "Language / Idioma") }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            listOf("Español", "English").forEach { lang -> DropdownMenuItem(text = { Text(lang) }, onClick = { language = lang; languageMenu = false }) }
                        }
                    }
                    IconButton(onClick = { refreshToken++; loading = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo")
                    }
                })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(canvas).padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (detail != null) {
                    val p = detail!!
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { detail = null }) { Text(if (english) "← Back to catalog" else "← Volver al catálogo") }
                        PartPhoto(p.code, p.photos, p.name, ContentScale.Fit,
                            Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(28.dp)).background(Color.White).clickable { fullScreenPhoto = p })
                        Text(if (english) "Tap image to view full screen and zoom" else "Toca la imagen para verla a pantalla completa y hacer zoom", fontSize = 12.sp, color = Color.Gray)
                        Text(p.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ink)
                        Text(p.code, color = red, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill(p.vehicle); InfoPill(p.category)
                        }
                        HorizontalDivider(color = Color(0xFFE7EAF0))
                        DetailRow(if (english) "Quantity" else "Cantidad", p.qty.toString())
                        DetailRow(if (english) "Category" else "Categoría", p.category)
                        DetailRow(if (english) "Parent code" else "Código padre", p.parentCode.ifBlank { "—" })
                        DetailRow(if (english) "Node" else "Nodo", p.node.ifBlank { "—" })
                        if (p.unit.isNotBlank()) DetailRow(if (english) "Unit" else "Unidad", p.unit)
                        if (p.price > 0) DetailRow(if (english) "Recorded price" else "Precio registrado", String.format("%.2f", p.price))
                        if (p.note.isNotBlank()) DetailRow(if (english) "Note" else "Nota", p.note)
                        Button(onClick = { db.toggleFavorite(p.favoriteKey); favorites = db.favorites() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(if (p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (p.favoriteKey in favorites) (if (english) "Remove from favorites" else "Quitar de favoritos") else (if (english) "Save to favorites" else "Guardar en favoritos"))
                        }
                    }
                } else {
                    if (activeVehicle.isBlank()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(ink).padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (english) "Find the right part" else "Encuentra la pieza correcta", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(if (english) "${parts.size} references organized by vehicle and system" else "${parts.size} referencias organizadas por vehículo y sistema", fontSize = 12.sp, color = Color(0xFFD5DCE6))
                            Spacer(Modifier.height(4.dp))
                            Text(if (english) "Choose a vehicle to begin" else "Selecciona un auto para comenzar", color = Color.White, fontSize = 13.sp)
                            vehicles.forEach { vehicle ->
                                TextButton(onClick = { selectedVehicle = vehicle; selectedCategory = "Todas las categorías"; search = "" }) { Text(vehicle, color = Color.White) }
                            }
                        }
                    }
                    if (activeVehicle.isNotBlank()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF172B43)).padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(if (english) "PARTS FOR YOUR VEHICLE" else "REPUESTOS PARA TU VEHÍCULO",
                                    color = Color(0xFFB8C9DC), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Text(activeVehicle, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                                Text(if (english) "\${parts.count { it.vehicle == activeVehicle }} references available" else "\${parts.count { it.vehicle == activeVehicle }} referencias disponibles",
                                    color = Color(0xFFE0E8F1), fontSize = 12.sp)
                            }
                            Surface(shape = RoundedCornerShape(18.dp), color = Color(0xFF294663)) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = Color.White, modifier = Modifier.padding(14.dp).size(30.dp))
                            }
                        }
                    }
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(if (english) "Search part name, code or system" else "Buscar nombre, código o sistema") },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = red) }, singleLine = true, shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = Color(0xFFE1E5EB), focusedBorderColor = red))
                    if (activeVehicle.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (english) "Browse by system" else "Explorar por sistema", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                categoryOptions.forEach { category ->
                                    FilterChip(
                                        selected = selectedCategory == category,
                                        onClick = { selectedCategory = category },
                                        label = { Text(if (category == "Todas las categorías") { if (english) "All systems" else "Todos" } else category, maxLines = 1) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF203D5B),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !onlyFavorites, onClick = { onlyFavorites = false; selectedTab = 0 }, label = { Text(if (english) "All" else "Todos") })
                        FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = true; selectedTab = 1 }, label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp)); Text(if (english) "Favorites" else "Favoritos")
                            }
                        })
                        Spacer(Modifier.weight(1f))
                        Text(if (activeVehicle.isBlank()) (if (english) "Select a vehicle" else "Selecciona un auto") else "${shown.size} ${if (english) "parts" else "piezas"}", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        if (activeVehicle.isNotBlank()) {
                            IconButton(onClick = { listMode = false }) { Icon(Icons.Default.GridView, contentDescription = if (english) "Grid view" else "Vista de recuadros", tint = if (!listMode) red else Color.Gray) }
                            IconButton(onClick = { listMode = true }) { Icon(Icons.Default.ViewList, contentDescription = if (english) "List view" else "Vista de lista", tint = if (listMode) red else Color.Gray) }
                        }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(if (english) message.replace("Catálogo actualizado", "Catalog updated").replace("No se pudo cargar el catálogo. Revisa tu conexión e intenta actualizar.", "Could not load catalog. Check your connection and try again.").replace("Sin conexión: se muestran los datos guardados en el dispositivo.", "Offline: showing data saved on this device.") else message, fontSize = 11.sp, color = Color.Gray)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (activeVehicle.isBlank() || shown.isEmpty()) {
                            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Build, null, tint = Color.LightGray, modifier = Modifier.size(42.dp))
                                Text(if (activeVehicle.isBlank()) (if (english) "Select your vehicle" else "Selecciona tu auto") else (if (english) "No parts found" else "No encontramos piezas"), fontWeight = FontWeight.SemiBold, color = ink)
                                Text(if (activeVehicle.isBlank()) (if (english) "Choose a vehicle from the menu to see its parts." else "Elige un vehículo para ver únicamente sus repuestos.") else (if (english) "Try another name, code or category." else "Prueba otro nombre, código o categoría."), color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            if (listMode) {
                                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    lazyItems(shown, key = { it.favoriteKey }) { p ->
                                        Card(Modifier.fillMaxWidth().clickable { detail = p }, shape = RoundedCornerShape(20.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                PartPhoto(p.code, p.photos, p.name, ContentScale.Fit, Modifier.size(88.dp).clip(RoundedCornerShape(10.dp)))
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                                    Text(p.code, color = red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    Text(p.category, color = Color.Gray, fontSize = 11.sp)
                                                    Text(if (english) "Qty. ${p.qty}" else "Cant. ${p.qty}", fontSize = 11.sp, color = ink)
                                                }
                                                IconButton(onClick = { db.toggleFavorite(p.favoriteKey); favorites = db.favorites() }) {
                                                    Icon(if (p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (p.favoriteKey in favorites) red else Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shown, key = { it.favoriteKey }) { p ->
                                    Card(Modifier.fillMaxWidth().clickable { detail = p }, shape = RoundedCornerShape(22.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCFDFE)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                                        Box {
                                            PartPhoto(p.code, p.photos, p.name, ContentScale.Fit,
                                                Modifier.fillMaxWidth().height(138.dp).padding(8.dp).clip(RoundedCornerShape(16.dp)))
                                            if (p.price > 0) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),
                                                color = Color(0xFFEAF6EE), shape = RoundedCornerShape(8.dp)) {
                                                Text(String.format("%.2f", p.price), color = Color(0xFF267547), fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                                            }
                                        }
                                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(p.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, minLines = 2, maxLines = 2)
                                            Text(p.code, color = red, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(p.category, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Cant. ${p.qty}", fontSize = 10.sp, color = ink)
                                                Spacer(Modifier.weight(1f))
                                                IconButton(onClick = { db.toggleFavorite(p.favoriteKey); favorites = db.favorites() },
                                                    modifier = Modifier.size(30.dp)) {
                                                    Icon(if (p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                        if (p.favoriteKey in favorites) "Quitar favorito" else "Añadir favorito",
                                                        tint = if (p.favoriteKey in favorites) red else Color.Gray, modifier = Modifier.size(18.dp))
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
            }
        }
        }
    }
    if (fullScreenPhoto != null) {
        val photo = fullScreenPhoto!!
        var zoom by remember(photo.code) { mutableFloatStateOf(1f) }
        var pan by remember(photo.code) { mutableStateOf(Offset.Zero) }
        val zoomState = rememberTransformableState { zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(1f, 5f)
            pan = if (zoom > 1f) pan + panChange else Offset.Zero
        }
        val photoCandidates = remember(photo.code, photo.photos) {
            (0..maxOf(photo.photos, 4)).map { i -> RAW + "fotos/" + photo.code + (if (i == 0) "" else "_$i") + ".jpg" }
        }
        var photoCandidateIndex by remember(photo.code) { mutableIntStateOf(0) }
        Dialog(onDismissRequest = { fullScreenPhoto = null }) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = photoCandidates[photoCandidateIndex],
                    contentDescription = "Imagen ampliada de ${photo.name}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
                        .transformable(zoomState),
                    onError = { if (photoCandidateIndex < photoCandidates.lastIndex) photoCandidateIndex++ }
                )
                TextButton(onClick = { fullScreenPhoto = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Text("Cerrar", color = Color.White)
                }
                Text("Pellizca para ampliar · ${photo.name}", color = Color.White,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(15.dp), singleLine = true)
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@Composable
private fun InfoPill(text: String) {
    Surface(color = Color(0xFFFDECEF), shape = RoundedCornerShape(9.dp)) {
        Text(text, color = Color(0xFFB62936), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color.Gray, fontSize = 13.sp, modifier = Modifier.weight(0.8f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun PartPhoto(code: String, count: Int, contentDescription: String, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val candidates = remember(code, count) {
        (0..maxOf(count, 4)).map { i -> RAW + "fotos/" + code + (if (i == 0) "" else "_$i") + ".jpg" }
    }
    var candidateIndex by remember(code, count) { mutableIntStateOf(0) }
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F8FA)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Build, null, tint = Color(0xFFD7DDE5), modifier = Modifier.size(36.dp))
        AsyncImage(model = candidates[candidateIndex], contentDescription = contentDescription,
            contentScale = contentScale, modifier = Modifier.fillMaxSize(),
            onError = { if (candidateIndex < candidates.lastIndex) candidateIndex++ })
    }
}
