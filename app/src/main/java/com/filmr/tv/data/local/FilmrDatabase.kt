package com.filmr.tv.data.local

import android.content.Context
import androidx.room.*
import com.filmr.tv.data.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Dao
interface FilmrDao {
    @Query("SELECT * FROM addons ORDER BY name ASC")
    fun observeAddons(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons")
    suspend fun getAllAddons(): List<AddonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: AddonEntity)

    @Query("UPDATE addons SET isEnabled = :enabled WHERE id = :id")
    suspend fun setAddonEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteAddon(id: String)

    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT 20")
    fun observeWatchProgress(): Flow<List<WatchProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchProgress(progress: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE mediaKey = :mediaKey")
    suspend fun deleteWatchProgress(mediaKey: String)

    @Query("SELECT * FROM library ORDER BY addedAt DESC")
    fun observeLibrary(): Flow<List<LibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibrary(item: LibraryEntity)

    @Query("DELETE FROM library WHERE mediaId = :mediaId")
    suspend fun deleteLibrary(mediaId: String)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [
        AddonEntity::class,
        WatchProgressEntity::class,
        LibraryEntity::class,
        SearchHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FilmrDatabase : RoomDatabase() {
    abstract fun dao(): FilmrDao

    companion object {
        @Volatile
        private var INSTANCE: FilmrDatabase? = null

        fun getInstance(context: Context): FilmrDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FilmrDatabase::class.java,
                    "filmr_tv_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}

class CatalogCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("filmr_cache_prefs", Context.MODE_PRIVATE)

    fun saveSettings(s: AppSettings) {
        val obj = JSONObject().apply {
            put("tmdbEnabled", s.tmdbEnabled)
            put("tmdbApplyHome", s.tmdbApplyHome)
            put("tmdbApplyContinue", s.tmdbApplyContinue)
            put("tmdbLanguage", s.tmdbLanguage)
            put("tmdbArtwork", s.tmdbArtwork)
            put("tmdbBasicInfo", s.tmdbBasicInfo)
            put("tmdbDetails", s.tmdbDetails)
            put("tmdbCast", s.tmdbCast)
            put("tmdbCompanies", s.tmdbCompanies)
            put("tmdbNetworks", s.tmdbNetworks)
            put("tmdbEpisodes", s.tmdbEpisodes)
            put("tmdbTrailers", s.tmdbTrailers)
            put("tmdbRecommendations", s.tmdbRecommendations)
            put("autoTranslateArabic", s.autoTranslateArabic)
            put("mdbListEnabled", s.mdbListEnabled)
            put("mdbListApiKey", s.mdbListApiKey)
            put("mdbShowOnHero", s.mdbShowOnHero)
            put("showImdbRating", s.showImdbRating)
            put("showTmdbRating", s.showTmdbRating)
            put("showRtRating", s.showRtRating)
            put("showAudienceRating", s.showAudienceRating)
            put("showMcRating", s.showMcRating)
            put("showLbRating", s.showLbRating)
            put("showMalRating", s.showMalRating)
            put("animeSkipEnabled", s.animeSkipEnabled)
            put("animeSkipClientId", s.animeSkipClientId)
            put("amoledDarkMode", s.amoledDarkMode)
            put("preferEnglishTitles", s.preferEnglishTitles)
            put("showContinueWatching", s.showContinueWatching)
            put("autoPlayNextEpisode", s.autoPlayNextEpisode)
            put("subtitleFontPercent", s.subtitleFontPercent)
            put("subtitleBold", s.subtitleBold)
            put("subtitleItalic", s.subtitleItalic)
            put("subtitleColorHex", s.subtitleColorHex)
            put("subtitleTextOpacity", s.subtitleTextOpacity)
            put("subtitleBgOpacity", s.subtitleBgOpacity)
            put("subtitleOutline", s.subtitleOutline)
            put("subtitleDelayMs", s.subtitleDelayMs)
        }
        prefs.edit().putString("settings_json", obj.toString()).apply()
    }

    fun loadSettings(): AppSettings {
        val raw = prefs.getString("settings_json", null) ?: return AppSettings()
        return try {
            val o = JSONObject(raw)
            AppSettings(
                tmdbEnabled = o.optBoolean("tmdbEnabled", true),
                tmdbApplyHome = o.optBoolean("tmdbApplyHome", true),
                tmdbApplyContinue = o.optBoolean("tmdbApplyContinue", true),
                tmdbLanguage = o.optString("tmdbLanguage", "ar-SA"),
                tmdbArtwork = o.optBoolean("tmdbArtwork", true),
                tmdbBasicInfo = o.optBoolean("tmdbBasicInfo", true),
                tmdbDetails = o.optBoolean("tmdbDetails", true),
                tmdbCast = o.optBoolean("tmdbCast", true),
                tmdbCompanies = o.optBoolean("tmdbCompanies", true),
                tmdbNetworks = o.optBoolean("tmdbNetworks", true),
                tmdbEpisodes = o.optBoolean("tmdbEpisodes", true),
                tmdbTrailers = o.optBoolean("tmdbTrailers", true),
                tmdbRecommendations = o.optBoolean("tmdbRecommendations", true),
                autoTranslateArabic = o.optBoolean("autoTranslateArabic", true),
                mdbListEnabled = o.optBoolean("mdbListEnabled", true),
                mdbListApiKey = o.optString("mdbListApiKey", ""),
                mdbShowOnHero = o.optBoolean("mdbShowOnHero", true),
                showImdbRating = o.optBoolean("showImdbRating", true),
                showTmdbRating = o.optBoolean("showTmdbRating", true),
                showRtRating = o.optBoolean("showRtRating", true),
                showAudienceRating = o.optBoolean("showAudienceRating", true),
                showMcRating = o.optBoolean("showMcRating", true),
                showLbRating = o.optBoolean("showLbRating", true),
                showMalRating = o.optBoolean("showMalRating", true),
                animeSkipEnabled = o.optBoolean("animeSkipEnabled", false),
                animeSkipClientId = o.optString("animeSkipClientId", ""),
                amoledDarkMode = o.optBoolean("amoledDarkMode", false),
                preferEnglishTitles = o.optBoolean("preferEnglishTitles", false),
                showContinueWatching = o.optBoolean("showContinueWatching", true),
                autoPlayNextEpisode = o.optBoolean("autoPlayNextEpisode", true),
                subtitleFontPercent = o.optInt("subtitleFontPercent", 100),
                subtitleBold = o.optBoolean("subtitleBold", true),
                subtitleItalic = o.optBoolean("subtitleItalic", false),
                subtitleColorHex = o.optLong("subtitleColorHex", 0xFFFFFFFF),
                subtitleTextOpacity = o.optInt("subtitleTextOpacity", 100),
                subtitleBgOpacity = o.optInt("subtitleBgOpacity", 40),
                subtitleOutline = o.optBoolean("subtitleOutline", true),
                subtitleDelayMs = o.optLong("subtitleDelayMs", 0L)
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    fun saveCatalogRows(rows: List<AddonCatalogRow>) {
        val arr = JSONArray()
        rows.forEach { row ->
            val rObj = JSONObject().apply {
                put("addonId", row.addonId)
                put("addonName", row.addonName)
                put("catalogId", row.catalogId)
                put("title", row.title)
                put("language", row.language)
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
                        put("year", m.year)
                        put("rating", m.rating)
                        put("imdbRating", m.imdbRating)
                        put("tmdbRating", m.tmdbRating)
                        put("sourceAddon", m.sourceAddon)
                    })
                }
                put("items", itemsArr)
            }
            arr.put(rObj)
        }
        prefs.edit().putString("cached_rows_json", arr.toString()).apply()
    }

    fun loadCatalogRows(): List<AddonCatalogRow> {
        val raw = prefs.getString("cached_rows_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<AddonCatalogRow>()
            for (i in 0 until arr.length()) {
                val rObj = arr.getJSONObject(i)
                val itemsArr = rObj.optJSONArray("items") ?: continue
                val items = mutableListOf<MediaItem>()
                for (j in 0 until itemsArr.length()) {
                    val m = itemsArr.getJSONObject(j)
                    items.add(
                        MediaItem(
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
                            year = m.optString("year"),
                            rating = m.optDouble("rating", 0.0),
                            imdbRating = m.optString("imdbRating"),
                            tmdbRating = m.optString("tmdbRating"),
                            sourceAddon = m.optString("sourceAddon")
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    result.add(
                        AddonCatalogRow(
                            addonId = rObj.optString("addonId"),
                            addonName = rObj.optString("addonName"),
                            catalogId = rObj.optString("catalogId"),
                            title = rObj.optString("title"),
                            language = rObj.optString("language", "ar"),
                            items = items
                        )
                    )
                }
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}
