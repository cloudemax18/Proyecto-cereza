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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {\n        if (oldVersion < 2) {\n            db.execSQL("DROP TABLE IF EXISTS parts")\n            db.execSQL("""CREATE TABLE parts(\n                code TEXT NOT NULL, name TEXT NOT NULL, qty INTEGER NOT NULL DEFAULT 0,\n                note TEXT, node TEXT, photos INTEGER NOT NULL DEFAULT 0, vehicle TEXT NOT NULL,\n                category TEXT NOT NULL, parentCode TEXT, unit TEXT, price REAL NOT NULL DEFAULT 0,\n                PRIMARY KEY(code, vehicle, parentCode, node))""".trimIndent())\n            db.execSQL("CREATE INDEX idx_parts_vehicle_category ON parts(vehicle, category)")\n        }\n    }

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
    var selectedVehicle by remember { mutableStateOf("Chery S12 (SQR7160) 1.3L — Arauca 1.3") }
    var selectedCategory by remember { mutableStateOf("Todas las categorías") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Part?>(null) }
    var message by remember { mutableStateOf("") }

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
    val vehicleOptions = listOf("Todos los autos") + vehicles
    val activeVehicle = if (selectedVehicle == "Todos los autos" || selectedVehicle in vehicles) selectedVehicle
        else vehicles.firstOrNull { it.contains("Arauca 1.3", true) } ?: vehicles.firstOrNull() ?: "Chery Arauca 1.3"
    val categories = parts.filter { activeVehicle == "Todos los autos" || it.vehicle == activeVehicle }
        .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val categoryOptions = listOf("Todas las categorías") + categories
    if (selectedCategory !in categoryOptions) selectedCategory = "Todas las categorías"

    val shown = parts.filter { p ->
        (activeVehicle == "Todos los autos" || p.vehicle == activeVehicle) &&
        (selectedCategory == "Todas las categorías" || p.category == selectedCategory) &&
        (!onlyFavorites || p.favoriteKey in favorites) &&
        (search.isBlank() || p.code.contains(search, true) || p.name.contains(search, true) ||
            p.category.contains(search, true) || p.node.contains(search, true) || p.parentCode.contains(search, true))
    }
    val red = Color(0xFFD83A45)
    val ink = Color(0xFF172334)
    val canvas = Color(0xFFF4F6F8)

    MaterialTheme(colorScheme = lightColorScheme(primary = red, background = canvas, surface = Color.White)) {
        Scaffold(
            containerColor = canvas,
            topBar = {
                TopAppBar(title = {
                    Column {
                        Text("CEREZA", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                        Text("CATÁLOGO DE REPUESTOS", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.2.sp)
                    }
                }, actions = {
                    IconButton(onClick = { refreshToken++; loading = true }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo")
                    }
                })
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (detail != null) {
                    val p = detail!!
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { detail = null }) { Text("← Volver al catálogo") }
                        PartPhoto(p.code, p.photos, p.name, ContentScale.Fit,
                            Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(24.dp)).background(Color.White))
                        Text(p.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = ink)
                        Text(p.code, color = red, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoPill(p.vehicle); InfoPill(p.category)
                        }
                        HorizontalDivider(color = Color(0xFFE7EAF0))
                        DetailRow("Cantidad", p.qty.toString())
                        DetailRow("Categoría", p.category)
                        DetailRow("Código padre", p.parentCode.ifBlank { "—" })
                        DetailRow("Nodo", p.node.ifBlank { "—" })
                        if (p.unit.isNotBlank()) DetailRow("Unidad", p.unit)
                        if (p.price > 0) DetailRow("Precio registrado", String.format("%.2f", p.price))
                        if (p.note.isNotBlank()) DetailRow("Nota", p.note)
                        Button(onClick = { db.toggleFavorite(p.favoriteKey); favorites = db.favorites() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), shape = RoundedCornerShape(14.dp)) {
                            Icon(if (p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (p.favoriteKey in favorites) "Quitar de favoritos" else "Guardar en favoritos")
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ink).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Encuentra la pieza correcta", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("${parts.size} referencias organizadas por vehículo y sistema", fontSize = 12.sp, color = Color(0xFFD5DCE6))
                    }
                    DropdownField("Vehículo", activeVehicle, vehicleOptions) {
                        selectedVehicle = it
                        selectedCategory = "Todas las categorías"
                    }
                    OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar nombre, código o sistema") },
                        leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(15.dp))
                    DropdownField("Categoría / sistema", selectedCategory, categoryOptions) { selectedCategory = it }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !onlyFavorites, onClick = { onlyFavorites = false }, label = { Text("Todos") })
                        FilterChip(selected = onlyFavorites, onClick = { onlyFavorites = true }, label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp)); Text("Favoritos")
                            }
                        })
                        Spacer(Modifier.weight(1f))
                        Text("${shown.size} piezas", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (message.isNotBlank()) Text(message, fontSize = 11.sp, color = Color.Gray)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        if (shown.isEmpty()) {
                            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Search, null, tint = Color.LightGray, modifier = Modifier.size(42.dp))
                                Text("No encontramos piezas", fontWeight = FontWeight.SemiBold, color = ink)
                                Text("Prueba otro nombre, código o categoría.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 24.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shown, key = { it.favoriteKey }) { p ->
                                    Card(Modifier.fillMaxWidth().clickable { detail = p }, shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                                        Box {
                                            PartPhoto(p.code, p.photos, p.name, ContentScale.Fit,
                                                Modifier.fillMaxWidth().height(132.dp).padding(7.dp))
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
