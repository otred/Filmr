package com.filmr.tv.data

import android.content.Context
import androidx.room.*
import com.filmr.tv.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmrDao {
    @Query("SELECT * FROM addons ORDER BY orderIndex ASC") fun observeAddons(): Flow<List<AddonEntity>>
    @Query("SELECT * FROM addons WHERE isEnabled = 1 ORDER BY orderIndex ASC") suspend fun getEnabledAddons(): List<AddonEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAddon(addon: AddonEntity)
    @Query("DELETE FROM addons WHERE id = :id") suspend fun deleteAddon(id: String)
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC") fun observeProgress(): Flow<List<WatchProgressEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertProgress(progress: WatchProgressEntity)
    @Query("DELETE FROM watch_progress WHERE `key` = :key") suspend fun deleteProgress(key: String)
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC") fun observeHistory(): Flow<List<WatchHistoryEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertHistory(history: WatchHistoryEntity)
    @Query("SELECT * FROM custom_artwork") fun observeAllCustomArtwork(): Flow<List<CustomArtworkEntity>>
    @Query("SELECT * FROM custom_artwork WHERE mediaId = :mediaId LIMIT 1") suspend fun getCustomArtwork(mediaId: String): CustomArtworkEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCustomArtwork(art: CustomArtworkEntity)
    @Query("SELECT * FROM library ORDER BY addedAt DESC") fun observeLibrary(): Flow<List<LibraryItemEntity>>
    @Query("SELECT * FROM library") suspend fun getAllLibrary(): List<LibraryItemEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLibraryItem(item: LibraryItemEntity)
    @Query("DELETE FROM library WHERE id = :id") suspend fun deleteLibraryItem(id: String)
    @Query("SELECT * FROM user_session WHERE id = 1 LIMIT 1") suspend fun getSession(): UserSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(session: UserSessionEntity)
    @Query("DELETE FROM user_session") suspend fun clearSession()
}

@Database(
    entities = [AddonEntity::class, WatchProgressEntity::class, WatchHistoryEntity::class, LibraryItemEntity::class, CustomArtworkEntity::class, UserSessionEntity::class],
    version = 3,
    exportSchema = false
)
abstract class FilmrDatabase : RoomDatabase() {
    abstract fun dao(): FilmrDao
    companion object {
        @Volatile private var INSTANCE: FilmrDatabase? = null
        fun get(context: Context): FilmrDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, FilmrDatabase::class.java, "filmr_tv.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
