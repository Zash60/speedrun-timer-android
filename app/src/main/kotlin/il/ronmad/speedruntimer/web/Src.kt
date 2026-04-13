package il.ronmad.speedruntimer.web

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import il.ronmad.speedruntimer.SRC_API
import il.ronmad.speedruntimer.toResult
import il.ronmad.speedruntimer.web.Result
import il.ronmad.speedruntimer.web.Success
import il.ronmad.speedruntimer.web.Failure
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.reflect.Type
import java.util.Locale
import kotlin.math.roundToLong

// ==================== API Interface ====================

interface SrcAPI {
    @POST("GetSearch")
    suspend fun searchGames(@Body request: SearchRequest): Response<SearchResponse>

    @POST("GetGameData")
    suspend fun getGameData(@Body request: GetGameDataRequest): Response<GameDataResponse>

    @POST("GetGameLeaderboard2")
    suspend fun getLeaderboard(@Body request: GetLeaderboardRequest): Response<LeaderboardResponse>

    @POST("GetUserSummary")
    suspend fun getUser(@Body request: GetUserRequest): Response<UserResponse>

    @POST("GetStaticData")
    suspend fun getStaticData(@Body request: EmptyRequest = EmptyRequest()): Response<StaticDataResponse>
}

// ==================== Request Models ====================

data class SearchRequest(
    val query: String,
    val includeGames: Boolean = true,
    val limit: Int = 50
)

data class GetGameDataRequest(
    val gameUrl: String? = null,
    val gameId: String? = null
)

data class GetLeaderboardRequest(
    val gameId: String,
    val categoryId: String,
    val video: Int = 0, // 0 = include runs without video
    val verified: Int = 1, // 1 = only verified runs
    val page: Int = 1
)

data class GetUserRequest(val url: String)
object EmptyRequest

// ==================== Response Models ====================

data class SearchResponse(
    val gameList: List<V2Game> = emptyList(),
    val platformList: List<V2Platform> = emptyList()
)

data class GameDataResponse(
    val game: V2Game? = null,
    val categories: List<V2Category> = emptyList(),
    val levels: List<V2Level> = emptyList(),
    val variables: List<V2Variable> = emptyList(),
    val values: List<V2Value> = emptyList(),
    val platforms: List<V2Platform> = emptyList(),
    val regions: List<V2Region> = emptyList()
)

data class LeaderboardResponse(
    val runList: List<V2Run> = emptyList(),
    val playerList: List<V2Player> = emptyList(),
    val platformList: List<V2Platform> = emptyList(),
    val pagination: V2Pagination? = null
)

data class UserResponse(
    val user: V2User? = null
)

data class StaticDataResponse(
    val platformList: List<V2Platform> = emptyList(),
    val regionList: List<V2Region> = emptyList()
)

// ==================== Data Models ====================

data class V2Game(
    val id: String,
    val name: String,
    val url: String,
    val coverPath: String? = null,
    val runCount: Int = 0
)

data class V2Category(
    val id: String,
    val name: String,
    val url: String,
    val gameId: String,
    val isMisc: Boolean = false,
    val isPerLevel: Boolean = false,
    val timeDirection: String = "asc"
)

data class V2Level(
    val id: String,
    val name: String,
    val gameId: String
)

data class V2Variable(
    val id: String,
    val name: String,
    val isSubcategory: Boolean = false,
    val categoryId: String? = null,
    val gameId: String
)

data class V2Value(
    val id: String,
    val name: String,
    val variableId: String,
    val rules: String? = null
)

data class V2Run(
    val id: String,
    val gameId: String,
    val categoryId: String,
    val levelId: String? = null,
    val time: Float? = null,
    val timeWithLoads: Float? = null,
    val platformId: String? = null,
    val emulator: Boolean = false,
    val video: String? = null,
    val date: Long = 0,
    val place: Int? = null,
    val playerIds: List<String> = emptyList(),
    val valueIds: List<String> = emptyList(),
    val obsolete: Boolean? = null
)

data class V2Player(
    val id: String,
    val name: String,
    val url: String? = null
)

data class V2Platform(
    val id: String,
    val name: String
)

data class V2Region(
    val id: String,
    val name: String
)

data class V2User(
    val id: String,
    val name: String,
    val url: String
)

data class V2Pagination(
    val count: Int = 0,
    val page: Int = 1,
    val pages: Int = 1,
    val per: Int = 100
)

// ==================== Legacy compatibility models ====================

data class SrcGame(
    val name: String,
    val categories: List<SrcCategory>,
    val links: List<SrcLink>,
    val id: String = ""
) {
    companion object {
        val EMPTY_GAME = SrcGame("", emptyList(), emptyList())
    }
}

data class SrcCategory(
    val name: String,
    val subCategories: List<SrcVariable>,
    val leaderboardUrl: String?,
    val id: String = ""
)

data class SrcLink(val rel: String?, val uri: String)

data class SrcLeaderboard(
    val weblink: String,
    val runs: List<SrcRun>,
    val id: String = ""
) {
    var categoryName = ""
    var subcategories: List<String> = listOf()
    var wrRunners = ""
    var wrPlatform = ""

    suspend fun initWrData(srcApi: SrcAPI) {
        if (runs.isEmpty()) return
        val wrRun = runs[0]
        wrRunners = wrRun.players.joinToString()
        wrPlatform = wrRun.platform ?: "[unknown]"
    }
}

data class SrcRun(
    val place: Int,
    val videoLink: SrcLink?,
    val players: List<String>,
    val time: Long,
    val platform: String?
)

