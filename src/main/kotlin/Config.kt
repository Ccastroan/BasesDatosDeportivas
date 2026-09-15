import java.sql.Connection
import java.sql.DriverManager

object Config {
    // Las tres propiedades se llaman IGUAL que sus variables de entorno.
    // Asi no hay forma de confundirse entre el nombre del codigo y el del sistema.
    val APIFOOTBALL_KEY: String by lazy {
        System.getenv("APIFOOTBALL_KEY") ?: error("Falta la variable de entorno APIFOOTBALL_KEY")
    }
    val GEMINI_API_KEY: String by lazy {
        System.getenv("GEMINI_API_KEY") ?: error("Falta la variable de entorno GEMINI_API_KEY")
    }
    private val DB_PASS: String by lazy {
        System.getenv("DB_PASS") ?: error("Falta la variable de entorno DB_PASS")
    }

    const val API_BASE = "https://v3.football.api-sports.io"
    const val MODELO_GEMINI = "gemini-3.8-flash"

    private const val URL_DB = "jdbc:postgresql://localhost:5433/apuestas_db"
    private const val USER_DB = "postgres"

    val LIGAS = mapOf(
        113 to "Allsvenskan",
        103 to "Eliteserien",
        72  to "Serie B Brasil"
    )
    val TEMPORADAS = listOf(2024, 2025, 2026)

    fun conectar(): Connection = DriverManager.getConnection(URL_DB, USER_DB, DB_PASS)
}