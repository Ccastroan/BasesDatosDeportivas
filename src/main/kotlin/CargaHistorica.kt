import java.sql.Timestamp
import java.time.OffsetDateTime

fun main() {
    val sql = """
        INSERT INTO matches (fixture_id, league_id, season, match_date, round,
                             home_team, away_team, home_goals, away_goals, match_status)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (fixture_id) DO UPDATE SET
            home_goals   = EXCLUDED.home_goals,
            away_goals   = EXCLUDED.away_goals,
            match_status = EXCLUDED.match_status,
            match_date   = EXCLUDED.match_date,
            last_updated = CURRENT_TIMESTAMP
    """.trimIndent()

    var total = 0

    Config.conectar().use { con ->
        con.autoCommit = false
        val ps = con.prepareStatement(sql)

        for ((ligaId, nombreLiga) in Config.LIGAS) {
            for (temporada in Config.TEMPORADAS) {
                try {
                    val data = Api.get(
                        "/fixtures?league=$ligaId&season=$temporada",
                        FixturesResponse::class.java
                    )
                    val partidos = data?.response ?: emptyList()
                    println("$nombreLiga $temporada -> ${partidos.size} partidos")

                    for (p in partidos) {
                        ps.setInt(1, p.fixture.id)
                        ps.setInt(2, p.league.id)
                        ps.setInt(3, p.league.season)
                        ps.setTimestamp(4, Timestamp.from(
                            OffsetDateTime.parse(p.fixture.date).toInstant()))
                        ps.setString(5, p.league.round)
                        ps.setString(6, p.teams.home.name)
                        ps.setString(7, p.teams.away.name)

                        // NULL, no 0: un partido sin jugar no termino 0-0
                        ponerInt(ps, 8, p.goals.home)
                        ponerInt(ps, 9, p.goals.away)

                        ps.setString(10, p.fixture.status.short)
                        ps.addBatch()
                        total++
                    }

                    ps.executeBatch()
                    con.commit()
                    Thread.sleep(2000)   // no atropellar el rate limit

                } catch (e: Exception) {
                    con.rollback()
                    println("Error en $nombreLiga $temporada: ${e.message}")
                }
            }
        }
    }
    println("\nCarga terminada: $total partidos procesados")
}