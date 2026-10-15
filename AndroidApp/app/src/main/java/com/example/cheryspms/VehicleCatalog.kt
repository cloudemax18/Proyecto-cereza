package com.example.cheryspms

enum class VehicleModel(val id: String, val folder: String, val displayNameEs: String, val displayNameEn: String, val displayNameRu: String) {
    S12("s12", "S12-Sep08", "Modelo S12", "S12 model", "Модель S12"),
    M11("m11", "M11", "Modelo M11", "M11 model", "Модель M11"),
    S21("s21", "S21-Sep08", "Modelo S21", "S21 model", "Модель S21");

    fun displayName(language: String): String = when (language) {
        "English" -> displayNameEn
        "Русский", "Ruso" -> displayNameRu
        else -> displayNameEs
    }
}

data class VehiclePart(
    val vehicleId: String,
    val serial: String,
    val name: String,
    val model: String,
    val stock: Int,
    val location: String,
    val rawFields: Map<String, String> = emptyMap()
)

object VehicleCatalog {
    fun forVehicle(parts: List<VehiclePart>, selected: VehicleModel): List<VehiclePart> =
        parts.filter { it.vehicleId == selected.id }

    fun search(parts: List<VehiclePart>, selected: VehicleModel, query: String): List<VehiclePart> {
        val selectedParts = forVehicle(parts, selected)
        val term = query.trim()
        if (term.isEmpty()) return selectedParts
        return selectedParts.filter { p ->
            p.serial.contains(term, true) || p.name.contains(term, true) ||
                p.model.contains(term, true) || p.location.contains(term, true) ||
                p.rawFields.values.any { it.contains(term, true) }
        }
    }
}
