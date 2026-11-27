package com.filmr.tv.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_items")
data class LibraryEntity(
    @PrimaryKey val mediaId: String,
    val imdbId: String?,
    val tmdbId: Int?,
    val type: String,
    val title: String,
    val arabicTitle: String,
    val originalTitle: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val year: String,
    val ratingImdb: String?,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val mediaId: String,
    val imdbId: String?,
    val tmdbId: Int?,
    val type: String,
    val title: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val logoUrl: String?,
    val season: Int,
    val episode: Int,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "media_overrides")
data class MediaOverrideEntity(
    @PrimaryKey val mediaId: String,
    val customPosterUrl: String? = null,
    val customBackdropUrl: String? = null,
    val customTitle: String? = null,
    val isWatched: Boolean = false
)

@Entity(tableName = "stremio_addons")
data class StremioAddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val manifestUrl: String,
    val baseUrl: String,
    val iconUrl: String?,
    val typesJson: String,
    val resourcesJson: String,
    val catalogsJson: String,
    val enabled: Boolean = true,
    val orderIndex: Int = 0
)

@Entity(tableName = "cloudstream_repos")
data class CloudStreamRepoEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val description: String,
    val pluginsJson: String,
    val enabled: Boolean = true
)

@Dao
interface FilmrDao {
    // Library
    @Query("SELECT * FROM library_items ORDER BY addedAt DESC")
    fun observeLibrary(): Flow<List<LibraryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(item: LibraryEntity)

    @Query("DELETE FROM library_items WHERE mediaId = :mediaId")
    suspend fun removeLibrary(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM library_items WHERE mediaId = :mediaId)")
    suspend fun isInLibrary(mediaId: String): Boolean

    // Continue Watching
    @Query("SELECT * FROM continue_watching ORDER BY updatedAt DESC LIMIT 25")
    fun observeContinueWatching(): Flow<List<ContinueWatchingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContinueWatching(item: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE mediaId = :mediaId")
    suspend fun deleteContinueWatching(mediaId: String)

    // Custom Poster / Backdrop / Title / Watched Overrides
    @Query("SELECT * FROM media_overrides")
    fun observeOverrides(): Flow<List<MediaOverrideEntity>>

    @Query("SELECT * FROM media_overrides WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getOverride(mediaId: String): MediaOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOverride(override: MediaOverrideEntity)

    // Stremio Addons
    @Query("SELECT * FROM stremio_addons ORDER BY orderIndex ASC")
    fun observeAddons(): Flow<List<StremioAddonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAddon(addon: StremioAddonEntity)

    @Query("DELETE FROM stremio_addons WHERE id = :id")
    suspend fun deleteAddon(id: String)

    @Query("DELETE FROM stremio_addons")
    suspend fun clearAddons()

    // CloudStream Repos
    @Query("SELECT * FROM cloudstream_repos")
    fun observeCloudStreamRepos(): Flow<List<CloudStreamRepoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCloudStreamRepo(repo: CloudStreamRepoEntity)

    @Query("DELETE FROM cloudstream_repos WHERE id = :id")
    suspend fun deleteCloudStreamRepo(id: String)
}

@Database(
    entities = [
        LibraryEntity::class,
        ContinueWatchingEntity::class,
        MediaOverrideEntity::class,
        StremioAddonEntity::class,
        CloudStreamRepoEntity::class
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
                    "filmr_tv_pro_v6.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
