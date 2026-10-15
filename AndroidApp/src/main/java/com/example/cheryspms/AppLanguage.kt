package com.example.cheryspms

enum class AppLanguage(val code: String, val label: String) {
    SPANISH("es", "Español"),
    ENGLISH("en", "English"),
    RUSSIAN("ru", "Русский");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: SPANISH
    }
}

enum class UiText {
    APP_TITLE,
    SELECT_VEHICLE,
    CATALOG,
    SEARCH_PARTS,
    PART_CODE,
    PART_NAME,
    MODEL,
    QUANTITY_IN_CATALOG,
    DIAGRAMS,
    NO_RESULTS,
    LANGUAGE,
    TEMPORARY_LIST
}

object AppStrings {
    private val translations = mapOf(
        AppLanguage.SPANISH to mapOf(
            UiText.APP_TITLE to "Chery SPMS",
            UiText.SELECT_VEHICLE to "Seleccionar vehículo",
            UiText.CATALOG to "Catálogo general",
            UiText.SEARCH_PARTS to "Buscar repuestos",
            UiText.PART_CODE to "Código de pieza",
            UiText.PART_NAME to "Nombre de pieza",
            UiText.MODEL to "Modelo",
            UiText.QUANTITY_IN_CATALOG to "Cantidad en catálogo",
            UiText.DIAGRAMS to "Diagramas",
            UiText.NO_RESULTS to "No se encontraron piezas para este vehículo",
            UiText.LANGUAGE to "Idioma",
            UiText.TEMPORARY_LIST to "Lista temporal"
        ),
        AppLanguage.ENGLISH to mapOf(
            UiText.APP_TITLE to "Chery SPMS",
            UiText.SELECT_VEHICLE to "Select vehicle",
            UiText.CATALOG to "General catalog",
            UiText.SEARCH_PARTS to "Search parts",
            UiText.PART_CODE to "Part code",
            UiText.PART_NAME to "Part name",
            UiText.MODEL to "Model",
            UiText.QUANTITY_IN_CATALOG to "Catalog quantity",
            UiText.DIAGRAMS to "Diagrams",
            UiText.NO_RESULTS to "No parts found for this vehicle",
            UiText.LANGUAGE to "Language",
            UiText.TEMPORARY_LIST to "Temporary list"
        ),
        AppLanguage.RUSSIAN to mapOf(
            UiText.APP_TITLE to "Chery SPMS",
            UiText.SELECT_VEHICLE to "Выбрать автомобиль",
            UiText.CATALOG to "Общий каталог",
            UiText.SEARCH_PARTS to "Поиск запчастей",
            UiText.PART_CODE to "Код детали",
            UiText.PART_NAME to "Название детали",
            UiText.MODEL to "Модель",
            UiText.QUANTITY_IN_CATALOG to "Количество в каталоге",
            UiText.DIAGRAMS to "Схемы",
            UiText.NO_RESULTS to "Для этого автомобиля детали не найдены",
            UiText.LANGUAGE to "Язык",
            UiText.TEMPORARY_LIST to "Временный список"
        )
    )

    fun text(key: UiText, language: AppLanguage): String =
        translations[language]?.get(key) ?: translations.getValue(AppLanguage.SPANISH).getValue(key)
}
