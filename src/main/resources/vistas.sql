-- ============================================================
--  vistas.sql - solo las vistas
--  Se puede correr cuantas veces se quiera: las vistas no
--  guardan datos, solo son consultas con nombre.
--      \i 'C:/Users/usuario/IdeaProjects/BasesDatosDeportivas/src/main/resources/vistas.sql'
-- ============================================================

DROP VIEW IF EXISTS v_media_liga CASCADE;

CREATE VIEW v_media_liga AS
SELECT source,
       league_id,
       season,
       ROUND(AVG(home_goals), 2) AS media_gol_local,
       ROUND(AVG(away_goals), 2) AS media_gol_visita,
       COUNT(*)                  AS partidos
FROM matches
WHERE match_status = 'FT' AND home_goals IS NOT NULL
GROUP BY source, league_id, season;


DROP VIEW IF EXISTS v_form_equipos CASCADE;

CREATE VIEW v_form_equipos AS
WITH jugados AS (
    SELECT *
    FROM matches
    WHERE match_status = 'FT' AND home_goals IS NOT NULL
),
     casa AS (
         SELECT j.source,
                j.league_id,
                j.season,
                j.home_team AS equipo,
                COUNT(*) AS p_casa,
                ROUND(AVG(j.home_goals), 2) AS gf_casa,
                ROUND(AVG(j.away_goals), 2) AS gc_casa,
                ROUND(AVG(s.corners), 1) AS corners_casa
         FROM jugados j
                  LEFT JOIN match_statistics s
                            ON s.fixture_id = j.fixture_id
                                AND s.is_home = TRUE
         GROUP BY j.source, j.league_id, j.season, j.home_team
     ),
     fuera AS (
         SELECT j.source,
                j.league_id,
                j.season,
                j.away_team AS equipo,
                COUNT(*) AS p_fuera,
                ROUND(AVG(j.away_goals), 2) AS gf_fuera,
                ROUND(AVG(j.home_goals), 2) AS gc_fuera,
                ROUND(AVG(s.corners), 1) AS corners_fuera
         FROM jugados j
                  LEFT JOIN match_statistics s
                            ON s.fixture_id = j.fixture_id
                                AND s.is_home = FALSE
         GROUP BY j.source, j.league_id, j.season, j.away_team
     )
SELECT COALESCE(c.source, f.source)       AS source,
       COALESCE(c.league_id, f.league_id) AS league_id,
       COALESCE(c.season, f.season)       AS season,
       COALESCE(c.equipo, f.equipo)       AS equipo,
       c.p_casa,  c.gf_casa,  c.gc_casa,  c.corners_casa,
       f.p_fuera, f.gf_fuera, f.gc_fuera, f.corners_fuera
FROM casa c
         FULL JOIN fuera f
                   ON c.equipo    = f.equipo
                       AND c.league_id = f.league_id
                       AND c.season    = f.season
                       AND c.source    = f.source;


DROP VIEW IF EXISTS v_tabla CASCADE;

CREATE VIEW v_tabla AS
SELECT league_id,
       season,
       team_name,
       posicion,
       points,
       form,
       description,
       pj_casa,
       ROUND(gf_casa::numeric  / NULLIF(pj_casa, 0),  2) AS gf_casa_prom,
       ROUND(gc_casa::numeric  / NULLIF(pj_casa, 0),  2) AS gc_casa_prom,
       pj_fuera,
       ROUND(gf_fuera::numeric / NULLIF(pj_fuera, 0), 2) AS gf_fuera_prom,
       ROUND(gc_fuera::numeric / NULLIF(pj_fuera, 0), 2) AS gc_fuera_prom
FROM standings;


DROP VIEW IF EXISTS v_ultima_cuota CASCADE;

CREATE VIEW v_ultima_cuota AS
SELECT DISTINCT ON (fixture_id) *
        FROM match_odds
        ORDER BY fixture_id, captured_at DESC;