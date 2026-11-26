package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.FilmrDatabase
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilmrViewModel(app: Application) : AndroidViewModel(app) {
    private val db = FilmrDatabase.get(app)
    private val dao = db.dao()
    private val stremioEngine = StremioEngine()
    private val cloudStreamEngine = CloudStreamEngine()
    private val tmdbEngine = TmdbArtworkEngine()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val playerSettings: StateFlow<PlayerSettings> = _settings
        .map { PlayerSettings(it.defaultEngine, it.seekStepSeconds) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    val addons: StateFlow<List<AddonEntity>> = dao.observeAddons()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watchProgress: StateFlow<List<WatchProgressEntity>> = dao.observeProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<WatchHistoryEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _session = MutableStateFlow<UserSessionEntity?>(null)
    val session: StateFlow<UserSessionEntity?> = _session.asStateFlow()

    val profiles = listOf(UserProfile("main", "الرئيسي"), UserProfile("kids", "الأطفال", true))
    private val _activeProfile = MutableStateFlow(profiles.first())
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    private val _trendingMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    val trendingMovies: StateFlow<List<MediaItem>> = _trendingMovies.asStateFlow()

    private val _trendingSeries = MutableStateFlow<List<MediaItem>>(emptyList())
    val trendingSeries: StateFlow<List<MediaItem>> = _trendingSeries.asStateFlow()

    private val _disneyRow = MutableStateFlow<List<MediaItem>>(emptyList())
    val disneyRow: StateFlow<List<MediaItem>> = _disneyRow.asStateFlow()

    private val _hboRow = MutableStateFlow<List<MediaItem>>(emptyList())
    val hboRow: StateFlow<List<MediaItem>> = _hboRow.asStateFlow()

    private val _library = MutableStateFlow<List<MediaItem>>(emptyList())
    val library: StateFlow<List<MediaItem>> = _library.asStateFlow()

    init {
        viewModelScope.launch {
            _session.value = dao.getSession()
            if (dao.getEnabledAddons().isEmpty()) {
                dao.upsertAddon(AddonEntity("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "الكتالوج الرسمي"))
            }
            refreshHome()
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            val key = _settings.value.tmdbApiKey
            _trendingMovies.value = tmdbEngine.getTrending("movie", key)
            _trendingSeries.value = tmdbEngine.getTrending("series", key)
            _disneyRow.value = tmdbEngine.getByNetwork(2739, key)
            _hboRow.value = tmdbEngine.getByNetwork(49, key)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) { _settings.update(transform) }
    fun selectProfile(p: UserProfile) { _activeProfile.value = p }

    fun addAddon(url: String) {
        viewModelScope.launch {
            stremioEngine.fetchManifest(url)?.let { dao.upsertAddon(it) }
        }
    }

    fun toggleAddon(addon: AddonEntity) {
        viewModelScope.launch { dao.upsertAddon(addon.copy(isEnabled = !addon.isEnabled)) }
    }

    fun deleteAddon(id: String) {
        viewModelScope.launch { dao.deleteAddon(id) }
    }

    fun syncCloudStreamRepo(repoUrl: String) {
        viewModelScope.launch {
            cloudStreamEngine.parseRepository(repoUrl).forEach { dao.upsertAddon(it) }
        }
    }

    fun loginStremio(email: String, pass: String) {
        viewModelScope.launch {
            val s = stremioEngine.loginStremio(email, pass)
            if (s != null) {
                dao.saveSession(s)
                _session.value = s
                fetchUserAddonsFromAccount(s.authKey)
            }
        }
    }

    fun logoutStremio() {
        viewModelScope.launch {
            dao.clearSession()
            _session.value = null
        }
    }

    fun fetchUserAddonsFromAccount(authKey: String) {
        viewModelScope.launch {
            stremioEngine.fetchUserAddonsFromAccount(authKey).forEach { dao.upsertAddon(it) }
        }
    }

    suspend fun search(query: String): List<MediaItem> = tmdbEngine.searchArabic(query, _settings.value.tmdbApiKey)

    suspend fun loadFullDetails(item: MediaItem): MediaItem {
        val enriched = tmdbEngine.enrichDetails(item, _settings.value.tmdbApiKey)
        val art = dao.getCustomArtwork(enriched.id)
        return if (art != null) enriched.copy(customPosterUrl = art.posterUrl, customBackdropUrl = art.backdropUrl, customLogoUrl = art.logoUrl) else enriched
    }

    suspend fun loadEpisodes(item: MediaItem, season: Int): List<EpisodeItem> = tmdbEngine.getSeasonEpisodes(item.tmdbId, season, _settings.value.tmdbApiKey)

    suspend fun fetchStreams(item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        val mediaId = if (item.type == "series") "${item.imdbId.ifBlank { item.id }}:$season:$episode" else item.imdbId.ifBlank { item.id }
        val enabled = dao.getEnabledAddons()
        val s1 = stremioEngine.fetchStreams(enabled, item.type, mediaId)
        val s2 = enabled.filter { it.type == "cloudstream" }.flatMap { cloudStreamEngine.fetchStreamsFromPlugin(it, item.displayTitle, season, episode) }
        return (s1 + s2).distinctBy { it.url }.sortedByDescending { it.qualityScore }
    }

    suspend fun fetchSubtitles(item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> {
        val mediaId = if (item.type == "series") "${item.imdbId.ifBlank { item.id }}:$season:$episode" else item.imdbId.ifBlank { item.id }
        return stremioEngine.fetchSubtitles(dao.getEnabledAddons(), item.type, mediaId)
    }

    fun saveProgress(item: MediaItem, season: Int, episode: Int, pos: Long, dur: Long) {
        viewModelScope.launch {
            val key = "${item.id}_${season}_$episode"
            dao.upsertProgress(WatchProgressEntity(key, item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode, pos, dur))
            dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type))
        }
    }

    fun removeProgress(key: String) {
        viewModelScope.launch { dao.deleteProgress(key) }
    }

    fun toggleLibrary(item: MediaItem) {
        _library.update { list -> if (list.any { it.id == item.id }) list.filterNot { it.id == item.id } else list + item }
    }

    suspend fun loadArtworkGallery(item: MediaItem): ArtworkOptions = tmdbEngine.fetchArtworkOptions(item.tmdbId, item.type, _settings.value.tmdbApiKey)

    fun saveCustomArtwork(mediaId: String, poster: String, backdrop: String, logo: String = "") {
        viewModelScope.launch { dao.saveCustomArtwork(CustomArtworkEntity(mediaId, poster, backdrop, logo)) }
    }
}
