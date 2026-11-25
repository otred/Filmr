package com.filmr.tv.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val logoUrl: String = "",
    val iconUrl: String = "",
    val type: String = "stremio",
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val key: String,
    val mediaId: String,
    val imdbId: String = "",
    val type: String = "movie",
    val title: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val title: String,
    val posterUrl: String = "",
    val type: String = "movie",
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val id: Int = 1,
    val authKey: String = "",
    val email: String = "",
    val fullName: String = "",
    val avatarUrl: String = ""
)

@Entity(tableName = "custom_artwork")
data class CustomArtworkEntity(
    @PrimaryKey val mediaId: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = ""
)

data class MediaItem(
    val id: String,
    val imdbId: String = "",
    val tmdbId: Int = 0,
    val type: String = "movie",
    val titleAr: String = "",
    val titleEn: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = "",
    val overviewAr: String = "",
    val overviewEn: String = "",
    val rating: Double = 0.0,
    val mdbRating: String = "",
    val year: String = "",
    val releaseDate: String = "",
    val genres: List<String> = emptyList(),
    val network: String = "",
    val networkId: Int = 0,
    val seasonsCount: Int = 1,
    val cast: List<CastMember> = emptyList(),
    val customPosterUrl: String = "",
    val customBackdropUrl: String = "",
    val customLogoUrl: String = "",
    val useEnglishTitle: Boolean = false
) {
    val displayTitle: String get() = if (useEnglishTitle && titleEn.isNotBlank()) titleEn else titleAr.ifBlank { titleEn }
    val displayPoster: String get() = customPosterUrl.ifBlank { posterUrl }
    val displayBackdrop: String get() = customBackdropUrl.ifBlank { backdropUrl.ifBlank { posterUrl } }
    val displayLogo: String get() = customLogoUrl.ifBlank { logoUrl }
}

data class CastMember(val id: Int, val name: String, val character: String, val profileUrl: String)
data class EpisodeItem(val season: Int, val episode: Int, val title: String, val overview: String = "", val released: String = "", val stillUrl: String = "", val progressMs: Long = 0L, val durationMs: Long = 0L)
data class SubtitleTrack(val id: String, val url: String, val lang: String, val label: String)
data class UnifiedStream(val id: String, val name: String, val title: String, val url: String, val quality: String = "1080p", val qualityScore: Int = 1080, val addonName: String = "", val isTorrent: Boolean = false, val subtitles: List<SubtitleTrack> = emptyList())
data class UserProfile(val id: String, val name: String, val isKids: Boolean = false)
enum class PlayerEngineType { EXOPLAYER, VLC, EXTERNAL }
data class PlayerSettings(val defaultEngine: PlayerEngineType = PlayerEngineType.EXOPLAYER, val seekStepSeconds: Int = 10, val autoPlayNext: Boolean = true, val subtitleSize: Float = 20f)
data class ArtworkOptions(val posters: List<String> = emptyList(), val backdrops: List<String> = emptyList(), val logos: List<String> = emptyList())

data class AppSettings(
    val accentHex: Long = 0xFFE50914,
    val amoledBlack: Boolean = false,
    val showContinueWatching: Boolean = true,
    val disneyRow: Boolean = true,
    val hboRow: Boolean = true,
    val expandFocusedCard: Boolean = true,
    val torrentMode: Boolean = false,
    val mdbListApiKey: String = "",
    val animeSkipClientId: String = "",
    val tmdbApiKey: String = "4ef0d7355d9ffb5151e987764708ce96",
    val defaultEngine: PlayerEngineType = PlayerEngineType.EXOPLAYER,
    val seekStepSeconds: Int = 10
)
