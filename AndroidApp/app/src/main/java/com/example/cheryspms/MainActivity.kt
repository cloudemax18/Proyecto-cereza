package com.example.cheryspms

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.webkit.JavascriptInterface
import java.io.ByteArrayOutputStream
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val CheryRed = Color(0xFFB3261E)
private val CheryInk = Color(0xFF20242A)
private val CheryMuted = Color(0xFF667085)
private val CheryCanvas = Color(0xFFF5F6F8)
private val CheryLine = Color(0xFFE3E6EA)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = CheryRed, onPrimary = Color.White,
                primaryContainer = Color(0xFFFCE8E6), onPrimaryContainer = Color(0xFF601410),
                secondary = Color(0xFF48576A), background = CheryCanvas,
                surface = Color.White, surfaceVariant = Color(0xFFF0F2F5),
                onSurface = CheryInk, onSurfaceVariant = CheryMuted, outline = CheryLine
            )) {
                Surface(Modifier.fillMaxSize(), color = CheryCanvas) { CatalogScreen() }
            }
        }
    }
}

private enum class ScreenLanguage(val code: String, val label: String) {
    ES("es", "Español"), EN("en", "English"), RU("ru", "Русский")
}
private data class Copy(
    val title: String, val tagline: String, val vehicle: String, val language: String,
    val search: String, val refresh: String, val loading: String, val noResults: String,
    val parts: String, val error: String, val retry: String, val quantity: String,
    val source: String, val diagram: String, val noDiagram: String, val selectedPart: String,
    val showRaw: String, val hideRaw: String, val choosePart: String
)
private fun copyFor(language: ScreenLanguage) = when (language) {
    ScreenLanguage.EN -> Copy("Spare parts catalog", "Original catalog · diagrams and part numbers",
        "VEHICLE", "Language", "Search part number, name or category", "Reload", "Loading catalog…",
        "No parts match this search.", "parts", "Could not load catalog", "Try again", "Catalog quantity",
        "Source", "ILLUSTRATION", "No drawing was found for this code.", "Selected part",
        "Show technical fields", "Hide technical fields", "Choose a part to preview its drawing")
    ScreenLanguage.RU -> Copy("Каталог запчастей", "Оригинальный каталог · схемы и номера деталей",
        "АВТОМОБИЛЬ", "Язык", "Поиск по номеру, названию или группе", "Обновить", "Загрузка каталога…",
        "Совпадений не найдено.", "деталей", "Не удалось загрузить каталог", "Повторить", "Количество в каталоге",
        "Источник", "ИЛЛЮСТРАЦИЯ", "Для этого кода схема не найдена.", "Выбранная деталь",
        "Показать технические поля", "Скрыть технические поля", "Выберите деталь для просмотра схемы")
    ScreenLanguage.ES -> Copy("Catálogo de repuestos", "Catálogo original · diagramas y códigos de piezas",
        "VEHÍCULO", "Idioma", "Buscar código, nombre o categoría", "Recargar", "Cargando catálogo…",
        "No hay piezas que coincidan con la búsqueda.", "piezas", "No se pudo cargar el catálogo", "Reintentar",
        "Cantidad en catálogo", "Origen", "ILUSTRACIÓN", "No se encontró un dibujo para este código.",
        "Repuesto seleccionado", "Mostrar campos técnicos", "Ocultar campos técnicos",
        "Selecciona un repuesto para ver su dibujo")
}

