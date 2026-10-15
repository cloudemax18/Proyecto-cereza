package com.example.cheryspms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { CatalogScreen() } } }
    }
}

private enum class ScreenLanguage(val code: String, val label: String) {
    ES("es", "Español"), EN("en", "English"), RU("ru", "Русский")
}
private data class Copy(
    val title: String, val vehicle: String, val language: String, val search: String,
    val refresh: String, val loading: String, val noResults: String, val parts: String,
    val error: String, val retry: String, val quantity: String, val source: String
)
private fun copyFor(language: ScreenLanguage) = when (language) {
    ScreenLanguage.EN -> Copy("Chery SPMS · Parts catalog", "Vehicle", "Language", "Search code, name or any field",
        "Reload catalog", "Loading real DBF catalog…", "No matching parts for this vehicle.", "parts",
        "Could not load DBF", "Retry", "Catalog quantity", "Source")
    ScreenLanguage.RU -> Copy("Chery SPMS · Каталог запчастей", "Автомобиль", "Язык", "Поиск по коду, названию или полю",
        "Обновить каталог", "Загрузка каталога DBF…", "Для этого автомобиля детали не найдены.", "деталей",
        "Не удалось загрузить DBF", "Повторить", "Количество в каталоге", "Источник")
    ScreenLanguage.ES -> Copy("Chery SPMS · Catálogo de repuestos", "Vehículo", "Idioma", "Buscar código, nombre o campo",
        "Recargar catálogo", "Cargando catálogo DBF real…", "No hay piezas que coincidan con este vehículo.", "piezas",
        "No se pudo cargar el DBF", "Reintentar", "Cantidad en catálogo", "Origen")
}

@Composable
private fun CatalogScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chery_spms", 0) }
    var selectedVehicle by remember { mutableStateOf(VehicleModel.entries.firstOrNull { it.id == prefs.getString("vehicle_id", "s12") } ?: VehicleModel.S12) }
    var language by remember { mutableStateOf(ScreenLanguage.entries.firstOrNull { it.code == prefs.getString("language", "es") } ?: ScreenLanguage.ES) }
    var query by remember { mutableStateOf("") }
    var parts by remember { mutableStateOf<List<VehiclePart>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var vehicleMenu by remember { mutableStateOf(false) }
    var languageMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val strings = copyFor(language)
    val repository = remember { RemoteDbfRepository() }

    fun loadCatalog(vehicle: VehicleModel) {
        loading = true
        error = null
        scope.launch {
            try {
                parts = repository.loadParts(vehicle)
            } catch (t: Throwable) {
                error = t.message ?: strings.error
                parts = emptyList()
            } finally { loading = false }
        }
    }
    LaunchedEffect(selectedVehicle) { loadCatalog(selectedVehicle) }
    val filtered = remember(parts, selectedVehicle, query) { VehicleCatalog.search(parts, selectedVehicle, query) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(strings.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(strings.vehicle, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vehicleMenu = true }) { Text(selectedVehicle.folder) }
            DropdownMenu(expanded = vehicleMenu, onDismissRequest = { vehicleMenu = false }) {
                VehicleModel.entries.forEach { model ->
                    DropdownMenuItem(text = { Text(model.folder) }, onClick = {
                        selectedVehicle = model
                        prefs.edit().putString("vehicle_id", model.id).apply()
                        vehicleMenu = false
                    })
                }
            }
            TextButton(onClick = { languageMenu = true }) { Text("${strings.language}: ${language.label}") }
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
        OutlinedTextField(value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(), label = { Text(strings.search) }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${filtered.size} ${strings.parts}", modifier = Modifier.weight(1f))
            TextButton(onClick = { loadCatalog(selectedVehicle) }) { Text(strings.refresh) }
        }
        Text("${strings.source}: ${VehicleDataSources.forVehicle(selectedVehicle).dbfRelativePath}",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text(strings.loading)
            }
        } else if (error != null) {
            Text("${strings.error}: $error", color = MaterialTheme.colorScheme.error)
            Button(onClick = { loadCatalog(selectedVehicle) }) { Text(strings.retry) }
        } else if (filtered.isEmpty()) {
            Text(strings.noResults)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { "${it.vehicleId}:${it.serial}:${it.rawFields.hashCode()}" }) { part ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(part.serial.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium)
                            if (part.name.isNotBlank()) Text(part.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${strings.quantity}: ${part.stock}")
                            if (part.model.isNotBlank()) Text(part.model, style = MaterialTheme.typography.bodySmall)
                            part.rawFields.forEach { (field, value) ->
                                if (value.isNotBlank() && value != part.serial && value != part.name && value != part.model) {
                                    Text("$field: $value", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