data class SrcVariable(
    val id: String,
    val name: String,
    val values: List<SrcValue>
)

data class SrcValue(
    val id: String,
    val label: String
)

// ==================== Main API Client ====================

class Src private constructor() {

    private val gson = setupGson()
    private val api = setupApi()

    private var gameCache: MutableMap<String, SrcGame> = mutableMapOf()
    private var platformCache: MutableMap<String, String> = mutableMapOf()
    private var staticDataLoaded = false

    private fun setupGson(): Gson {
        return Gson()
    }

    private fun setupApi(): SrcAPI {
        return Retrofit.Builder()
            .addConverterFactory(GsonConverterFactory.create(gson))
            .baseUrl(SRC_API)
            .build()
            .create(SrcAPI::class.java)
    }

    private suspend fun ensureStaticData() {
        if (staticDataLoaded) return
        try {
            val response = api.getStaticData()
            if (response.isSuccessful) {
                response.body()?.platformList?.forEach {
                    platformCache[it.id] = it.name
                }
                staticDataLoaded = true
            }
        } catch (_: Exception) {
            // Ignore static data loading failures
        }
    }

    suspend fun fetchGameData(gameName: String): Result<SrcGame> {
        return gameCache.getOrElse(gameName) {
            try {
                // First search for the game to get its URL
                val searchResponse = api.searchGames(SearchRequest(query = gameName, includeGames = true, limit = 20))
                if (!searchResponse.isSuccessful || searchResponse.body() == null) {
                    return@getOrElse null
                }

                val matchingGame = searchResponse.body()!!.gameList.find {
                    it.name.lowercase(Locale.US) == gameName.lowercase(Locale.US)
                } ?: searchResponse.body()!!.gameList.firstOrNull()
                    ?: return@getOrElse null

                // Get full game data with categories
                val gameDataResponse = api.getGameData(GetGameDataRequest(gameUrl = matchingGame.url))
                if (!gameDataResponse.isSuccessful || gameDataResponse.body() == null || gameDataResponse.body()!!.game == null) {
                    return@getOrElse null
                }

                val gameData = gameDataResponse.body()!!
                val game = gameData.game!!

                // Build categories with variables
                val categories = gameData.categories.map { category ->
                    val subCategories = gameData.variables
                        .filter { it.isSubcategory && it.categoryId == category.id }
                        .map { variable ->
                            val variableValues = gameData.values
                                .filter { it.variableId == variable.id }
                                .map { SrcValue(it.id, it.name) }
                            SrcVariable(variable.id, variable.name, variableValues)
                        }

                    SrcCategory(
                        name = category.name,
                        subCategories = subCategories,
                        leaderboardUrl = null, // Will be set when fetching leaderboards
                        id = category.id
                    )
                }

                SrcGame(
                    name = game.name,
                    categories = categories,
                    links = listOf(SrcLink("self", game.url)),
                    id = game.id
                ).also { gameCache[gameName] = it }
            } catch (_: Exception) {
                null
            }
        }.toResult()
    }

    suspend fun fetchLeaderboardsForGame(gameName: String): Result<List<SrcLeaderboard>> {
        return when (val game = fetchGameData(gameName)) {
            is Success -> {
                try {
                    ensureStaticData()
                    val leaderboards = mutableListOf<SrcLeaderboard>()

                    for (category in game.value.categories) {
                        if (category.id.isEmpty()) continue

                        val leaderboardRequest = GetLeaderboardRequest(
                            gameId = game.value.id,
                            categoryId = category.id,
                            video = 0,
                            verified = 1
                        )

                        val response = api.getLeaderboard(leaderboardRequest)
                        if (!response.isSuccessful || response.body() == null) continue

                        val lbData = response.body()!!
                        if (lbData.runList.isEmpty()) continue

                        // Build player lookup
                        val playerLookup = lbData.playerList.associateBy { it.id }

                        // Convert runs
                        val runs = lbData.runList.mapNotNull { run ->
                            val players = run.playerIds.mapNotNull { playerId ->
                                playerLookup[playerId]?.name
                            }

                            val runTime = (run.time ?: run.timeWithLoads ?: 0f)
                            val platform = run.platformId?.let { platformCache[it] } ?: "[unknown]"

                            SrcRun(
                                place = run.place ?: 0,
                                videoLink = run.video?.let { SrcLink("video", it) },
                                players = players,
                                time = (runTime * 1000).roundToLong(),
                                platform = platform
                            )
                        }

                        val leaderboard = SrcLeaderboard(
                            weblink = "https://www.speedrun.com/${game.value.url}/${category.url}",
                            runs = runs,
                            id = category.id
                        ).apply {
                            categoryName = category.name

                            // Set subcategory info from run values
                            if (category.subCategories.isNotEmpty()) {
                                val wrRun = lbData.runList.firstOrNull()
                                if (wrRun != null && wrRun.valueIds.isNotEmpty()) {
                                    val valueLookup = lbData.runList
                                        .flatMap { it.valueIds }
                                        .distinct()
                                        .mapNotNull { valueId ->
                                            game.value.categories
                                                .flatMap { it.subCategories }
                                                .flatMap { it.values }
                                                .find { it.id == valueId }
                                        }
                                    subcategories = valueLookup.map { it.label }
                                }
                            }

                            initWrData(api)
                        }

                        leaderboards.add(leaderboard)
                    }

                    leaderboards.toResult()
                } catch (e: Exception) {
                    Failure
                }
            }
            is Failure -> Failure
        }
    }

    companion object {
        private val instance by lazy { Src() }
        operator fun invoke() = instance
    }
}