@Composable
private fun CatalogScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chery_spms", 0) }
    val diagramIndex = remember { DiagramIndex(loadDiagramIndex(context)) }
    var selectedVehicle by remember {
        mutableStateOf(VehicleModel.entries.firstOrNull { it.id == prefs.getString("vehicle_id", "s12") } ?: VehicleModel.S12)
    }
    var language by remember {
        mutableStateOf(ScreenLanguage.entries.firstOrNull { it.code == prefs.getString("language", "es") } ?: ScreenLanguage.ES)
    }
    var query by remember { mutableStateOf("") }
    var parts by remember { mutableStateOf<List<VehiclePart>>(emptyList()) }
    var selectedPart by remember { mutableStateOf<VehiclePart?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    var rawExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strings = copyFor(language)
    val repository = remember { RemoteDbfRepository() }

    fun loadCatalog(vehicle: VehicleModel) {
        loading = true
        error = null
        scope.launch {
            try {
                val loaded = repository.loadParts(vehicle)
                parts = loaded
                selectedPart = loaded.firstOrNull { diagramIndex.pathFor(vehicle, it.serial) != null } ?: loaded.firstOrNull()
                rawExpanded = false
            } catch (t: Throwable) {
                error = t.message ?: strings.error
                parts = emptyList()
                selectedPart = null
            } finally { loading = false }
        }
    }

    LaunchedEffect(selectedVehicle) { loadCatalog(selectedVehicle) }
    val filtered = remember(parts, selectedVehicle, query) { VehicleCatalog.search(parts, selectedVehicle, query) }
    val chosen = selectedPart?.let { current ->
        filtered.firstOrNull { it.serial == current.serial && it.name == current.name && it.rawFields == current.rawFields }
    }
    val diagramFilename = chosen?.let { diagramIndex.pathFor(selectedVehicle, it) }
    val diagramUrl = diagramFilename?.let { pictureUrl(selectedVehicle, it) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(CheryRed), contentAlignment = Alignment.Center) {
                        Text("C", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("CHERY SPMS", style = MaterialTheme.typography.labelLarge, color = CheryRed, fontWeight = FontWeight.Bold)
                        Text(strings.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = CheryInk)
                    }
                    Box {
                        IconButton(onClick = { languageMenu = true }) {
                            Icon(Icons.Default.Language, contentDescription = strings.language, tint = CheryInk)
                        }
                        DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }) {
                            ScreenLanguage.entries.forEach { lang ->
                                DropdownMenuItem(text = { Text(lang.label) }, onClick = {
                                    language = lang
                                    prefs.edit().putString("language", lang.code).apply()
                                    languageMenu = false
                                })
                            }
                        }
                    }
                }
                Text(strings.tagline, style = MaterialTheme.typography.bodyMedium, color = CheryMuted)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, CheryLine)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.vehicle, style = MaterialTheme.typography.labelMedium, color = CheryMuted, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { vehicleMenu = true }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)) {
                            Text(selectedVehicle.displayName(language.code), modifier = Modifier.weight(1f), color = CheryInk)
                            Text("⌄", color = CheryRed, style = MaterialTheme.typography.titleLarge)
                        }
                        DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                            VehicleModel.entries.forEach { model ->
                                DropdownMenuItem(text = { Text(model.displayName(language.code)) }, onClick = {
                                    selectedVehicle = model
                                    prefs.edit().putString("vehicle_id", model.id).apply()
                                    vehicleMenu = false
                                })
                            }
                        }
                    }
                    OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(strings.search, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true, shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFFAFAFB), focusedContainerColor = Color.White
                        ))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Color(0xFFFCE8E6), shape = CircleShape) {
                            Text(filtered.size.toString() + " " + strings.parts,
                                Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = CheryRed,
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { loadCatalog(selectedVehicle) }, enabled = !loading) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(strings.refresh)
                        }
                    }
                    Text(strings.source + ": " + VehicleDataSources.forVehicle(selectedVehicle).dbfRelativePath,
                        style = MaterialTheme.typography.bodySmall, color = CheryMuted)
                }
            }
        }
        if (loading) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                        Text(strings.loading, color = CheryMuted)
                    }
                }
            }
        } else if (error != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(strings.error + ": " + error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = { loadCatalog(selectedVehicle) }) { Text(strings.retry) }
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(strings.noResults, Modifier.padding(20.dp), color = CheryMuted)
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(strings.diagram, style = MaterialTheme.typography.labelMedium, color = CheryRed, fontWeight = FontWeight.Bold)
                            Text(chosen?.let { it.name.ifBlank { it.serial } } ?: strings.choosePart,
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = CheryInk)
                        }
                        chosen?.serial?.takeIf { it.isNotBlank() }?.let { code ->
                            Surface(color = Color(0xFFF0F2F5), shape = RoundedCornerShape(8.dp)) {
                                Text(code, Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium, color = CheryInk)
                            }
                        }
                    }
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, CheryLine)) {
                        if (diagramUrl != null) {
                            DiagramPreview(cacheKey = selectedVehicle.id + ":" + diagramFilename.orEmpty(), url = diagramUrl, modifier = Modifier.fillMaxWidth().height(280.dp))
                        } else {
                            Box(Modifier.fillMaxWidth().height(170.dp).padding(20.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("▧", color = CheryRed, style = MaterialTheme.typography.headlineLarge)
                                    Text(strings.noDiagram, color = CheryMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    chosen?.let { part ->
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, CheryLine)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(strings.selectedPart, style = MaterialTheme.typography.labelMedium, color = CheryMuted)
                                Text(part.serial.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                if (part.name.isNotBlank()) Text(part.name, style = MaterialTheme.typography.bodyLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Color(0xFFF0F2F5), shape = RoundedCornerShape(8.dp)) {
                                        Text(strings.quantity + ": " + part.stock, Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelMedium, color = CheryInk)
                                    }
                                    if (part.diagramRef.isNotBlank()) {
                                        Surface(color = Color(0xFFFCE8E6), shape = RoundedCornerShape(8.dp)) {
                                            Text("Ref. " + part.diagramRef, Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelMedium, color = CheryRed)
                                        }
                                    }
                                }
                                TextButton(onClick = { rawExpanded = !rawExpanded }, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (rawExpanded) strings.hideRaw else strings.showRaw)
                                }
                                AnimatedVisibility(visible = rawExpanded, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
                                    Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        HorizontalDivider(color = CheryLine)
                                        part.rawFields.forEach { (field, value) ->
                                            if (value.isNotBlank()) Text(field + ": " + value, style = MaterialTheme.typography.bodySmall, color = CheryMuted)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text(if (language == ScreenLanguage.EN) "PARTS LIST" else if (language == ScreenLanguage.RU) "СПИСОК ДЕТАЛЕЙ" else "LISTA DE REPUESTOS",
                        style = MaterialTheme.typography.labelMedium, color = CheryMuted, fontWeight = FontWeight.Bold)
                }
            }
            items(filtered, key = { it.vehicleId + ":" + it.serial + ":" + it.name + ":" + it.rawFields.hashCode() }) { part ->
                val isSelected = chosen == part
                val cardColor by animateColorAsState(if (isSelected) Color(0xFFFFF7F6) else Color.White, label = "part-card-color")
                val cardBorder by animateColorAsState(if (isSelected) Color(0xFFE9B9B5) else CheryLine, label = "part-card-border")
                Card(modifier = Modifier.fillMaxWidth().animateContentSize().clickable {
                    selectedPart = part
                    rawExpanded = false
                }, shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = BorderStroke(1.dp, cardBorder)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFFCE8E6) else Color(0xFFF0F2F5)), contentAlignment = Alignment.Center) {
                            Text(if (diagramIndex.pathFor(selectedVehicle, part) != null) "▧" else "·",
                                color = if (isSelected) CheryRed else CheryMuted, style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(part.serial.ifBlank { "—" }, style = MaterialTheme.typography.titleSmall, color = CheryInk, fontWeight = FontWeight.Bold)
                            Text(part.name.ifBlank { part.location }, style = MaterialTheme.typography.bodyMedium, color = CheryMuted, maxLines = 2)
                            if (part.location.isNotBlank()) {
                                Text(part.location.substringAfterLast('/').substringAfterLast('\\'),
                                    style = MaterialTheme.typography.labelSmall, color = CheryMuted, maxLines = 1)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("×" + part.stock, style = MaterialTheme.typography.labelLarge, color = CheryInk, fontWeight = FontWeight.Bold)
                            Text(if (language == ScreenLanguage.EN) "qty" else if (language == ScreenLanguage.RU) "кол." else "cant.",
                                style = MaterialTheme.typography.labelSmall, color = CheryMuted)
                        }
                    }
                }
            }
        }
    }
}

