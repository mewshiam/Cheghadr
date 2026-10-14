package ir.cheghadr.core.common

/**
 * RFC 4180-style CSV building (blueprint §15): fields containing commas,
 * quotes or line breaks are quoted; inner quotes are doubled. Deterministic
 * `\r\n` row endings so exported files open cleanly in spreadsheets.
 */
object Csv {

    fun escape(field: String): String {
        val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuoting) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun row(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    fun build(header: List<String>, rows: List<List<String>>): String = buildString {
        append(row(header))
        rows.forEach {
            append("\r\n")
            append(row(it))
        }
    }
}
