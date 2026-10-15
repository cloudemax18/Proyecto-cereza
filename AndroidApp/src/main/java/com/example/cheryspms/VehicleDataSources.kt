package com.example.cheryspms

data class VehicleDataSource(
    val vehicle: VehicleModel,
    val rootPath: String,
    val dbfRelativePath: String,
    val picturesFolder: String
) {
    val dbfUrl: String
        get() = "https://raw.githubusercontent.com/cloudemax18/Proyecto-cereza/main/$dbfRelativePath"
    val picturesPath: String get() = "$rootPath/$picturesFolder"
}

object VehicleDataSources {
    val all = listOf(
        VehicleDataSource(VehicleModel.S12, "S12-Sep08", "S12-Sep08/DData/chery.dbf", "Pictures"),
        VehicleDataSource(VehicleModel.M11, "M11", "M11/DData/chery.dbf", "Pictures"),
        VehicleDataSource(VehicleModel.S21, "S21-Sep08", "S21-Sep08/DData/chery.dbf", "Pictures")
    )
    fun forVehicle(vehicle: VehicleModel): VehicleDataSource = all.first { it.vehicle == vehicle }
    fun candidateDbfDirectory(vehicle: VehicleModel): String = forVehicle(vehicle).rootPath
}
