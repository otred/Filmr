package com.filmr.tv.domain.model

enum class MediaType(val value: String) {
    MOVIE("movie"),
    SERIES("series"),
    ANIME("anime"),
    CHANNEL("tv");

    companion object {
        fun fromString(raw: String?): MediaType = when (raw?.lowercase()) {
            "series", "tv", "show" -> SERIES
            "anime" -> ANIME
            "channel" -> CHANNEL
            else -> MOVIE
        }
    }
}

enum class NavSection(val titleAr: String) {
    HOME("الرئيسية"),
    SEARCH("البحث"),
    LIBRARY("المكتبة"),
    CALENDAR("التقويم"),
    SETTINGS("الإعدادات")
}

data class RatingsBundle(
    val imdb: String? = null,
    val tmdb: String? = null,
    val rottenTomatoes: String? = null,
    val metacritic: String? = null,
    val trakt: String? = null,
    val letterboxd: String? = null,
    val mal: String? = null
)

data class CastMember(
    val id: Int,
    val name: String,
    val character: String = "",
    val profileUrl: String? = null,
    val roleType: String = "ممثل"
)

data class EpisodeItem(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val thumbnailUrl: String? = null,
    val airDate: String? = null,
    val runtimeMinutes: Int = 0
)

data class MediaItem(
    val id: String,               // e.g., "tt1375666" or "tmdb:27205"
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val type: MediaType = MediaType.MOVIE,
    val title: String,
    val originalTitle: String = "",
    val arabicTitle: String = "",
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val year: String = "",
    val runtime: String = "",
    val certification: String = "",
    val genres: List<String> = emptyList(),
    val ratings: RatingsBundle = RatingsBundle(),
    val trailerYoutubeKey: String? = null,
    val trailerStreamUrl: String? = null,
    val episodes: List<EpisodeItem> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val directors: List<CastMember> = emptyList(),
    val networks: List<String> = emptyList(),
    val status: String = "",
    val isWatched: Boolean = false,
    val inLibrary: Boolean = false
)

data class StreamSource(
    val id: String,
    val addonId: String,
    val addonName: String,
    val title: String,
    val description: String = "",
    val quality: String = "HD",
    val sizeText: String = "",
    val seeds: Int? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val isTorrent: Boolean = false,
    val isCloudStream: Boolean = false
)

data class SubtitleTrack(
    val id: String,
    val url: String,
    val lang: String,
    val label: String
)

data class StremioAddon(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String = "",
    val manifestUrl: String,
    val baseUrl: String,
    val iconUrl: String? = null,
    val types: List<String> = listOf("movie", "series"),
    val resources: List<String> = listOf("stream"),
    val catalogs: List<AddonCatalog> = emptyList(),
    val enabled: Boolean = true,
    val orderIndex: Int = 0
)

data class AddonCatalog(
    val addonId: String,
    val addonName: String,
    val type: String,
    val id: String,
    val name: String
)

data class CloudStreamRepo(
    val id: String,
    val name: String,
    val url: String,
    val description: String = "",
    val plugins: List<CloudStreamPlugin> = emptyList(),
    val enabled: Boolean = true
)

data class CloudStreamPlugin(
    val id: String,
    val name: String,
    val internalName: String,
    val url: String,
    val language: String = "ar",
    val tvTypes: List<String> = listOf("Movie", "TvSeries"),
    val iconUrl: String? = null,
    val enabled: Boolean = true
)

data class ContinueWatchingItem(
    val mediaId: String,
    val imdbId: String?,
    val tmdbId: Int?,
    val type: MediaType,
    val title: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val logoUrl: String?,
    val season: Int = 0,
    val episode: Int = 0,
    val episodeTitle: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val remainingMinutes: Int
        get() = if (durationMs > positionMs) ((durationMs - positionMs) / 60000L).toInt() else 0
}

data class CalendarEpisodeEntry(
    val dateKey: String, // YYYY-MM-DD
    val dayOfMonth: Int,
    val mediaId: String,
    val seriesTitle: String,
    val posterUrl: String?,
    val season: Int,
    val episode: Int,
    val episodeTitle: String
)

data class UserProfile(
    val id: Int,
    val name: String,
    val avatarColorHex: Long,
    val isActive: Boolean = false
)

data class TmdbImageOption(
    val url: String,
    val isBackdrop: Boolean,
    val lang: String,
    val width: Int,
    val height: Int
)

data class AppSettings(
    // Accounts & Sync
    val stremioEmail: String = "",
    val stremioAuthKey: String = "",
    val traktConnected: Boolean = false,
    val traktUsername: String = "",
    val simklConnected: Boolean = false,
    val mdblistApiKey: String = "5b8f4d91a3c2e76094128f3d1c9a0b4e",
    val animeSkipClientId: String = "th2oogUKsi6ZmFUQPXrNcJZSnFmLI0WN",
    val tmdbApiKey: String = "439c478a771f35c05022f9feabcca01c",

    // Appearance & TV UI
    val accentColorHex: Long = 0xFFE5B869, // Filmr Signature Gold
    val pureBlackTheme: Boolean = true,
    val posterSizeScale: Float = 1.0f,
    val showContinueWatchingBadge: Boolean = true,
    val showPlatformFolders: Boolean = true,
    val autoTranslateArabic: Boolean = true,
    val preferArabicTitles: Boolean = true,
    val lowRamPerformanceMode: Boolean = false,

    // Playback & Torrent Engine
    val torrServerUrl: String = "http://127.0.0.1:8090",
    val autoResolveTorrents: Boolean = true,
    val subtitleTextSizeSp: Int = 22,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleBgOpacity: Float = 0.55f,
    val audioBoostDb: Int = 0,
    val nightModeAudio: Boolean = false,
    val autoSkipAnimeIntro: Boolean = true
)
