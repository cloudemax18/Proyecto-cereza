package com.example.cheryspms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale

/** Lee chery.dbf real desde GitHub; no descarga tablas de cuentas. */
class RemoteDbfRepository {
    suspend fun loadParts(vehicle: VehicleModel): List<VehiclePart> = withContext(Dispatchers.IO) {
        val source = VehicleDataSources.forVehicle(vehicle)
        parseDbf(download(source.dbfUrl), vehicle)
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("User-Agent", "CherySPMS-Android")
        try {
            val status = connection.responseCode
            check(status in 200..299) { "No se pudo descargar el catálogo ($status)" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                }
                return output.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private data class Field(val name: String, val length: Int)

    private fun parseDbf(bytes: ByteArray, vehicle: VehicleModel): List<VehiclePart> {
        require(bytes.size >= 33) { "DBF incompleto" }
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = header.getInt(4).coerceAtLeast(0)
        val headerLength = header.getShort(8).toInt() and 0xffff
        val recordLength = header.getShort(10).toInt() and 0xffff
        require(headerLength in 33..bytes.size && recordLength > 1) { "Cabecera DBF inválida para ${vehicle.folder}" }

        val charset = Charset.forName("windows-1252")
        val fields = mutableListOf<Field>()
        var p = 32
        while (p < headerLength && bytes[p] != 0x0D.toByte()) {
            require(p + 32 <= bytes.size) { "Descriptor DBF truncado" }
            val nameBytes = bytes.copyOfRange(p, p + 11)
            val end = nameBytes.indexOf(0)
            val name = String(if (end >= 0) nameBytes.copyOfRange(0, end) else nameBytes, charset).trim()
            val length = bytes[p + 16].toInt() and 0xff
            if (name.isNotEmpty() && length > 0) fields += Field(name, length)
            p += 32
        }
        require(fields.isNotEmpty()) { "No se detectaron columnas DBF" }
        val byName = fields.associateBy { norm(it.name) }
        fun find(vararg names: String): Field? = names.firstNotNullOfOrNull { byName[norm(it)] }
        val code = find("SERIAL", "PARTNO", "PART_NO", "PARTNUM", "PART", "ITEMNO", "ITEM", "CODE", "PN", "NUMBER", "NO")
        val description = find("DESCRIPTION", "DESCRIPT", "PARTNAME", "NAME", "PARTDESC", "DESC", "DESCR", "ENGLISH", "ENGNAME")
        val model = find("MODEL", "MODELCODE", "CARMODEL", "VEHICLE", "TYPE")
        val qty = find("QUANTITY", "QTY", "COUNT", "AMOUNT", "NUM")
        val loc = find("LOCATION", "LOC", "POSITION", "POS", "FIGURE", "SECTION", "GROUP")

        val result = ArrayList<VehiclePart>(count)
        var record = headerLength
        val safeCount = minOf(count, (bytes.size - headerLength).coerceAtLeast(0) / recordLength)
        repeat(safeCount) {
            if (bytes[record] != '*'.code.toByte()) {
                var fieldOffset = record + 1
                val row = linkedMapOf<String, String>()
                fields.forEach { f ->
                    val end = minOf(fieldOffset + f.length, record + recordLength)
                    row[f.name] = String(bytes.copyOfRange(fieldOffset, end), charset).trim().trim('\u0000')
                    fieldOffset += f.length
                }
                if (row.values.any { it.isNotBlank() }) {
                    val serial = row.value(code).ifBlank { row.values.firstOrNull { it.isNotBlank() }.orEmpty() }
                    val name = row.value(description).ifBlank { row.values.firstOrNull { it.isNotBlank() && it != serial }.orEmpty() }
                    result += VehiclePart(
                        vehicleId = vehicle.id, serial = serial, name = name,
                        model = row.value(model), stock = row.value(qty).toDoubleOrNull()?.toInt() ?: 0,
                        location = row.value(loc), rawFields = row
                    )
                }
            }
            record += recordLength
        }
        return result
    }

    private fun norm(s: String) = s.uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }
    private fun Map<String, String>.value(field: Field?): String = if (field == null) "" else this[field.name].orEmpty()
}
