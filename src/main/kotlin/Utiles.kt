import com.google.gson.JsonElement
import java.sql.PreparedStatement
import java.sql.Types

// Convierte el value de la API a Int?, sirva lo que venga:
// 7 -> 7   |   "54%" -> 54   |   null -> null   |   "" -> null
fun JsonElement?.aEntero(): Int? {
    if (this == null || this.isJsonNull) return null
    val texto = this.asString.replace("%", "").trim()
    return texto.toDoubleOrNull()?.toInt()
}

// Mete un Int? en un PreparedStatement respetando el NULL
fun ponerInt(ps: PreparedStatement, indice: Int, valor: Int?) {
    if (valor != null) ps.setInt(indice, valor) else ps.setNull(indice, Types.INTEGER)
}