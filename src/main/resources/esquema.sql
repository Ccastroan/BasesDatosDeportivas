-- ============================================================
--  BasesDatosDeportivas - esquema completo
--  ARCHIVO: src/main/resources/esquema.sql   (se edita en IntelliJ)
--  SE EJECUTA EN: psql, conectado a apuestas_db, con:
--      \i 'C:/Users/usuario/IdeaProjects/BasesDatosDeportivas/src/main/resources/esquema.sql'
--
--  Alcance: Allsvenskan (113), Eliteserien (103), Brasileirao Serie B (72)
--  En una corrida limpia imprime 19 tags y NINGUN error.
--  Los avisos "NOTICE ... no existe, omitiendo" son normales.
-- ============================================================


-- ---------- 1. Limpieza ----------
-- CASCADE va ANTES del punto y coma, dentro de la misma orden.

DROP VIEW  IF EXISTS v_ultima_cuota CASCADE;
DROP VIEW  IF EXISTS v_tabla CASCADE;
DROP VIEW  IF EXISTS v_form_equipos CASCADE;
DROP VIEW  IF EXISTS v_media_liga CASCADE;

DROP TABLE IF EXISTS standings CASCADE;
DROP TABLE IF EXISTS match_odds CASCADE;
DROP TABLE IF EXISTS match_statistics CASCADE;
DROP TABLE IF EXISTS matches CASCADE;


-- ---------- 2. matches: un registro por partido ----------
-- home_goals y away_goals quedan NULL mientras el partido no se juegue.
-- Nunca poner 0: AVG() ignora los NULL, pero promedia los 0.
-- stats_checked_at marca que ya se pidieron las estadisticas de ese partido.

CREATE TABLE matches (
                         fixture_id        INTEGER PRIMARY KEY,
                         league_id         INTEGER NOT NULL,
                         season            INTEGER NOT NULL,
                         match_date        TIMESTAMPTZ NOT NULL,
                         round             VARCHAR(100),
                         home_team         VARCHAR(150) NOT NULL,
                         away_team         VARCHAR(150) NOT NULL,
                         home_goals        INTEGER,
                         away_goals        INTEGER,
                         match_status      VARCHAR(20) NOT NULL,
                         stats_checked_at  TIMESTAMPTZ,
                         source           VARCHAR(20) DEFAULT 'apifootball'
                         last_updated      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                         CONSTRAINT chk_ligas_objetivo CHECK (league_id IN (113, 103, 72))
);

CREATE INDEX idx_matches_liga   ON matches (league_id, season, match_date);
CREATE INDEX idx_matches_status ON matches (match_status);

CREATE INDEX idx_matches_stats_pend ON matches (match_date DESC)
    WHERE match_status = 'FT' AND stats_checked_at IS NULL;


-- ---------- 3. match_statistics: dos filas por partido ----------
-- Sin DEFAULT 0: si la API no entrega el dato, queda NULL.

CREATE TABLE match_statistics (
                                  fixture_id          INTEGER NOT NULL,
                                  team_name           VARCHAR(150) NOT NULL,
                                  team_id             INTEGER,
                                  is_home             BOOLEAN NOT NULL,
                                  shots_total         INTEGER,
                                  shots_on_target     INTEGER,
                                  corners             INTEGER,
                                  yellow_cards        INTEGER,
                                  possession_percent  INTEGER,
                                  PRIMARY KEY (fixture_id, team_name),
                                  FOREIGN KEY (fixture_id) REFERENCES matches (fixture_id) ON DELETE CASCADE
);


-- ---------- 4. match_odds: una fila por captura ----------
-- captured_at va en la PK para guardar varias capturas del mismo partido.
-- Las cuotas desaparecen despues del partido: lo que no se guarda, se pierde.

CREATE TABLE match_odds (
                            fixture_id     INTEGER NOT NULL,
                            captured_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            bookmaker      VARCHAR(60) NOT NULL,
                            cuota_local    NUMERIC(6,2),
                            cuota_empate   NUMERIC(6,2),
                            cuota_visita   NUMERIC(6,2),
                            cuota_over25   NUMERIC(6,2),
                            cuota_under25  NUMERIC(6,2),
                            cuota_btts_si  NUMERIC(6,2),
                            cuota_btts_no  NUMERIC(6,2),
                            PRIMARY KEY (fixture_id, bookmaker,  captured_at ),
                            FOREIGN KEY (fixture_id) REFERENCES matches (fixture_id) ON DELETE CASCADE
);


-- ---------- 5. standings: tabla de posiciones ----------

CREATE TABLE standings (
                           league_id    INTEGER NOT NULL,
                           season       INTEGER NOT NULL,
                           team_name    VARCHAR(150) NOT NULL,
                           team_id      INTEGER,
                           posicion     INTEGER,
                           points       INTEGER,
                           form         VARCHAR(15),
                           description  VARCHAR(150),
                           pj_total     INTEGER,
                           gf_total     INTEGER,
                           gc_total     INTEGER,
                           pj_casa      INTEGER,
                           gf_casa      INTEGER,
                           gc_casa      INTEGER,
                           pj_fuera     INTEGER,
                           gf_fuera     INTEGER,
                           gc_fuera     INTEGER,
                           updated_at   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                           PRIMARY KEY (league_id, season, team_name)
);
