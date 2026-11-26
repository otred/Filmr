package com.filmr.tv.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    val released: String = "",
    val genres: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val network: String = "",
    val networkId: Int = 0,
    val networkLogoUrl: String = "",
    val runtimeMinutes: Int = 0,
    val seasonsCount: Int = 1,
    val cast: List<CastMember> = emptyList(),
    val customPosterUrl: String = "",
    val customBackdropUrl: String = "",
    val customLogoUrl: String = "",
    val customTitle: String = "",
    val useEnglishTitle: Boolean = false
) {
    val displayTitle: String
        get() = when {
            customTitle.isNotBlank() -> customTitle
            useEnglishTitle && titleEn.isNotBlank() -> titleEn
            titleAr.isNotBlank() -> titleAr
            else -> titleEn
        }

    val displayPoster: String get() = customPosterUrl.ifBlank { posterUrl }
    val displayBackdrop: String get() = customBackdropUrl.ifBlank { backdropUrl.ifBlank { displayPoster } }
    val displayLogo: String get() = customLogoUrl.ifBlank { logoUrl }
}

data class CastMember(val id: Int, val name: String, val character: String, val profileUrl: String)
data class PersonDetails(val id: Int, val name: String, val biography: String, val birthday: String, val placeOfBirth: String, val profileUrl: String, val credits: List<MediaItem>)
data class EpisodeItem(val season: Int, val episode: Int, val title: String, val overview: String, val released: String, val stillUrl: String, val runtime: Int = 24)
data class ArtworkOptions(val posters: List<String> = emptyList(), val backdrops: List<String> = emptyList(), val logos: List<String> = emptyList())
data class CalendarEntry(val day: Int, val date: String, val item: MediaItem, val season: Int, val episode: Int, val title: String)
data class AddonCatalogRow(val title: String, val items: List<MediaItem>)
data class SubtitleTrack(val id: String, val url: String, val lang: String, val label: String)
data class UnifiedStream(val id: String, val name: String, val title: String, val url: String, val quality: String = "1080p", val qualityScore: Int = 1080, val addonName: String = "", val isTorrent: Boolean = false, val subtitles: List<SubtitleTrack> = emptyList())
data class UserProfile(val id: String, val name: String, val isKids: Boolean = false)

data class AppSettings(
    val tmdbApiKey: String = "439c478a771f35c05022d9feabcca01c",
    val mdbListApiKey: String = "",
    val mdbListEnabled: Boolean = false,
    val animeSkipClientId: String = "tQy0yTzaDFIYmxpyXsdRKroBhGem6pdg",
    val animeSkipEnabled: Boolean = true,
    val autoTranslateArabic: Boolean = true,
    val tmdbEnrichmentEnabled: Boolean = true,
    val preferEnglishTitles: Boolean = false,
    val amoledBlack: Boolean = false,
    val accentHex: Long = 0xFFE5A93C,
    val expandFocusedCard: Boolean = true,
    val cardExpandDelayMs: Long = 1200L,
    val showContinueWatching: Boolean = true,
    val trendingMoviesRow: Boolean = true,
    val trendingSeriesRow: Boolean = true,
    val disneyRow: Boolean = true,
    val hboRow: Boolean = true,
    val torrentEnabled: Boolean = true,
    val autoPlayNext: Boolean = true,
    val autoSkipIntro: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val autoSkipCredits: Boolean = false,
    val seekStepSeconds: Int = 10,
    val volumeBoostPercent: Int = 0,
    val subtitleSizeSp: Float = 20f,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val traktConnected: Boolean = false,
    val simklConnected: Boolean = false
)

@Entity(tableName = "addons")
data class AddonEntity(@PrimaryKey val id: String, val name: String, val url: String, val description: String = "", val logoUrl: String = "", val iconUrl: String = "", val type: String = "stremio", val isEnabled: Boolean = true, val orderIndex: Int = 0)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(@PrimaryKey val key: String, val mediaId: String, val imdbId: String, val type: String, val title: String, val posterUrl: String, val backdropUrl: String, val season: Int, val episode: Int, val positionMs: Long, val durationMs: Long, val streamUrl: String = "", val addonName: String = "", val updatedAt: Long = System.currentTimeMillis())

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(@PrimaryKey val id: String, val mediaId: String, val title: String, val posterUrl: String, val type: String, val watchedAt: Long = System.currentTimeMillis())

@Entity(tableName = "library")
data class LibraryItemEntity(@PrimaryKey val id: String, val imdbId: String = "", val tmdbId: Int = 0, val type: String = "movie", val titleAr: String = "", val titleEn: String = "", val posterUrl: String = "", val backdropUrl: String = "", val overviewAr: String = "", val rating: Double = 0.0, val year: String = "", val addedAt: Long = System.currentTimeMillis())

@Entity(tableName = "custom_artwork")
data class CustomArtworkEntity(@PrimaryKey val mediaId: String, val posterUrl: String = "", val backdropUrl: String = "", val logoUrl: String = "", val customTitle: String = "")

@Entity(tableName = "user_session")
data class UserSessionEntity(@PrimaryKey val id: Int = 1, val authKey: String, val email: String, val fullName: String = "", val avatarUrl: String = "")
