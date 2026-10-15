package com.example.cheryspms

/**
 * Repository-relative source paths. DBF filenames are deliberately not
 * hardcoded until each directory's actual filename is verified.
 */
data class VehicleDataSource(
    val vehicle: VehicleModel,
    val rootPath: String
) {
    val picturesPath: String get() = "$rootPath/pictures"
}

object VehicleDataSources {
    val all = listOf(
        VehicleDataSource(VehicleModel.S12, "S12-Sep08"),
        VehicleDataSource(VehicleModel.M11, "m11"),
        VehicleDataSource(VehicleModel.S21, "S21-Sep08")
    )

    fun forVehicle(vehicle: VehicleModel): VehicleDataSource =
        all.first { it.vehicle == vehicle }

    /**
     * A DBF resolver should list the files inside source.rootPath and select
     * the confirmed parts table. It must not assume every DBF in a folder
     * is the catalog, because a directory may also contain account/config DBFs.
     */
    fun candidateDbfDirectory(vehicle: VehicleModel): String =
        forVehicle(vehicle).rootPath
}
