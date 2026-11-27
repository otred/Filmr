package com.filmr.tv.data.local

import android.content.Context
import androidx.room.*
import com.filmr.tv.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmrDao {
    // Addons (Separated clearly by sourceType: stremio vs cloudstream)
    @Query("SELECT * FROM addons ORDER BY orderIndex ASC, name ASC")
    fun observeAllAddons(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM addons WHERE enabled = 1 ORDER BY orderIndex ASC")
    suspend fun getEnabledAddons(): List<AddonEntity>

    @Query("SELECT * FROM addons WHERE sourceType = :type AND enabled = 1 ORDER BY orderIndex ASC")
    suspend fun getEnabledAddonsByType(type: String): List<AddonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: AddonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddons(addons: List<AddonEntity>)

    @Query("UPDATE addons SET enabled = :enabled WHERE id = :id")
    suspend fun setAddonEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteAddon(id: String)

    @Query("DELETE FROM addons WHERE isFromStremioAccount = 1")
    suspend fun clearSyncedStremioAddons()

    // Watch Progress (Continue Watching)
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC LIMIT 30")
    fun observeContinueWatching(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE key = :key LIMIT 1")
    suspend fun getProgress(key: String): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllProgress(items: List<WatchProgressEntity>)

    @Query("DELETE FROM watch_progress WHERE key = :key")
    suspend fun removeProgress(key: String)

    // Watch History (Watched Badge)
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC")
    fun observeWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markWatched(item: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE mediaId = :mediaId")
    suspend fun unmarkWatched(mediaId: String)

    // Library
    @Query("SELECT * FROM library ORDER BY addedAt DESC")
    fun observeLibrary(): Flow<List<LibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToLibrary(item: LibraryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryItems(items: List<LibraryEntity>)

    @Query("DELETE FROM library WHERE mediaId = :mediaId")
    suspend fun removeFromLibrary(mediaId: String)

    // Search History
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSearchQuery(item: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [
        AddonEntity::class,
        WatchProgressEntity::class,
        WatchHistoryEntity::class,
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
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class CatalogCacheStore(context: Context) {
    private val prefs = context.getSharedPreferences("filmr_fast_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveCatalogRows(rows: List<AddonCatalogRow>) {
        runCatching {
            prefs.edit().putString("cached_rows_v2", gson.toJson(rows)).apply()
        }
    }

    fun loadCatalogRows(): List<AddonCatalogRow> {
        return runCatching {
            val json = prefs.getString("cached_rows_v2", null) ?: return emptyList()
            val type = object : TypeToken<List<AddonCatalogRow>>() {}.type
            gson.fromJson<List<AddonCatalogRow>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun saveSettings(settings: AppSettings) {
        runCatching {
            prefs.edit().putString("app_settings_v2", gson.toJson(settings)).apply()
        }
    }

    fun loadSettings(): AppSettings {
        return runCatching {
            val json = prefs.getString("app_settings_v2", null) ?: return AppSettings()
            gson.fromJson(json, AppSettings::class.java) ?: AppSettings()
        }.getOrDefault(AppSettings())
    }

    fun saveStremioAccount(account: StremioAccountState) {
        runCatching {
            prefs.edit().putString("stremio_account_v1", gson.toJson(account)).apply()
        }
    }

    fun loadStremioAccount(): StremioAccountState {
        return runCatching {
            val json = prefs.getString("stremio_account_v1", null) ?: return StremioAccountState()
            gson.fromJson(json, StremioAccountState::class.java) ?: StremioAccountState()
        }.getOrDefault(StremioAccountState())
    }
}