private fun loadDiagramIndex(context: android.content.Context): Map<String, Map<String, String>> {
    return try {
        val json = context.assets.open("diagram-index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(json)
        VehicleModel.entries.associate { model ->
            val source = root.optJSONObject(model.id) ?: JSONObject()
            val map = mutableMapOf<String, String>()
            val keys = source.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = source.optString(key)
            }
            model.id to map
        }
    } catch (_: Exception) { emptyMap() }
}

private class DiagramIndex(private val entries: Map<String, Map<String, String>>) {
    fun pathFor(vehicle: VehicleModel, part: VehiclePart): String? {
        val map = entries[vehicle.id] ?: return null
        val fields = part.rawFields
        val candidates = linkedSetOf<String>()
        fun add(value: String?) {
            if (value.isNullOrBlank()) return
            value.split('/', '\\', ';', '|').forEach { segment ->
                val cleaned = segment.trim()
                if (cleaned.isNotEmpty()) candidates.add(normalizeCode(cleaned))
            }
            candidates.add(normalizeCode(value))
        }
        add(part.serial)
        add(part.diagramRef)
        add(part.parentRef)
        add(fields.entries.firstOrNull { normalizeCode(it.key) == "PARTNUMBER" }?.value)
        add(fields.entries.firstOrNull { normalizeCode(it.key) == "DIRECTORY" }?.value)
        add(fields.entries.firstOrNull { normalizeCode(it.key) == "HYPOTAXIS" }?.value)
        add(fields.entries.firstOrNull { normalizeCode(it.key) == "NO" || normalizeCode(it.key) == "N1" }?.value)
        return candidates.firstNotNullOfOrNull { key -> map[key] }
    }
}
private fun normalizeCode(value: String): String = value.uppercase().filter { it.isLetterOrDigit() }
private fun pictureUrl(vehicle: VehicleModel, filename: String): String {
    val folder = VehicleDataSources.forVehicle(vehicle).picturesPath
    val encodedFolder = folder.split('/').joinToString("/") { Uri.encode(it) }
    return "https://raw.githubusercontent.com/cloudemax18/Proyecto-cereza/main/" + encodedFolder + "/" + Uri.encode(filename)
}

