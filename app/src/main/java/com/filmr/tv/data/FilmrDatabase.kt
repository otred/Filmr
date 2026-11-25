package com.filmr.tv.data
import android.content.Context
import androidx.room.*
import com.filmr.tv.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FilmrDao {
    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, name ASC")
    fun getAllAddons(): Flow<List<AddonEntity>>
    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllAddonsOnce(): List<AddonEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddon(addon: AddonEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAddons(addons: List<AddonEntity>)
    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteAddon(id: String)
    @Query("UPDATE addons SET enabled = :enabled WHERE id = :id")
    suspend fun setAddonEnabled(id: String, enabled: Boolean)
    @Query("SELECT * FROM library ORDER BY addedAt DESC")
    fun getLibrary(): Flow<List<LibraryEntity>>
    @Query("SELECT * FROM library ORDER BY addedAt DESC")
    suspend fun getLibraryOnce(): List<LibraryEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM library WHERE id = :id)")
    suspend fun isInLibrary(id: String): Boolean
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibrary(item: LibraryEntity)
    @Query("DELETE FROM library WHERE id = :id")
    suspend fun deleteLibrary(id: String)
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    fun getWatchProgress(): Flow<List<WatchProgressEntity>>
    @Query("SELECT * FROM watch_progress ORDER BY updatedAt DESC")
    suspend fun getWatchProgressOnce(): List<WatchProgressEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: WatchProgressEntity)
    @Query("DELETE FROM watch_progress WHERE id = :id")
    suspend fun deleteProgress(id: String)
    @Query("SELECT * FROM custom_meta WHERE mediaId = :mediaId LIMIT 1")
    suspend fun getCustomMeta(mediaId: String): CustomMetaEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomMeta(meta: CustomMetaEntity)
}

@Database(entities = [AddonEntity::class, LibraryEntity::class, WatchProgressEntity::class, CustomMetaEntity::class], version = 3, exportSchema = false)
abstract class FilmrDatabase : RoomDatabase() {
    abstract fun dao(): FilmrDao
    companion object {
        @Volatile private var INSTANCE: FilmrDatabase? = null
        fun getInstance(context: Context): FilmrDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, FilmrDatabase::class.java, "filmr_tv_v3.db")
                .fallbackToDestructiveMigration().build().also { INSTANCE = it }
        }
    }
}
