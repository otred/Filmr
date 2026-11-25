package com.filmr.tv.model
import androidx.room.Entity
import androidx.room.PrimaryKey

data class MediaItem(
    val id: String, val imdbId: String = "", val tmdbId: Int = 0, val type: String = "movie",
    val titleAr: String = "", val titleEn: String = "", val posterUrl: String = "", val backdropUrl: String = "",
    val overviewAr: String = "", val overviewEn: String = "", val rating: Double = 0.0, val mdbRating: String = "",
    val year: String = "", val releaseDate: String = "", val genres: List<String> = emptyList(),
    val network: String = "", val networkId: Int = 0, val seasonsCount: Int = 1, val cast: List<CastMember> = emptyList(),
    val customPosterUrl: String = "", val customBackdropUrl: String = "", val useEnglishTitle: Boolean = false
) {
    val displayTitle: String get() = if (useEnglishTitle && titleEn.isNotBlank()) titleEn else titleAr.ifBlank { titleEn }
    val displayPoster: String get() = customPosterUrl.ifBlank { posterUrl }
    val displayBackdrop: String get() = customBackdropUrl.ifBlank { backdropUrl.ifBlank { posterUrl } }
}
data class CastMember(val id: Int, val name: String, val character: String, val profileUrl: String)
data class EpisodeItem(val season: Int, val episode: Int, val title: String, val overview: String, val released: String, val stillUrl: String, val progressMs: Long = 0L, val durationMs: Long = 0L)
data class SubtitleTrack(val id: String, val url: String, val lang: String, val label: String)
data class UnifiedStream(val id: String, val name: String, val title: String, val url: String, val infoHash: String = "", val fileIdx: Int? = null, val sourceName: String, val engineType: String, val quality: String = "HD", val size: String = "", val seeders: Int = 0, val subtitles: List<SubtitleTrack> = emptyList())
data class ArtworkGallery(val posters: List<String> = emptyList(), val backdrops: List<String> = emptyList())
data class CalendarEntry(val date: String, val showTitle: String, val episodeCode: String, val episodeTitle: String, val posterUrl: String, val mediaItem: MediaItem, val aired: Boolean)
data class UserProfile(val id: String, val name: String, val colorHex: Long)
@Entity(tableName = "addons")
data class AddonEntity(@PrimaryKey val id: String, val name: String, val url: String, val description: String = "", val icon: String = "", val engineType: String = "Stremio", val enabled: Boolean = true, val sortOrder: Int = 0)
@Entity(tableName = "library")
data class LibraryEntity(@PrimaryKey val id: String, val imdbId: String, val tmdbId: Int, val type: String, val titleAr: String, val titleEn: String, val posterUrl: String, val backdropUrl: String, val rating: Double, val year: String, val addedAt: Long = System.currentTimeMillis())
@Entity(tableName = "watch_progress")
data class WatchProgressEntity(@PrimaryKey val id: String, val mediaId: String, val type: String, val titleAr: String, val titleEn: String, val posterUrl: String, val backdropUrl: String, val season: Int = 1, val episode: Int = 1, val episodeTitle: String = "", val positionMs: Long = 0L, val durationMs: Long = 0L, val watched: Boolean = false, val updatedAt: Long = System.currentTimeMillis())
@Entity(tableName = "custom_meta")
data class CustomMetaEntity(@PrimaryKey val mediaId: String, val customPoster: String = "", val customBackdrop: String = "", val useEnglishTitle: Boolean = false)
data class AppSettings(
    val accentHex: Long = 0xFFE50914, val amoledBlack: Boolean = true, val expandFocusedCard: Boolean = true,
    val showContinueWatching: Boolean = true, val hideUnreleased: Boolean = false, val autoScrollOverview: Boolean = true,
    val defaultPlayer: String = "ExoPlayer", val autoSwitchPlayer: Boolean = true, val torrentMode: String = "Auto",
    val autoPlayNext: Boolean = true, val seekStepSeconds: Int = 10, val subSizeSp: Int = 22, val subBold: Boolean = true,
    val subColorHex: Long = 0xFFFFFFFF, val audioBoostDb: Int = 0, val nightMode: Boolean = false, val hdrBrightness: Float = 1.0f,
    val tmdbApiKey: String = "4ef0d7355d9ffb5151e987764708ce96", val mdbListApiKey: String = "", val animeSkipClientId: String = "",
    val traktToken: String = "", val simklToken: String = "", val stremioAuthKey: String = "", val stremioEmail: String = ""
)