@Composable
private fun DiagramPreview(cacheKey: String, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember(context) { DiagramImageDatabase(context.applicationContext) }
    var cached by remember(cacheKey) { mutableStateOf<Bitmap?>(null) }
    var attempted by remember(cacheKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cacheKey) {
        val bytes = withContext(Dispatchers.IO) { database.getImage(cacheKey) }
        if (bytes != null) cached = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        attempted = true
    }

    if (cached != null) {
        Image(bitmap = cached!!.asImageBitmap(), contentDescription = "Diagrama del repuesto",
            modifier = modifier, contentScale = ContentScale.Fit)
    } else if (attempted) {
        SwfDiagramViewer(url = url, cacheKey = cacheKey, modifier = modifier) { webp ->
            scope.launch {
                withContext(Dispatchers.IO) { database.putImage(cacheKey, webp, url) }
                cached = BitmapFactory.decodeByteArray(webp, 0, webp.size)
            }
        }
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CheryRed, strokeWidth = 3.dp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SwfDiagramViewer(
    url: String,
    cacheKey: String,
    modifier: Modifier = Modifier,
    onWebpReady: (ByteArray) -> Unit
) {
    AndroidView(modifier = modifier, factory = { context ->
        lateinit var webView: WebView
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) view.loadData(
                        "<html><body style='font:14px sans-serif;color:#667085;padding:24px;text-align:center'>No se pudo cargar el visor. Comprueba la conexión e inténtalo otra vez.</body></html>",
                        "text/html", "UTF-8")
                }
            }
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onRendered() {
                    webView.postDelayed({
                        try {
                            val width = webView.width.coerceAtLeast(1)
                            val height = webView.height.coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            webView.draw(Canvas(bitmap))
                            val output = ByteArrayOutputStream()
                            @Suppress("DEPRECATION")
                            bitmap.compress(Bitmap.CompressFormat.WEBP, 88, output)
                            bitmap.recycle()
                            val bytes = output.toByteArray()
                            if (bytes.isNotEmpty()) onWebpReady(bytes)
                        } catch (_: Exception) { }
                    }, 900)
                }
            }, "CheryBridge")
        }
    }, update = { view ->
        val signature = cacheKey + "|" + url
        if (view.tag != signature) {
            view.tag = signature
            val html = """
                <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3">
                <style>
                html,body,#stage{margin:0;width:100%;height:100%;background:#fff;overflow:hidden}
                #stage{display:flex;align-items:center;justify-content:center}
                ruffle-player{width:100%;height:100%;display:block}
                #message{font:14px sans-serif;color:#667085;text-align:center;padding:20px}
                </style>
                <script src="https://unpkg.com/@ruffle-rs/ruffle"></script></head>
                <body><div id="stage"><div id="message">Preparando ilustración…</div></div>
                <script>
                (async function(){
                  const stage=document.getElementById('stage');
                  try {
                    if(!window.RufflePlayer) throw new Error('No se pudo cargar el visor Flash');
                    const player=window.RufflePlayer.newest().createPlayer();
                    player.style.width='100%'; player.style.height='100%';
                    stage.innerHTML=''; stage.appendChild(player);
                    const response=await fetch('__DIAGRAM_URL__');
                    if(!response.ok) throw new Error('Archivo no disponible ('+response.status+')');
                    const data=await response.arrayBuffer();
                    await player.load({data:data, autoplay:'off', unmuteOverlay:'hidden'});
                    setTimeout(()=>{ if(window.CheryBridge) CheryBridge.onRendered(); }, 1400);
                  } catch(e) {
                    stage.innerHTML='<div id="message">No se pudo mostrar esta ilustración.<br>'+String(e.message||e)+'</div>';
                  }
                })();
                </script></body></html>
            """.trimIndent().replace("__DIAGRAM_URL__", url.replace("&", "&amp;").replace("'", "%27"))
            view.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
        }
    })
}
