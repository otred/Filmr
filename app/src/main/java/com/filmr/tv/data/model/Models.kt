package com.filmr.tv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class MediaItem(
    val id: String,
    val imdbId: String = "",
    val tmdbId: Int = 0,
    val type: String = "movie",
    val titleAr: String = "",
    val titleEn: String = "",
    val customTitle: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = "",
    val overviewAr: String = "",
    val year: String = "",
    val releaseDate: String = "",
    val ageRating: String = "",
    val country: String = "",
    val originalLang: String = "",
    val director: String = "",
    val runtimeMinutes: Int = 0,
    val seasonsCount: Int = 1,
    val rating: Double = 0.0,
    val imdbRating: String = "",
    val tmdbRating: String = "",
    val rtRating: String = "",
    val audienceRating: String = "",
    val metacriticRating: String = "",
    val letterboxdRating: String = "",
    val malRating: String = "",
    val trailerKey: String = "",
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val companies: List<CompanyItem> = emptyList(),
    val recommendations: List<MediaItem> = emptyList(),
    val sourceAddon: String = ""
) {
    fun displayTitle(preferEnglish: Boolean): String {
        if (customTitle.isNotBlank()) return customTitle
        return if (preferEnglish) {
            titleEn.ifBlank { titleAr }
        } else {
            titleAr.ifBlank { titleEn }
        }
    }

    fun formattedRuntime(): String {
        if (runtimeMinutes <= 0) return ""
        val h = runtimeMinutes / 60
        val m = runtimeMinutes % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}

data class EpisodeItem(
    val seasonNumber: Int,
    val episodeNumber: Int,
    val name: String,
    val overview: String = "",
    val stillUrl: String = "",
    val airDate: String = "",
    val runtime: Int = 0,
    val rating: String = ""
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profileUrl: String = ""
)

data class CompanyItem(
    val id: Int,
    val name: String,
    val logoUrl: String = "",
    val originCountry: String = "",
    val isNetwork: Boolean = false
)

data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String = "",
    val birthday: String = "",
    val placeOfBirth: String = "",
    val profileUrl: String = "",
    val knownFor: List<MediaItem> = emptyList()
)

data class AddonCatalogRow(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val title: String,
    val language: String = "ar",
    val items: List<MediaItem>
)

data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String,
    val url: String,
    val addonName: String = ""
)

data class UnifiedStream(
    val id: String,
    val name: String,
    val title: String,
    val url: String,
    val quality: String = "HD",
    val qualityScore: Int = 480,
    val addonName: String = "",
    val sourceType: String = "stremio",
    val sizeText: String = "",
    val seeders: Int = 0,
    val isTorrent: Boolean = false
)

data class AppSettings(
    val tmdbEnabled: Boolean = true,
    val tmdbApplyHome: Boolean = true,
    val tmdbApplyContinue: Boolean = true,
    val tmdbLanguage: String = "ar-SA",
    val tmdbArtwork: Boolean = true,
    val tmdbBasicInfo: Boolean = true,
    val tmdbDetails: Boolean = true,
    val tmdbCast: Boolean = true,
    val tmdbCompanies: Boolean = true,
    val tmdbNetworks: Boolean = true,
    val tmdbEpisodes: Boolean = true,
    val tmdbTrailers: Boolean = true,
    val tmdbRecommendations: Boolean = true,
    val autoTranslateArabic: Boolean = true,
    val mdbListEnabled: Boolean = true,
    val mdbListApiKey: String = "",
    val mdbShowOnHero: Boolean = true,
    val showImdbRating: Boolean = true,
    val showTmdbRating: Boolean = true,
    val showRtRating: Boolean = true,
    val showAudienceRating: Boolean = true,
    val showMcRating: Boolean = true,
    val showLbRating: Boolean = true,
    val showMalRating: Boolean = true,
    val animeSkipEnabled: Boolean = false,
    val animeSkipClientId: String = "",
    val amoledDarkMode: Boolean = false,
    val preferEnglishTitles: Boolean = false,
    val showContinueWatching: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val subtitleFontPercent: Int = 100,
    val subtitleBold: Boolean = true,
    val subtitleItalic: Boolean = false,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleTextOpacity: Int = 100,
    val subtitleBgOpacity: Int = 40,
    val subtitleOutline: Boolean = true,
    val subtitleDelayMs: Long = 0L
)

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val manifestUrl: String,
    val logoUrl: String = "",
    val type: String = "stremio",
    val isEnabled: Boolean = true
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val mediaKey: String,
    val mediaId: String,
    val imdbId: String = "",
    val type: String = "movie",
    val title: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val season: Int = 1,
    val episode: Int = 1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val mediaId: String,
    val imdbId: String = "",
    val type: String = "movie",
    val title: String,
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val year: String = "",
    val rating: Double = 0.0,
    val isWatched: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
