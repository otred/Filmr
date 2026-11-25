package com.filmr.tv.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "installed_addons")
data class AddonEntity(
    @PrimaryKey val url: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val type: String,
    val isEnabled: Boolean = true
)

@Entity(tableName = "custom_artwork")
data class CustomArtworkEntity(
    @PrimaryKey val mediaId: String,
    val customPosterUrl: String?,
    val customBackdropUrl: String?,
    val customLogoUrl: String?
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val mediaId: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val type: String,
    val season: Int = 1,
    val episode: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val id: Int = 1,
    val authKey: String,
    val email: String,
    val fullName: String,
    val avatarUrl: String?,
    val customAvatarUrl: String?,
    val seekStepSeconds: Int = 10,
    val defaultEngine: String = "EXOPLAYER",
    val tmdbApiKey: String = ""
)

@Dao
interface FilmrDao {
    @Query("SELECT * FROM installed_addons")
    fun observeAddons(): Flow<List<AddonEntity>>

    @Query("SELECT * FROM installed_addons WHERE isEnabled = 1")
    suspend fun getEnabledAddons(): List<AddonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: AddonEntity)

    @Delete
    suspend fun deleteAddon(addon: AddonEntity)

    @Query("SELECT * FROM custom_artwork WHERE mediaId = :mediaId")
    suspend fun getCustomArtwork(mediaId: String): CustomArtworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCustomArtwork(artwork: CustomArtworkEntity)

    @Query("SELECT * FROM watch_history ORDER BY updatedAt DESC LIMIT 30")
    fun observeHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(item: WatchHistoryEntity)

    @Query("SELECT * FROM user_session WHERE id = 1")
    fun observeSession(): Flow<UserSessionEntity?>

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
        CustomArtworkEntity::class,
        WatchHistoryEntity::class,
        UserSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FilmrDatabase : RoomDatabase() {
    abstract fun dao(): FilmrDao

    companion object {
        @Volatile private var INSTANCE: FilmrDatabase? = null

        fun getInstance(context: Context): FilmrDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FilmrDatabase::class.java,
                    "filmr_tv.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
