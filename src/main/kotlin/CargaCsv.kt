import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// Cada archivo del sitio, con la liga a la que corresponde y su zona horaria.
// La zona importa: el CSV trae la hora local del partido, y si la guardas como
// si fuera UTC los partidos se corren dos horas y los filtros por dia fallan.
data class FuenteCsv(
    val url: String,
    val ligaId: Int,
    val nombre: String,
    val zona: ZoneId
)

val FUENTES = listOf(
    FuenteCsv("https://www.football-data.co.uk/new/SWE.csv", 113, "Allsvenskan", ZoneId.of("Europe/Stockholm")),
    FuenteCsv("https://www.football-data.co.uk/new/NOR.csv", 103, "Eliteserien", ZoneId.of("Europe/Oslo"))
)

private val FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")

// Descarga el archivo completo como texto. No lleva API key: es un archivo publico.
fun descargar(url: String): String? {
    val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)   // <-- esto es lo que faltaba
        .build()

    val req = HttpRequest.newBuilder()
        .uri(URI.create(url))
        // Algunos sitios rechazan al cliente de Java por su User-Agent por defecto.
        // Con uno de navegador el archivo se entrega sin problema.
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .GET()
        .build()

    val res = client.send(req, HttpResponse.BodyHandlers.ofString())
    println("GET $url -> HTTP ${res.statusCode()} (${res.body().length} bytes) | uri final: ${res.uri()}")
    return if (res.statusCode() == 200) res.body() else null
}
// El CSV no trae id de partido, y la tabla lo exige. Se construye uno a partir
// de liga+temporada+fecha+equipos. Es DETERMINISTA: el mismo partido siempre
// genera el mismo numero, asi recargar el archivo actualiza en vez de duplicar.
// Negativo para que nunca choque con los ids de API-Football, que son positivos.
fun idDeterminista(clave: String): Int {
    val h = Math.abs(clave.hashCode())
    return -(h % 2_000_000_000) - 1
}

fun main() {
    val sqlPartido = """
        INSERT INTO matches (fixture_id, league_id, season, match_date, round,
                             home_team, away_team, home_goals, away_goals,
                             match_status, source)
        VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?, 'FT', 'footballdata')
        ON CONFLICT (fixture_id) DO UPDATE SET
            home_goals   = EXCLUDED.home_goals,
            away_goals   = EXCLUDED.away_goals,
            match_status = EXCLUDED.match_status,
            match_date   = EXCLUDED.match_date,
            last_updated = CURRENT_TIMESTAMP
    """.trimIndent()

    val sqlCuota = """
        INSERT INTO match_odds (fixture_id, captured_at, bookmaker,
                                cuota_local, cuota_empate, cuota_visita)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT (fixture_id, bookmaker, captured_at) DO UPDATE SET
            cuota_local  = EXCLUDED.cuota_local,
            cuota_empate = EXCLUDED.cuota_empate,
            cuota_visita = EXCLUDED.cuota_visita
    """.trimIndent()

    var partidos = 0
    var cuotas = 0
    var saltadas = 0

    Config.conectar().use { con ->
        con.autoCommit = false
        val psPartido = con.prepareStatement(sqlPartido)
        val psCuota = con.prepareStatement(sqlCuota)

        for (f in FUENTES) {
            val texto = descargar(f.url) ?: continue
            val lineas = texto.lines().filter { it.isNotBlank() }
            if (lineas.size < 2) continue

            // Mapa "nombre de columna" -> posicion, leido de la primera linea.
            // Asi, si el sitio cambia el orden o agrega columnas, el codigo sigue
            // funcionando. Buscar por nombre y no por posicion es la regla de oro
            // cuando el archivo lo publica alguien mas.
            // El removePrefix saca el BOM, un caracter invisible que Windows mete
            // al inicio del archivo y que convertiria "Country" en "?Country".
            val cab = lineas[0].removePrefix("\uFEFF").split(",").map { it.trim() }
            val col = cab.withIndex().associate { (i, n) -> n to i }

            fun campo(partes: List<String>, nombre: String): String {
                val i = col[nombre] ?: return ""
                return if (i < partes.size) partes[i].trim() else ""
            }

            var enArchivo = 0
            for (linea in lineas.drop(1)) {
                val p = linea.split(",")

                val fechaTxt = campo(p, "Date")
                val local = campo(p, "Home")
                val visita = campo(p, "Away")
                val hg = campo(p, "HG").toIntOrNull()
                val ag = campo(p, "AG").toIntOrNull()

                // Si falta lo esencial, se salta la fila y se cuenta.
                // Nunca inventar un dato faltante.
                if (hg == null || ag == null || fechaTxt.isEmpty() || local.isEmpty()) {
                    saltadas++
                    continue
                }

                val fecha = LocalDate.parse(fechaTxt, FMT_FECHA)
                val hora = runCatching { LocalTime.parse(campo(p, "Time")) }
                    .getOrDefault(LocalTime.of(15, 0))
                val instante = fecha.atTime(hora).atZone(f.zona).toInstant()
                val season = campo(p, "Season").toIntOrNull() ?: fecha.year

                val fid = idDeterminista("${f.ligaId}|$season|$fechaTxt|$local|$visita")

                psPartido.setInt(1, fid)
                psPartido.setInt(2, f.ligaId)
                psPartido.setInt(3, season)
                psPartido.setTimestamp(4, Timestamp.from(instante))
                psPartido.setString(5, local)
                psPartido.setString(6, visita)
                psPartido.setInt(7, hg)
                psPartido.setInt(8, ag)
                psPartido.executeUpdate()
                partidos++
                enArchivo++

                // Dos juegos de cuotas de cierre por partido: la media del mercado
                // y la maxima. La media sirve para medir si hay valor de verdad;
                // la maxima es la que realmente podrias haber apostado.
                val juegos = listOf(
                    "FD-Avg" to listOf("AvgCH", "AvgCD", "AvgCA"),
                    "FD-Max" to listOf("MaxCH", "MaxCD", "MaxCA")
                )
                for ((etiqueta, claves) in juegos) {
                    val c = claves.map { campo(p, it).toDoubleOrNull() }
                    if (c.any { it == null }) continue   // sin cuota completa, no se guarda
                    psCuota.setInt(1, fid)
                    psCuota.setTimestamp(2, Timestamp.from(instante))
                    psCuota.setString(3, etiqueta)
                    psCuota.setDouble(4, c[0]!!)
                    psCuota.setDouble(5, c[1]!!)
                    psCuota.setDouble(6, c[2]!!)
                    psCuota.executeUpdate()
                    cuotas++
                }
            }

            con.commit()
            println("${f.nombre} -> $enArchivo partidos")
        }
    }

    println("\nTotal: $partidos partidos, $cuotas filas de cuotas, $saltadas lineas saltadas")
}