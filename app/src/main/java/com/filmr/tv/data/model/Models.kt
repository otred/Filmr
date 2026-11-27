package com.filmr.tv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class MediaItem(
    val id: String,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val type: String = "movie", // movie, series, tv
    val titleAr: String = "",
    val titleEn: String = "",
    val customTitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val overview: String = "",
    val year: String = "",
    val releaseInfo: String = "",
    val runtimeMinutes: Int = 0,
    val seasonsCount: Int = 0,
    val genres: List<String> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val companies: List<CompanyItem> = emptyList(),
    val network: String? = null,
    val networkId: Int? = null,
    val recommendations: List<MediaItem> = emptyList(),
    val imdbRating: String? = null,
    val tmdbRating: String? = null,
    val rtRating: String? = null,
    val metacriticRating: String? = null,
    val letterboxdRating: String? = null,
    val malRating: String? = null,
    val sourceAddonName: String? = null
) {
    fun displayTitle(preferEnglish: Boolean): String {
        if (!customTitle.isNullOrBlank()) return customTitle
        return if (preferEnglish) {
            titleEn.ifBlank { titleAr }
        } else {
            titleAr.ifBlank { titleEn }
        }
    }
}

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val thumbnailUrl: String? = null,
    val released: String = "",
    val runtimeMinutes: Int = 0
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profileUrl: String? = null
)

data class CompanyItem(
    val id: Int,
    val name: String,
    val logoUrl: String? = null,
    val isNetwork: Boolean = false
)

data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String = "",
    val birthday: String = "",
    val placeOfBirth: String = "",
    val profileUrl: String? = null,
    val credits: List<MediaItem> = emptyList()
)

data class AddonCatalogRow(
    val id: String,
    val title: String,
    val addonName: String,
    val addonType: String = "stremio", // tmdb, stremio, cloudstream
    val items: List<MediaItem>
)

data class SubtitleTrack(
    val id: String,
    val url: String,
    val lang: String,
    val label: String,
    val addonName: String = ""
)

data class UnifiedStream(
    val id: String,
    val title: String,
    val description: String = "",
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val quality: String = "HD",
    val sizeText: String = "",
    val seeders: Int = 0,
    val isTorrent: Boolean = false,
    val sourceEngine: String = "stremio", // stremio, cloudstream, torrent
    val addonName: String = "Stream",
    val addonUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class StremioAccountState(
    val isLoggedIn: Boolean = false,
    val email: String = "",
    val authKey: String = "",
    val userId: String = "",
    val lastSyncTimestamp: Long = 0L,
    val syncedAddonsCount: Int = 0,
    val syncedLibraryCount: Int = 0,
    val syncedHistoryCount: Int = 0
)

data class AppSettings(
    // Performance & Low-End Device Optimization
    val lowEndDeviceMode: Boolean = false,
    val reduceAnimations: Boolean = false,
    val maxConcurrentAddons: Int = 6,

    // Appearance & Playback (Nuvio Style)
    val amoledBlackMode: Boolean = false,
    val preferEnglishTitles: Boolean = false,
    val showContinueWatching: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val showHeroBackdrop: Boolean = true,

    // TMDB Enrichment
    val tmdbEnabled: Boolean = true,
    val tmdbApiKey: String = "d899771968f83d3981b25f89695a4242",
    val tmdbLanguage: String = "ar-SA",
    val tmdbArtwork: Boolean = true,
    val tmdbCast: Boolean = true,
    val tmdbCompanies: Boolean = true,
    val tmdbRecommendations: Boolean = true,

    // MDBList Multi-Ratings
    val mdblistApiKey: String = "",
    val showImdbRating: Boolean = true,
    val showTmdbRating: Boolean = true,
    val showRtRating: Boolean = true,
    val showMetacriticRating: Boolean = true,
    val showLetterboxdRating: Boolean = true,
    val showMalRating: Boolean = true,

    // Anime-Skip
    val animeSkipEnabled: Boolean = false,
    val animeSkipClientId: String = "",

    // Subtitle Studio Styling
    val subtitleFontSizeSp: Float = 22f,
    val subtitleBold: Boolean = true,
    val subtitleTextColorHex: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.55f,
    val subtitleEdgeOutline: Boolean = true,
    val subtitleBottomPaddingDp: Int = 44
)

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val manifestUrl: String,
    val transportUrl: String,
    val version: String = "1.0.0",
    val iconUrl: String? = null,
    val sourceType: String = "stremio", // "stremio" or "cloudstream"
    val isFromStremioAccount: Boolean = false,
    val enabled: Boolean = true,
    val orderIndex: Int = 0,
    val rawManifestJson: String = ""
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val key: String, // mediaId or mediaId:S:E
    val mediaId: String,
    val imdbId: String? = null,
    val mediaType: String = "movie",
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val mediaId: String,
    val mediaType: String = "movie",
    val title: String,
    val posterUrl: String? = null,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val mediaId: String,
    val imdbId: String? = null,
    val mediaType: String = "movie",
    val titleAr: String = "",
    val titleEn: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: String = "",
    val rating: String? = null,
    val syncedFromStremio: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)
