package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.local.CatalogCacheStore
import com.filmr.tv.data.local.FilmrDatabase
import com.filmr.tv.data.model.*
import com.filmr.tv.data.remote.CloudStreamRepoService
import com.filmr.tv.data.remote.StremioAddonService
import com.filmr.tv.data.remote.TmdbMetadataService
import com.filmr.tv.data.torrent.BuiltInTorrentEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilmrViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FilmrDatabase.getInstance(application).dao()
    private val cache = CatalogCacheStore(application)
    private val tmdbService = TmdbMetadataService()
    private val stremioService = StremioAddonService()
    private val cloudStreamService = CloudStreamRepoService()
    val torrentEngine = BuiltInTorrentEngine(application)

    private val _settings = MutableStateFlow(cache.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _catalogRows = MutableStateFlow(cache.loadCatalogRows())
    val catalogRows: StateFlow<List<AddonCatalogRow>> = _catalogRows.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedItem = MutableStateFlow<MediaItem?>(null)
    val selectedItem: StateFlow<MediaItem?> = _selectedItem.asStateFlow()

    private val _streams = MutableStateFlow<List<UnifiedStream>>(emptyList())
    val streams: StateFlow<List<UnifiedStream>> = _streams.asStateFlow()

    private val _isLoadingStreams = MutableStateFlow(false)
    val isLoadingStreams: StateFlow<Boolean> = _isLoadingStreams.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles.asStateFlow()

    private val _searchResults = MutableStateFlow<List<AddonCatalogRow>>(emptyList())
    val searchResults: StateFlow<List<AddonCatalogRow>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _personDetails = MutableStateFlow<PersonDetails?>(null)
    val personDetails: StateFlow<PersonDetails?> = _personDetails.asStateFlow()

    private val _companyItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val companyItems: StateFlow<List<MediaItem>> = _companyItems.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    val addons: StateFlow<List<AddonEntity>> = dao.observeAddons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchProgress: StateFlow<List<WatchProgressEntity>> = dao.observeWatchProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library: StateFlow<List<LibraryEntity>> = dao.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.getAllAddons().isEmpty()) {
                dao.upsertAddon(
                    AddonEntity(
                        id = "com.linvo.cinemeta",
                        name = "Cinemeta",
                        description = "المكتبة الرسمية للأفلام والمسلسلات",
                        manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                        type = "stremio",
                        isEnabled = true
                    )
                )
                dao.upsertAddon(
                    AddonEntity(
                        id = "org.stremio.opensubtitlesv3",
                        name = "OpenSubtitles v3",
                        description = "ترجمات عربية وعالمية تلقائية",
                        manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
                        type = "stremio",
                        isEnabled = true
                    )
                )
            }
            refreshCatalogs()
        }
    }

    fun refreshCatalogs() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentAddons = dao.getAllAddons()
                val tmdbDeferred = async { tmdbService.fetchTrendingRows(_settings.value) }
                val stremioDeferred = async { stremioService.fetchAllAddonCatalogs(currentAddons) }
                val combined = tmdbDeferred.await() + stremioDeferred.await()
                if (combined.isNotEmpty()) {
                    _catalogRows.value = combined
                    cache.saveCatalogRows(combined)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        cache.saveSettings(newSettings)
    }

    fun openDetails(item: MediaItem) {
        _selectedItem.value = item
        _streams.value = emptyList()
        viewModelScope.launch {
            val enriched = tmdbService.enrichItem(item, _settings.value)
            _selectedItem.value = enriched
        }
    }

    fun loadSeasonEpisodes(season: Int) {
        val current = _selectedItem.value ?: return
        if (current.tmdbId == 0) return
        viewModelScope.launch {
            val eps = tmdbService.fetchSeasonEpisodes(current.tmdbId, season, _settings.value.tmdbLanguage)
            if (eps.isNotEmpty()) {
                _selectedItem.value = current.copy(episodes = eps)
            }
        }
    }

    fun loadStreamsForItem(item: MediaItem, season: Int = 1, episode: Int = 1) {
        viewModelScope.launch {
            _isLoadingStreams.value = true
            _streams.value = emptyList()
            try {
                val currentAddons = dao.getAllAddons()
                val stremioStreams = stremioService.fetchStreams(currentAddons, item, season, episode)
                val csStreams = cloudStreamService.buildCloudStreamSources(currentAddons, item, season, episode)
                val all = (stremioStreams + csStreams).sortedByDescending { it.qualityScore }
                _streams.value = all
            } finally {
                _isLoadingStreams.value = false
            }
        }
    }

    fun loadSubtitlesForPlayback(item: MediaItem, season: Int = 1, episode: Int = 1) {
        viewModelScope.launch {
            val currentAddons = dao.getAllAddons()
            _subtitles.value = stremioService.fetchSubtitles(currentAddons, item, season, episode)
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            dao.upsertSearchHistory(SearchHistoryEntity(query.trim()))
            try {
                val currentAddons = dao.getAllAddons()
                val tmdbItems = tmdbService.searchTmdb(query, _settings.value)
                val addonRows = stremioService.searchAcrossAddons(currentAddons, query)
                val rows = mutableListOf<AddonCatalogRow>()
                if (tmdbItems.isNotEmpty()) {
                    rows.add(AddonCatalogRow("tmdb", "TMDB", "search", "نتائج البحث في TMDB", "ar", tmdbItems))
                }
                rows.addAll(addonRows)
                _searchResults.value = rows
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun installAddon(url: String) {
        viewModelScope.launch {
            _statusMessage.value = "جاري التحقق من الرابط..."
            val clean = url.trim()
            val stremioAddon = stremioService.fetchManifest(clean)
            if (stremioAddon != null) {
                dao.upsertAddon(stremioAddon)
                _statusMessage.value = "تم تثبيت إضافة Stremio: ${stremioAddon.name}"
                refreshCatalogs()
                return@launch
            }
            val csAddons = cloudStreamService.importRepository(clean)
            if (csAddons.isNotEmpty()) {
                csAddons.forEach { dao.upsertAddon(it) }
                _statusMessage.value = "تم استيراد ${csAddons.size} إضافة من CloudStream"
            } else {
                _statusMessage.value = "تعذر قراءة الرابط، تأكد من صحته"
            }
        }
    }

    fun toggleAddon(id: String, enabled: Boolean) {
        viewModelScope.launch {
            dao.setAddonEnabled(id, enabled)
            refreshCatalogs()
        }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch {
            dao.deleteAddon(id)
            refreshCatalogs()
        }
    }

    fun toggleLibrary(item: MediaItem) {
        viewModelScope.launch {
            val exists = library.value.any { it.mediaId == item.id }
            if (exists) {
                dao.deleteLibrary(item.id)
                _statusMessage.value = "تمت الإزالة من المكتبة"
            } else {
                dao.upsertLibrary(
                    LibraryEntity(
                        mediaId = item.id,
                        imdbId = item.imdbId,
                        type = item.type,
                        title = item.displayTitle(_settings.value.preferEnglishTitles),
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        year = item.year,
                        rating = item.rating
                    )
                )
                _statusMessage.value = "تمت الإضافة إلى المكتبة"
            }
        }
    }

    fun saveWatchProgress(item: MediaItem, season: Int, episode: Int, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        viewModelScope.launch {
            val key = "${item.id}_${season}_$episode"
            dao.upsertWatchProgress(
                WatchProgressEntity(
                    mediaKey = key,
                    mediaId = item.id,
                    imdbId = item.imdbId,
                    type = item.type,
                    title = item.displayTitle(_settings.value.preferEnglishTitles),
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    season = season,
                    episode = episode,
                    positionMs = positionMs,
                    durationMs = durationMs
                )
            )
        }
    }

    fun openPerson(personId: Int) {
        _personDetails.value = null
        viewModelScope.launch {
            _personDetails.value = tmdbService.fetchPersonDetails(personId, _settings.value.tmdbLanguage)
        }
    }

    fun openCompany(company: CompanyItem, sortBy: String = "popularity.desc") {
        _companyItems.value = emptyList()
        viewModelScope.launch {
            _companyItems.value = tmdbService.discoverByCompanyOrNetwork(company, sortBy, _settings.value.tmdbLanguage)
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        torrentEngine.stop()
    }
}
