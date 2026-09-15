-- ============================================================
--  consultas.sql - consultas de verificacion y analisis
--  Se corre desde psql, conectado a apuestas_db:
--      \i 'C:/Users/usuario/IdeaProjects/BasesDatosDeportivas/src/main/resources/consultas.sql'
--  Correrlo completo imprime un informe del estado de la base.
--  Tambien se puede copiar una consulta sola.
-- ============================================================


-- 1. Que hay cargado, por fuente
SELECT source, COUNT(*) AS partidos
FROM matches
GROUP BY source
ORDER BY source;


-- 2. CONTROL DE CALIDAD: debe dar 0.
-- Un partido no jugado no puede tener goles. Si da mas de 0,
-- algun cargador esta escribiendo 0 en vez de NULL y los
-- promedios van a salir mal.
SELECT COUNT(*) AS partidos_sin_jugar_con_goles
FROM matches
WHERE match_status = 'NS' AND home_goals IS NOT NULL;


-- 3. Partidos por liga y temporada
-- 113 = Allsvenskan, 103 = Eliteserien, 72 = Brasileirao Serie B
SELECT league_id, season, match_status, COUNT(*) AS n
FROM matches
GROUP BY 1, 2, 3
ORDER BY 1, 2, 3;


-- 4. Cuotas guardadas por casa
-- El promedio de FD-Max debe ser mayor que el de FD-Avg:
-- la cuota maxima del mercado siempre paga mas que la media.
SELECT bookmaker,
       COUNT(*) AS filas,
       ROUND(AVG(cuota_local), 2)  AS prom_local,
       ROUND(AVG(cuota_empate), 2) AS prom_empate,
       ROUND(AVG(cuota_visita), 2) AS prom_visita
FROM match_odds
GROUP BY bookmaker
ORDER BY bookmaker;


-- 5. Media de goles por liga y temporada
-- Es el ancla del modelo: la tasa base a la que se regresa
-- cuando un equipo tiene pocos partidos jugados.
SELECT * FROM v_media_liga ORDER BY league_id, season;


-- 6. Rendimiento por equipo, separando casa y fuera
-- Cambiar el season segun lo que se quiera mirar.
SELECT equipo, p_casa, gf_casa, gc_casa, p_fuera, gf_fuera, gc_fuera
FROM v_form_equipos
WHERE league_id = 113 AND season = 2026
ORDER BY equipo;


-- 7. Historial directo entre dos equipos, desde los propios datos.
-- No gasta ni un request de API. Cambiar los nombres segun la fuente:
-- football-data usa "Hacken", API-Football usa "BK Hacken".
SELECT match_date::date AS fecha, home_team, home_goals, away_goals, away_team,
       (home_goals + away_goals) AS goles_totales
FROM matches
WHERE match_status = 'FT'
  AND ((home_team = 'Hacken'   AND away_team = 'Malmo FF')
    OR (home_team = 'Malmo FF' AND away_team = 'Hacken'))
ORDER BY match_date DESC
    LIMIT 6;


-- 8. QUE TAN BIEN PREDICE EL MERCADO
-- Compara lo que la casa decia que iba a pasar contra lo que paso,
-- agrupado por tramo de cuota, sobre miles de partidos.
-- Dos lecturas: el orden de la columna real confirma que el mercado
-- rankea bien, y la diferencia entre las dos ultimas columnas es el
-- margen de la casa, o sea de donde sale su ganancia.
SELECT CASE
           WHEN o.cuota_local < 1.5 THEN '1.00-1.50'
           WHEN o.cuota_local < 2.0 THEN '1.50-2.00'
           WHEN o.cuota_local < 2.5 THEN '2.00-2.50'
           WHEN o.cuota_local < 3.5 THEN '2.50-3.50'
           ELSE '3.50+'
           END AS tramo_cuota,
       COUNT(*) AS partidos,
       ROUND(AVG(CASE WHEN m.home_goals > m.away_goals THEN 1.0 ELSE 0.0 END) * 100, 1) AS gano_local_real,
       ROUND(AVG(100.0 / o.cuota_local), 1) AS prob_implicita
FROM matches m
         JOIN match_odds o ON o.fixture_id = m.fixture_id AND o.bookmaker = 'FD-Avg'
WHERE m.source = 'footballdata' AND m.home_goals IS NOT NULL
GROUP BY 1
ORDER BY 1;


-- 9. Partidos jugados a los que les faltan estadisticas
-- Lo usara el cargador de corners: 90 por corrida para no pasarse
-- del limite diario de 100 requests del plan gratuito.
SELECT fixture_id, home_team, away_team
FROM matches
WHERE match_status = 'FT' AND stats_checked_at IS NULL
ORDER BY match_date DESC
    LIMIT 90;