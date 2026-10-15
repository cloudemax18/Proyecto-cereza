package com.cereza.catalogo

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import kotlinx.coroutines.launch
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
        enableEdgeToEdge()
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
    var selectedCategory by remember { mutableStateOf("Todas las categorías") }
    var onlyFavorites by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Part?>(null) }
    var fullScreenPhoto by remember { mutableStateOf<Part?>(null) }
    var message by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("Español") }
    var languageMenu by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var filtersOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    val english = language == "English"
    val cyan = Color(0xFF55D6F5)
    val canvas = Color(0xFF101722)
    val surface = Color(0xFF1B2635)
    val elevated = Color(0xFF243246)
    val muted = Color(0xFF9BAFC4)
    val drawerState = androidx.compose.material3.rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshToken) {
        if (parts.isEmpty() || refreshToken > 0) {
            loading = true
            try {
                withContext(Dispatchers.IO) { CATALOG_SOURCES.forEach { db.import(URL(RAW + it).readText()) } }
                parts = db.all()
                message = if (english) "Catalog updated" else "Catálogo actualizado"
            } catch (_: Exception) {
                message = if (parts.isEmpty()) (if (english) "Could not load catalog. Check your connection." else "No se pudo cargar el catálogo. Revisa tu conexión.")
                    else (if (english) "Offline mode · saved parts are shown" else "Modo sin conexión · se muestran las piezas guardadas")
            } finally { loading = false }
        }
    }
    val vehicles = parts.map { it.vehicle }.distinct().filter { it.isNotBlank() }.sorted()
    val activeVehicle = selectedVehicle.takeIf { it in vehicles } ?: ""
    val categories = parts.filter { it.vehicle == activeVehicle && activeVehicle.isNotBlank() }
        .map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val categoryOptions = listOf("Todas las categorías") + categories
    if (selectedCategory !in categoryOptions) selectedCategory = "Todas las categorías"
    val shown = parts.filter { p ->
        activeVehicle.isNotBlank() && p.vehicle == activeVehicle &&
        (selectedCategory == "Todas las categorías" || p.category == selectedCategory) &&
        (!onlyFavorites || p.favoriteKey in favorites) &&
        (search.isBlank() || p.code.contains(search,true) || p.name.contains(search,true) ||
         p.category.contains(search,true) || p.node.contains(search,true) || p.parentCode.contains(search,true))
    }

    MaterialTheme(colorScheme = darkColorScheme(primary=cyan,onPrimary=Color(0xFF09202B),primaryContainer=elevated,
        onPrimaryContainer=Color.White,secondary=Color(0xFFB5A6FF),background=canvas,surface=surface,
        onSurface=Color(0xFFF3F7FC),surfaceVariant=elevated,onSurfaceVariant=muted,outline=Color(0xFF3D4D62))) {
        ModalNavigationDrawer(drawerState=drawerState, gesturesEnabled=true, drawerContent={
            ModalDrawerSheet(drawerContainerColor=surface, drawerContentColor=Color.White) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment=Alignment.CenterVertically) {
                    Surface(shape=RoundedCornerShape(15.dp),color=cyan) {
                        Icon(Icons.Default.Build,null,tint=canvas,modifier=Modifier.padding(12.dp).size(27.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column { Text("CEREZA",fontWeight=FontWeight.Black,letterSpacing=2.sp)
                        Text(if(english) "PARTS CATALOG" else "CATÁLOGO DE REPUESTOS",fontSize=10.sp,color=muted) }
                }
                HorizontalDivider(color=elevated)
                NavigationDrawerItem(icon={Icon(Icons.Default.Home,null)},label={Text(if(english) "Catalog" else "Catálogo")},
                    selected=!aboutOpen,onClick={aboutOpen=false;detail=null},modifier=Modifier.padding(horizontal=10.dp))
                NavigationDrawerItem(icon={Icon(Icons.Default.Favorite,null)},label={Text(if(english) "Favorites" else "Favoritos")},
                    selected=onlyFavorites,onClick={onlyFavorites=!onlyFavorites;aboutOpen=false},modifier=Modifier.padding(horizontal=10.dp))
                NavigationDrawerItem(icon={Icon(Icons.Default.Build,null)},label={Text(if(english) "About" else "Acerca de")},
                    selected=aboutOpen,onClick={aboutOpen=true;detail=null},modifier=Modifier.padding(horizontal=10.dp))
                Spacer(Modifier.weight(1f))
                Text(if(english) "LANGUAGE" else "IDIOMA",color=muted,fontSize=11.sp,modifier=Modifier.padding(start=22.dp,top=12.dp))
                listOf("Español","English").forEach { lang ->
                    NavigationDrawerItem(label={Text(lang)},selected=language==lang,onClick={language=lang},modifier=Modifier.padding(horizontal=10.dp))
                }
                Spacer(Modifier.height(24.dp))
            }
        }) {
            Scaffold(containerColor=canvas,topBar={
                TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=canvas,titleContentColor=Color.White,
                    navigationIconContentColor=Color.White,actionIconContentColor=Color.White),title={
                    Column {
                        Text(if(aboutOpen) (if(english) "About Cereza" else "Acerca de Cereza") else if(detail!=null) (if(english) "Part details" else "Detalle de pieza") else "CEREZA",
                            fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
                        Text(if(detail!=null) detail!!.code else if(activeVehicle.isNotBlank()) activeVehicle else "SPARE PARTS EXPLORER",
                            fontSize=10.sp,color=muted,letterSpacing=1.sp,maxLines=1)
                    }
                },navigationIcon={
                    IconButton(onClick={ if(detail!=null) detail=null else if(aboutOpen) aboutOpen=false else scope.launch { drawerState.open() } }) {
                        Icon(if(detail!=null || aboutOpen) Icons.Default.Refresh else Icons.Default.Menu,if(english) "Back or menu" else "Volver o menú")
                    }
                },actions={
                    if(detail==null && !aboutOpen) {
                        Box { IconButton(onClick={languageMenu=true}) { Icon(Icons.Default.Translate,"Idioma") }
                            DropdownMenu(expanded=languageMenu,onDismissRequest={languageMenu=false}) {
                                listOf("Español","English").forEach { lang -> DropdownMenuItem(text={Text(lang)},onClick={language=lang;languageMenu=false}) }
                            }
                        }
                        IconButton(onClick={refreshToken++;loading=true}) { Icon(Icons.Default.Refresh,"Actualizar") }
                    }
                })
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).background(canvas).padding(horizontal=16.dp),
                    verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    when {
                        aboutOpen -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            Surface(shape=RoundedCornerShape(28.dp),color=surface,modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Surface(shape=RoundedCornerShape(20.dp),color=cyan) { Icon(Icons.Default.Build,null,tint=canvas,modifier=Modifier.padding(18.dp).size(42.dp)) }
                                    Text("CEREZA",fontSize=28.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp)
                                    Text(if(english) "A focused spare-parts catalog" else "Un catálogo de repuestos claro y práctico",fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                                    Text(if(english) "Browse by vehicle and system, search references, save favorites and inspect part images." else
                                        "Explora por vehículo y sistema, busca referencias, guarda favoritos y examina las imágenes de cada pieza.",color=muted,lineHeight=21.sp)
                                    HorizontalDivider(color=elevated)
                                    Text(if(english) "Design and interface" else "Diseño e interfaz",fontWeight=FontWeight.Bold)
                                    Text(if(english) "Interface concept, interaction design and implementation assistance by ChatGPT (OpenAI), guided by Android design recommendations." else
                                        "Concepto de interfaz, diseño de interacción y asistencia de implementación por ChatGPT (OpenAI), siguiendo recomendaciones de diseño de Android.",color=muted,lineHeight=21.sp)
                                    Text(if(english) "Vehicle and part data comes from the catalog source." else "Los datos de vehículos y piezas provienen del catálogo.",color=muted,fontSize=12.sp)
                                }
                            }
                        }
                        detail!=null -> {
                            val p=detail!!
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                PartPhoto(p.code,p.photos,p.name,ContentScale.Fit,Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(26.dp)).clickable { fullScreenPhoto=p })
                                Text(if(english) "Tap image to open · pinch to zoom" else "Toca la imagen para abrirla · pellizca para ampliar",fontSize=12.sp,color=muted)
                                Text(p.name,fontSize=24.sp,fontWeight=FontWeight.Bold,color=Color.White)
                                Text(p.code,color=cyan,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { InfoPill(p.vehicle); InfoPill(p.category) }
                                Surface(color=surface,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                        DetailRow(if(english) "Quantity" else "Cantidad",p.qty.toString())
                                        DetailRow(if(english) "Category" else "Categoría",p.category)
                                        DetailRow(if(english) "Parent code" else "Código padre",p.parentCode.ifBlank{"—"})
                                        DetailRow(if(english) "Node" else "Nodo",p.node.ifBlank{"—"})
                                        if(p.unit.isNotBlank()) DetailRow(if(english) "Unit" else "Unidad",p.unit)
                                        if(p.price>0) DetailRow(if(english) "Recorded price" else "Precio registrado",String.format("%.2f",p.price))
                                        if(p.note.isNotBlank()) DetailRow(if(english) "Note" else "Nota",p.note)
                                    }
                                }
                                Button(onClick={db.toggleFavorite(p.favoriteKey);favorites=db.favorites()},modifier=Modifier.fillMaxWidth().padding(bottom=24.dp),shape=RoundedCornerShape(16.dp)) {
                                    Icon(if(p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(if(p.favoriteKey in favorites) (if(english) "Remove favorite" else "Quitar de favoritos") else (if(english) "Save favorite" else "Guardar en favoritos"))
                                }
                            }
                        }
                        else -> {
                            AnimatedVisibility(visible=activeVehicle.isBlank(),enter=fadeIn()+slideInVertically{-it/8}) {
                                Column(Modifier.fillMaxWidth().padding(top=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                    Text(if(english) "Parts, without the guesswork." else "Tus repuestos, sin perderte.",fontSize=27.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold,color=Color.White)
                                    Text(if(english) "Choose a vehicle to reveal its catalog." else "Elige un vehículo para descubrir su catálogo.",color=muted,fontSize=14.sp)
                                }
                            }
                            Surface(shape=RoundedCornerShape(20.dp),color=surface,modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    DropdownField(if(english) "Vehicle" else "Vehículo",activeVehicle.ifBlank{if(english) "Choose a vehicle" else "Seleccionar vehículo"},
                                        vehicles,{ selectedVehicle=it;selectedCategory="Todas las categorías";search="";onlyFavorites=false;filtersOpen=false })
                                    AnimatedVisibility(visible=activeVehicle.isNotBlank()) {
                                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(activeVehicle,fontWeight=FontWeight.Bold,color=Color.White)
                                                    Text(parts.count{it.vehicle==activeVehicle}.toString()+" "+(if(english) "references" else "referencias"),fontSize=11.sp,color=muted)
                                                }
                                                TextButton(onClick={filtersOpen=!filtersOpen}) {
                                                    Text(if(filtersOpen) (if(english) "Hide filters" else "Ocultar filtros") else (if(english) "Filters" else "Filtros"))
                                                }
                                            }
                                            AnimatedVisibility(visible=filtersOpen,enter=fadeIn()+slideInVertically{-it/5}) {
                                                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                                    DropdownField(if(english) "System / category" else "Sistema / categoría",
                                                        if(selectedCategory=="Todas las categorías") (if(english) "All systems" else "Todos los sistemas") else selectedCategory,
                                                        categoryOptions,{selectedCategory=it;filtersOpen=false})
                                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                                        FilterChip(selected=!onlyFavorites,onClick={onlyFavorites=false},label={Text(if(english) "All parts" else "Todas las piezas")})
                                                        FilterChip(selected=onlyFavorites,onClick={onlyFavorites=true},label={Text(if(english) "Favorites" else "Favoritos")})
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            AnimatedVisibility(visible=activeVehicle.isNotBlank(),enter=fadeIn()+slideInVertically{it/8}) {
                                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),
                                        placeholder={Text(if(english) "Search name, code or system" else "Buscar nombre, código o sistema")},
                                        leadingIcon={Icon(Icons.Default.Search,null,tint=cyan)},singleLine=true,shape=RoundedCornerShape(17.dp),
                                        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=surface,focusedContainerColor=surface,
                                            unfocusedBorderColor=elevated,focusedBorderColor=cyan))
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Text(shown.size.toString()+" "+(if(english) "parts" else "piezas"),color=muted,fontSize=12.sp,modifier=Modifier.weight(1f))
                                        SingleChoiceSegmentedButtonRow {
                                            SegmentedButton(selected=!listMode,onClick={listMode=false},shape=SegmentedButtonDefaults.itemShape(0)) { Icon(Icons.Default.GridView,"Cuadrícula") }
                                            SegmentedButton(selected=listMode,onClick={listMode=true},shape=SegmentedButtonDefaults.itemShape(1)) { Icon(Icons.Default.ViewList,"Lista") }
                                        }
                                    }
                                }
                            }
                            if(loading) LinearProgressIndicator(Modifier.fillMaxWidth(),color=cyan)
                            if(message.isNotBlank()) Text(message,color=muted,fontSize=11.sp)
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                if(activeVehicle.isBlank()) {
                                    Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        Surface(shape=RoundedCornerShape(28.dp),color=surface) { Icon(Icons.Default.Build,null,tint=cyan,modifier=Modifier.padding(22.dp).size(42.dp)) }
                                        Text(if(english) "No vehicle selected" else "Ningún auto seleccionado",color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                                        Text(if(english) "Select a vehicle above. Parts stay hidden until you choose one." else "Selecciona un auto arriba. Las piezas permanecerán ocultas hasta que elijas uno.",color=muted,fontSize=13.sp,lineHeight=19.sp)
                                    }
                                } else if(shown.isEmpty()) {
                                    Column(Modifier.align(Alignment.Center).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Search,null,tint=muted,modifier=Modifier.size(36.dp))
                                        Text(if(english) "No parts found" else "No encontramos piezas",color=Color.White,fontWeight=FontWeight.SemiBold)
                                    }
                                } else if(listMode) {
                                    LazyColumn(contentPadding=PaddingValues(top=2.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                        lazyItems(shown,key={it.favoriteKey}) { p ->
                                            Card(Modifier.fillMaxWidth().animateContentSize(spring()).clickable{detail=p},shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                                                Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                                    PartPhoto(p.code,p.photos,p.name,ContentScale.Fit,Modifier.size(84.dp).clip(RoundedCornerShape(13.dp)))
                                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                                        Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2)
                                                        Text(p.code,color=cyan,fontSize=11.sp,fontWeight=FontWeight.Bold)
                                                        Text(p.category,color=muted,fontSize=11.sp,maxLines=1)
                                                        Text((if(english) "Qty. " else "Cant. ")+p.qty,color=Color.White,fontSize=11.sp)
                                                    }
                                                    IconButton(onClick={db.toggleFavorite(p.favoriteKey);favorites=db.favorites()}) {
                                                        Icon(if(p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,null,tint=if(p.favoriteKey in favorites) cyan else muted)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(columns=GridCells.Adaptive(minSize=148.dp),contentPadding=PaddingValues(top=2.dp,bottom=28.dp),
                                        horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        items(shown,key={it.favoriteKey}) { p ->
                                            Card(Modifier.fillMaxWidth().animateContentSize(spring()).clickable{detail=p},shape=RoundedCornerShape(21.dp),colors=CardDefaults.cardColors(containerColor=surface)) {
                                                Box {
                                                    PartPhoto(p.code,p.photos,p.name,ContentScale.Fit,Modifier.fillMaxWidth().height(142.dp).padding(7.dp).clip(RoundedCornerShape(15.dp)))
                                                    if(p.price>0) Surface(Modifier.align(Alignment.TopEnd).padding(9.dp),color=Color(0xFF153B3A),shape=RoundedCornerShape(8.dp)) {
                                                        Text(String.format("%.2f",p.price),color=Color(0xFF9EF1D2),fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=7.dp,vertical=4.dp))
                                                    }
                                                }
                                                Column(Modifier.fillMaxWidth().padding(start=11.dp,end=8.dp,bottom=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                                    Text(p.name,fontWeight=FontWeight.SemiBold,fontSize=13.sp,minLines=2,maxLines=2)
                                                    Text(p.code,color=cyan,fontSize=10.sp,fontWeight=FontWeight.Bold,maxLines=1)
                                                    Text(p.category,color=muted,fontSize=10.sp,maxLines=1)
                                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                                        Text((if(english) "Qty. " else "Cant. ")+p.qty,fontSize=10.sp,color=Color.White)
                                                        Spacer(Modifier.weight(1f))
                                                        IconButton(onClick={db.toggleFavorite(p.favoriteKey);favorites=db.favorites()},modifier=Modifier.size(30.dp)) {
                                                            Icon(if(p.favoriteKey in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,"Favorito",tint=if(p.favoriteKey in favorites) cyan else muted,modifier=Modifier.size(17.dp))
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

    if(fullScreenPhoto!=null) {
        val photo=fullScreenPhoto!!
        var zoom by remember(photo.favoriteKey) { mutableFloatStateOf(1f) }
        var pan by remember(photo.favoriteKey) { mutableStateOf(Offset.Zero) }
        val zoomState=rememberTransformableState { zoomChange,panChange,_ ->
            zoom=(zoom*zoomChange).coerceIn(1f,6f);pan=if(zoom>1f) pan+panChange else Offset.Zero
        }
        val candidates=remember(photo.code,photo.photos) {(0..maxOf(photo.photos,4)).map{i->RAW+"fotos/"+photo.code+(if(i==0) "" else "_"+i)+".jpg"}}
        var candidateIndex by remember(photo.code) { mutableIntStateOf(0) }
        Dialog(onDismissRequest={fullScreenPhoto=null}) {
            Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center) {
                AsyncImage(model=candidates[candidateIndex],contentDescription="Imagen ampliada de "+photo.name,contentScale=ContentScale.Fit,
                    modifier=Modifier.fillMaxSize().graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=pan.x,translationY=pan.y).transformable(zoomState),
                    onError={if(candidateIndex<candidates.lastIndex) candidateIndex++})
                TextButton(onClick={fullScreenPhoto=null},modifier=Modifier.align(Alignment.TopEnd).padding(8.dp)) { Text(if(english) "Close" else "Cerrar",color=Color.White) }
                Text((if(english) "Pinch to zoom · " else "Pellizca para ampliar · ")+photo.name,color=Color.White,modifier=Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
    Surface(color = Color(0xFF353F54), shape = RoundedCornerShape(9.dp)) {
        Text(text, color = Color(0xFF34C8E8), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, color = Color(0xFFB4C0D2), fontSize = 13.sp, modifier = Modifier.weight(0.8f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun PartPhoto(code: String, count: Int, contentDescription: String, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val candidates = remember(code, count) {
        (0..maxOf(count, 4)).map { i -> RAW + "fotos/" + code + (if (i == 0) "" else "_$i") + ".jpg" }
    }
    var candidateIndex by remember(code, count) { mutableIntStateOf(0) }
    Box(modifier = modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF353F54)), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Build, null, tint = Color(0xFF63718A), modifier = Modifier.size(36.dp))
        AsyncImage(model = candidates[candidateIndex], contentDescription = contentDescription,
            contentScale = contentScale, modifier = Modifier.fillMaxSize(),
            onError = { if (candidateIndex < candidates.lastIndex) candidateIndex++ })
    }
}
