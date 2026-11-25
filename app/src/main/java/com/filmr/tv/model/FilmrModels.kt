package com.filmr.tv.model

enum class AddonSourceType { STREMIO, CLOUDSTREAM }
enum class PlayerEngineType { EXOPLAYER, LIBVLC }

data class MediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val imdbId: String? = null,
    val type: String,
    val title: String,
    val overview: String = "",
    val posterUrl: String = "",
    val backdropUrl: String = "",
    val logoUrl: String? = null,
    val year: String = "",
    val rating: String = "8.5",
    val genres: List<String> = emptyList(),
    val seasons: List<SeasonData> = emptyList()
)

data class SeasonData(
    val seasonNumber: Int,
    val title: String,
    val episodes: List<EpisodeData> = emptyList()
)

data class EpisodeData(
    val id: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val overview: String = "",
    val thumbnail: String = "",
    val released: String = ""
)

data class UnifiedStream(
    val id: String,
    val title: String,
    val sourceName: String,
    val sourceType: AddonSourceType,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val qualityScore: Int = 1080,
    val subtitles: List<SubtitleTrack> = emptyList()
)

data class SubtitleTrack(
    val id: String,
    val language: String,
    val label: String,
    val url: String
)

data class ArtworkOptions(
    val posters: List<String> = emptyList(),
    val backdrops: List<String> = emptyList(),
    val logos: List<String> = emptyList()
)

data class StremioUserAccount(
    val authKey: String,
    val email: String,
    val fullName: String,
    val avatarUrl: String?
)

data class PlayerSettings(
    val seekStepSeconds: Int = 10,
    val engine: PlayerEngineType = PlayerEngineType.EXOPLAYER,
    val subtitleSizeSp: Float = 24f,
    val subtitleColorHex: Long = 0xFFFFFFFF,
    val subtitleBgAlpha: Float = 0.55f,
    val subtitleOffsetMs: Long = 0L,
    val audioDelayMs: Long = 0L,
    val audioVolumeBoost: Int = 100,
    val playbackSpeed: Float = 1.0f
)
