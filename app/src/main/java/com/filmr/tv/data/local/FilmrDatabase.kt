package com.filmr.tv.data.local

import android.content.Context
import androidx.room.*
import com.filmr.tv.data.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Dao
interface FilmrDao {
    @Query("SELECT * FROM addons ORDER BY orderIndex ASC")
    fun observeAddons(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons WHERE isEnabled = 1 ORDER BY orderIndex ASC")
    suspend fun getEnabledAddons(): List<AddonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: AddonEntity)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteAddon(id: String)

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE `key` = :key")
    suspend fun deleteProgress(key: String)

    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(history: WatchHistoryEntity)

    @Query("SELECT * FROM library ORDER BY addedAt DESC")
    fun observeLibrary(): Flow<List<LibraryEntity>>

    @Query("SELECT * FROM library")
    suspend fun getAllLibrary(): List<LibraryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryItem(item: LibraryEntity)

    @Query("DELETE FROM library WHERE id = :id")
    suspend fun deleteLibraryItem(id: String)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchQuery(entry: SearchHistoryEntity)

    @Query("SELECT * FROM user_session WHERE id = 1")
    suspend fun getSession(): UserSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: UserSessionEntity)

    @Query("DELETE FROM user_session")
    suspend fun clearSession()
}

@Database(
    entities = [
        AddonEntity::class,
        WatchProgressEntity::class,
        WatchHistoryEntity::class,
        LibraryEntity::class,
        SearchHistoryEntity::class,
        UserSessionEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class FilmrDatabase : RoomDatabase() {
    abstract fun dao(): FilmrDao

    companion object {
        @Volatile private var INSTANCE: FilmrDatabase? = null
        fun get(context: Context): FilmrDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                FilmrDatabase::class.java,
                "filmr_nuvio_tv.db"
            ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}

object CatalogCacheStore {
    private const val PREF_NAME = "filmr_fast_cache"
    private const val KEY_ROWS = "cached_catalog_rows"
    private const val KEY_SETTINGS = "app_settings_json"

    fun saveCatalogRows(context: Context, rows: List<AddonCatalogRow>) {
        if (rows.isEmpty()) return
        try {
            val arr = JSONArray()
            rows.take(16).forEach { row ->
                val rObj = JSONObject().apply {
                    put("addonId", row.addonId)
                    put("addonName", row.addonName)
                    put("catalogId", row.catalogId)
                    put("title", row.title)
                    put("lang", row.lang)
                }
                val itemsArr = JSONArray()
                row.items.take(20).forEach { m ->
                    itemsArr.put(JSONObject().apply {
                        put("id", m.id)
                        put("imdbId", m.imdbId)
                        put("tmdbId", m.tmdbId)
                        put("type", m.type)
                        put("titleAr", m.titleAr)
                        put("titleEn", m.titleEn)
                        put("posterUrl", m.posterUrl)
                        put("backdropUrl", m.backdropUrl)
                        put("logoUrl", m.logoUrl)
                        put("overviewAr", m.overviewAr)
                        put("overviewEn", m.overviewEn)
                        put("year", m.year)
                        put("rating", m.rating)
                        put("runtimeMinutes", m.runtimeMinutes)
                        put("sourceAddon", m.sourceAddon)
                    })
                }
                rObj.put("items", itemsArr)
                arr.put(rObj)
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_ROWS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun loadCatalogRows(context: Context): List<AddonCatalogRow> {
        return try {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ROWS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val result = mutableListOf<AddonCatalogRow>()
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                val itemsArr = r.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<MediaItem>()
                for (j in 0 until itemsArr.length()) {
                    val m = itemsArr.getJSONObject(j)
                    items.add(MediaItem(
                        id = m.optString("id"),
                        imdbId = m.optString("imdbId"),
                        tmdbId = m.optInt("tmdbId"),
                        type = m.optString("type", "movie"),
                        titleAr = m.optString("titleAr"),
                        titleEn = m.optString("titleEn"),
                        posterUrl = m.optString("posterUrl"),
                        backdropUrl = m.optString("backdropUrl"),
                        logoUrl = m.optString("logoUrl"),
                        overviewAr = m.optString("overviewAr"),
                        overviewEn = m.optString("overviewEn"),
                        year = m.optString("year"),
                        rating = m.optDouble("rating", 0.0),
                        runtimeMinutes = m.optInt("runtimeMinutes", 0),
                        sourceAddon = m.optString("sourceAddon")
                    ))
                }
                if (items.isNotEmpty()) {
                    result.add(AddonCatalogRow(
                        addonId = r.optString("addonId"),
                        addonName = r.optString("addonName"),
                        catalogId = r.optString("catalogId"),
                        title = r.optString("title"),
                        lang = r.optString("lang", "ar"),
                        items = items
                    ))
                }
            }
            result
        } catch (_: Exception) { emptyList() }
    }

    fun saveSettings(context: Context, s: AppSettings) {
        try {
            val o = JSONObject().apply {
                put("tmdbEnabled", s.tmdbEnabled)
                put("tmdbApiKey", s.tmdbApiKey)
                put("tmdbLanguage", s.tmdbLanguage)
                put("tmdbArtwork", s.tmdbArtwork)
                put("tmdbBasicInfo", s.tmdbBasicInfo)
                put("tmdbCast", s.tmdbCast)
                put("tmdbCompanies", s.tmdbCompanies)
                put("tmdbNetworks", s.tmdbNetworks)
                put("tmdbEpisodes", s.tmdbEpisodes)
                put("tmdbTrailers", s.tmdbTrailers)
                put("tmdbRecommendations", s.tmdbRecommendations)
                put("tmdbCollections", s.tmdbCollections)
                put("mdblistApiKey", s.mdblistApiKey)
                put("mdblistImdb", s.mdblistImdb)
                put("mdblistRt", s.mdblistRt)
                put("mdblistMetacritic", s.mdblistMetacritic)
                put("mdblistLetterboxd", s.mdblistLetterboxd)
                put("mdblistMal", s.mdblistMal)
                put("animeSkipClientId", s.animeSkipClientId)
                put("animeSkipEnabled", s.animeSkipEnabled)
                put("preferEnglishTitles", s.preferEnglishTitles)
                put("amoledBlack", s.amoledBlack)
                put("showContinueWatching", s.showContinueWatching)
                put("autoPlayNext", s.autoPlayNext)
                put("subtitleSizeSp", s.subtitleSizeSp.toDouble())
                put("subtitleBold", s.subtitleBold)
                put("subtitleColorHex", s.subtitleColorHex)
                put("subtitleBgOpacity", s.subtitleBgOpacity.toDouble())
                put("subtitleOutline", s.subtitleOutline)
                put("subtitleBottomPaddingDp", s.subtitleBottomPaddingDp.toDouble())
            }
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SETTINGS, o.toString()).apply()
        } catch (_: Exception) {}
    }

    fun loadSettings(context: Context): AppSettings {
        return try {
            val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SETTINGS, null) ?: return AppSettings()
            val o = JSONObject(raw)
            AppSettings(
                tmdbEnabled = o.optBoolean("tmdbEnabled", true),
                tmdbApiKey = o.optString("tmdbApiKey", "439c478a771f35c05022d9feabcca01c"),
                tmdbLanguage = o.optString("tmdbLanguage", "ar-SA"),
                tmdbArtwork = o.optBoolean("tmdbArtwork", true),
                tmdbBasicInfo = o.optBoolean("tmdbBasicInfo", true),
                tmdbCast = o.optBoolean("tmdbCast", true),
                tmdbCompanies = o.optBoolean("tmdbCompanies", true),
                tmdbNetworks = o.optBoolean("tmdbNetworks", true),
                tmdbEpisodes = o.optBoolean("tmdbEpisodes", true),
                tmdbTrailers = o.optBoolean("tmdbTrailers", true),
                tmdbRecommendations = o.optBoolean("tmdbRecommendations", true),
                tmdbCollections = o.optBoolean("tmdbCollections", true),
                mdblistApiKey = o.optString("mdblistApiKey", ""),
                mdblistImdb = o.optBoolean("mdblistImdb", true),
                mdblistRt = o.optBoolean("mdblistRt", true),
                mdblistMetacritic = o.optBoolean("mdblistMetacritic", true),
                mdblistLetterboxd = o.optBoolean("mdblistLetterboxd", true),
                mdblistMal = o.optBoolean("mdblistMal", true),
                animeSkipClientId = o.optString("animeSkipClientId", ""),
                animeSkipEnabled = o.optBoolean("animeSkipEnabled", true),
                preferEnglishTitles = o.optBoolean("preferEnglishTitles", false),
                amoledBlack = o.optBoolean("amoledBlack", false),
                showContinueWatching = o.optBoolean("showContinueWatching", true),
                autoPlayNext = o.optBoolean("autoPlayNext", true),
                subtitleSizeSp = o.optDouble("subtitleSizeSp", 22.0).toFloat(),
                subtitleBold = o.optBoolean("subtitleBold", true),
                subtitleColorHex = o.optLong("subtitleColorHex", 0xFFFFFFFF),
                subtitleBgOpacity = o.optDouble("subtitleBgOpacity", 0.55).toFloat(),
                subtitleOutline = o.optBoolean("subtitleOutline", true),
                subtitleBottomPaddingDp = o.optDouble("subtitleBottomPaddingDp", 36.0).toFloat()
            )
        } catch (_: Exception) { AppSettings() }
    }
}
