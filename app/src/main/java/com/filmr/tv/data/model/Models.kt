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
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String = "",
    val overviewAr: String = "",
    val overviewEn: String = "",
    val year: String = "",
    val rating: Double = 0.0,
    val imdbRating: String = "",
    val tmdbRating: String = "",
    val rtRating: String = "",
    val metacriticRating: String = "",
    val letterboxdRating: String = "",
    val malRating: String = "",
    val genres: List<String> = emptyList(),
    val network: String = "",
    val networkId: Int = 0,
    val runtimeMinutes: Int = 0,
    val seasonsCount: Int = 1,
    val episodes: List<EpisodeItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val companies: List<CompanyItem> = emptyList(),
    val recommendations: List<MediaItem> = emptyList(),
    val customPosterUrl: String = "",
    val customBackdropUrl: String = "",
    val customLogoUrl: String = "",
    val customTitle: String = "",
    val useEnglishTitle: Boolean = false,
    val sourceAddon: String = ""
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

data class CastMember(
    val id: Int,
    val name: String,
    val character: String,
    val profileUrl: String?
)

data class CompanyItem(
    val id: Int,
    val name: String,
    val logoUrl: String?
)

data class PersonDetails(
    val id: Int,
    val name: String,
    val biography: String,
    val birthday: String,
    val placeOfBirth: String,
    val profileUrl: String?,
    val knownFor: List<MediaItem> = emptyList()
)

data class EpisodeItem(
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String,
    val rating: Double,
    val stillUrl: String,
    val runtime: Int = 24
)

data class AddonCatalogRow(
    val addonId: String,
    val addonName: String,
    val catalogId: String,
    val title: String,
    val lang: String,
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
    val quality: String = "1080p",
    val qualityScore: Int = 1080,
    val addonName: String = "",
    val sourceType: String = "stremio",
    val sizeText: String = "",
    val seeders: Int = 0,
    val isTorrent: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class UserProfile(
    val id: String,
    val name: String,
    val isKids: Boolean = false
)

data class AppSettings(
    val tmdbEnabled: Boolean = true,
    val tmdbApiKey: String = "439c478a771f35c05022d9feabcca01c",
    val tmdbLanguage: String = "ar-SA",
    val tmdbArtwork: Boolean = true,
    val tmdbBasicInfo: Boolean = true,
    val tmdbCast: Boolean = true,
    val tmdbCompanies: Boolean = true,
    val tmdbNetworks: Boolean = true,
    val tmdbEpisodes: Boolean = true,
    val tmdbTrailers: Boolean = true,
    val tmdbRecommendations: Boolean = true,
    val tmdbCollections: Boolean = true,
    val mdblistApiKey: String = "",
    val mdblistImdb: Boolean = true,
    val mdblistRt: Boolean = true,
    val mdblistMetacritic: Boolean = true,
    val mdblistLetterboxd: Boolean = true,
    val mdblistMal: Boolean = true,
    val animeSkipClientId: String = "",
    val animeSkipEnabled: Boolean = true,
    val preferEnglishTitles: Boolean = false,
    val amoledBlack: Boolean = false,
    val showContinueWatching: Boolean = true,
    val autoPlayNext: Boolean = true,
    val subtitleSizeSp: Float = 22f,
    val subtitleBold: Boolean = true,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.55f,
    val subtitleOutline: Boolean = true,
    val subtitleBottomPaddingDp: Float = 36f
)

@Entity(tableName = "addons")
data class AddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val repoUrl: String = "",
    val manifestUrl: String = "",
    val logoUrl: String = "",
    val type: String = "stremio",
    val isEnabled: Boolean = true,
    val orderIndex: Int = 0
)

@Entity(tableName = "watch_progress")
data class WatchProgressEntity(
    @PrimaryKey val key: String,
    val mediaId: String,
    val imdbId: String,
    val type: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String,
    val season: Int,
    val episode: Int,
    val positionMs: Long,
    val durationMs: Long,
    val streamUrl: String = "",
    val addonName: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val key: String,
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val type: String,
    val season: Int = 0,
    val episode: Int = 0,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "library")
data class LibraryEntity(
    @PrimaryKey val id: String,
    val imdbId: String,
    val tmdbId: Int,
    val type: String,
    val titleAr: String,
    val titleEn: String,
    val posterUrl: String,
    val backdropUrl: String,
    val rating: Double,
    val year: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_session")
data class UserSessionEntity(
    @PrimaryKey val id: Int = 1,
    val email: String,
    val authKey: String,
    val activeProfileId: String = "default"
)
