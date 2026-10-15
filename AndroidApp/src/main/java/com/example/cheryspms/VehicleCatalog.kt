package com.example.cheryspms

/**
 * IDs estables para separar los catálogos de cada vehículo.
 * El nombre de carpeta se mantiene igual al del repositorio.
 */
enum class VehicleModel(
    val id: String,
    val folder: String,
    val displayNameEs: String,
    val displayNameEn: String,
    val displayNameRu: String
) {
    S12("s12", "S12-Sep08", "Modelo S12", "S12 model", "Модель S12"),
    M11("m11", "m11", "Modelo M11", "M11 model", "Модель M11"),
    S21("s21", "S21-Sep08", "Modelo S21", "S21 model", "Модель S21");

    fun displayName(language: String): String = when (language) {
        "English" -> displayNameEn
        "Русский", "Ruso" -> displayNameRu
        else -> displayNameEs
    }
}

/**
 * Modelo normalizado de una pieza importada desde cualquiera de los DBF.
 * vehicleId debe ser asignado por la carpeta de origen, no por el campo
 * "model" del DBF, que puede representar una variante o motorización.
 */
data class VehiclePart(
    val vehicleId: String,
    val serial: String,
    val name: String,
    val model: String,
    val stock: Int,
    val location: String
)

/** Catálogo general: filtra por vehículo antes de aplicar el texto de búsqueda. */
object VehicleCatalog {
    fun forVehicle(parts: List<VehiclePart>, selected: VehicleModel): List<VehiclePart> =
        parts.filter { it.vehicleId == selected.id }

    fun search(
        parts: List<VehiclePart>,
        selected: VehicleModel,
        query: String
    ): List<VehiclePart> {
        val vehicleParts = forVehicle(parts, selected)
        val term = query.trim()
        if (term.isEmpty()) return vehicleParts
        return vehicleParts.filter { part ->
            part.serial.contains(term, ignoreCase = true) ||
                part.name.contains(term, ignoreCase = true) ||
                part.model.contains(term, ignoreCase = true) ||
                part.location.contains(term, ignoreCase = true)
        }
    }
}
