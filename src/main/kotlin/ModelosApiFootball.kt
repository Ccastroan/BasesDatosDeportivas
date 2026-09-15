import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class FixturesResponse(
    val response: List<FixtureData>?
)
data class FixtureData(
    val fixture: FixtureInfo,
    val league: LeagueInfo,
    val teams: TeamsInfo,
    val goals: GoalsInfo,
)

data class FixtureInfo(
    val id: Int,
    val date: String,
    val status: StatusInfo
)

data class StatusInfo(val short: String)

data class LeagueInfo(
    val id: Int,
    val season: Int,
    val round: String?
)
data class TeamsInfo(val home: TeamInfo, val away: TeamInfo)
data class TeamInfo(val id: Int, val name: String)
data class GoalsInfo(val home: Int?, val away: Int?)

data class StatsResponse(
    val response: List<TeamStats>?
)
data class TeamStats(
    val team: TeamInfo,
    val statistics: List<StatItem>?
)

data class StatItem(
    val type: String,
    val value: JsonElement?
)


// ---------- /standings ----------
data class StandingsResponse(
    val response: List<StandingsWrap>?
)

data class StandingsWrap(val league: StandingsLeague)

data class StandingsLeague(
    val id: Int,
    val season: Int,
    val standings: List<List<StandingRow>>?   // lista DE listas: la API agrupa
)

data class StandingRow(
    val rank: Int,
    val team: TeamInfo,
    val points: Int,
    val goalsDiff: Int?,
    val form: String?,
    val description: String?,
    val all: StatBlock?,
    val home: StatBlock?,
    val away: StatBlock?
)

data class StatBlock(
    val played: Int?,
    val win: Int?,
    val draw: Int?,
    val lose: Int?,
    val goals: GoalsFA?
)

data class GoalsFA(
    @SerializedName("for") val favor: Int?,   // "for" es palabra reservada en Kotlin
    val against: Int?
)