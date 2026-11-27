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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FilmrViewModel(application: Application) : AndroidViewModel(application) {

    private val db = FilmrDatabase.getInstance(application)
    private val dao = db.dao()
    private val cacheStore = CatalogCacheStore(application)

    private val tmdbService = TmdbMetadataService()
    private val stremioService = StremioAddonService()
    private val cloudStreamService = CloudStreamRepoService()
    val torrentEngine = BuiltInTorrentEngine(application)

    private val _settings = MutableStateFlow(cacheStore.loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _stremioAccount = MutableStateFlow(cacheStore.loadStremioAccount())
    val stremioAccount: StateFlow<StremioAccountState> = _stremioAccount.asStateFlow()

    private val _catalogRows = MutableStateFlow(cacheStore.loadCatalogRows())
    val catalogRows: StateFlow<List<AddonCatalogRow>> = _catalogRows.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    // Streams & Subtitles for Active Details / Player
    private val _streams = MutableStateFlow<List<UnifiedStream>>(emptyList())
    val streams: StateFlow<List<UnifiedStream>> = _streams.asStateFlow()

    private val _isLoadingStreams = MutableStateFlow(false)
    val isLoadingStreams: StateFlow<Boolean> = _isLoadingStreams.asStateFlow()

    private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles.asStateFlow()

    // Search State
    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Room Flows
    val allAddons: StateFlow<List<AddonEntity>> = dao.observeAllAddons()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<WatchProgressEntity>> = dao.observeContinueWatching()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = dao.observeWatchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryItems: StateFlow<List<LibraryEntity>> = dao.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = dao.observeSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            ensureDefaultAddons()
            // Fast background Stremio account auto-sync if logged in
            val acc = _stremioAccount.value
            if (acc.isLoggedIn && acc.authKey.isNotBlank()) {
                syncStremioAccountSilently(acc)
            }
            refreshCatalogs()
        }
    }

    private suspend fun ensureDefaultAddons() {
        val existing = dao.getEnabledAddons()
        if (existing.isEmpty()) {
            val defaults = listOf(
                "https://v3-cinemeta.strem.io/manifest.json",
                "https://opensubtitles-v3.strem.io/manifest.json"
            )
            defaults.forEachIndexed { idx, url ->
                stremioService.installAddonFromUrl(url, isFromAccount = false, orderIndex = idx)?.let {
                    dao.upsertAddon(it)
                }
            }
        }
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        cacheStore.saveSettings(updated)
    }

    fun refreshCatalogs() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val currentSettings = _settings.value
                val tmdbDeferred = async { tmdbService.fetchHomeCatalogs(currentSettings) }

                val stremioAddons = dao.getEnabledAddonsByType("stremio")
                    .take(currentSettings.maxConcurrentAddons)

                val addonDeferreds = stremioAddons.map { addon ->
                    async {
                        withTimeoutOrNull(8_000L) {
                            stremioService.fetchAddonCatalogs(addon)
                        } ?: emptyList()
                    }
                }

                val tmdbRows = tmdbDeferred.await()
                val addonRows = addonDeferreds.awaitAll().flatten()
                val combined = (tmdbRows + addonRows).distinctBy { it.id }

                if (combined.isNotEmpty()) {
                    _catalogRows.value = combined
                    cacheStore.saveCatalogRows(combined)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Stremio Account Login & Fast Sync (Addons + Library + Continue Watching + Watched)
     */
    fun loginAndSyncStremio(email: String, password: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = stremioService.loginAndSyncStremioAccount(email, password)
            result.onSuccess { syncData ->
                applyStremioSyncData(syncData)
                refreshCatalogs()
                val msg = "تمت المزامنة بنجاح: ${syncData.addons.size} إضافة و ${syncData.libraryItems.size} عنصر بالمكتبة"
                _statusBanner.value = msg
                onComplete(true, msg)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "فشل تسجيل الدخول"
                _statusBanner.value = msg
                onComplete(false, msg)
            }
            _isRefreshing.value = false
        }
    }

    fun syncNowStremioAccount() {
        val acc = _stremioAccount.value
        if (!acc.isLoggedIn || acc.authKey.isBlank()) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val res = stremioService.syncWithAuthKey(acc.authKey, acc.email, acc.userId)
            res.onSuccess { syncData ->
                applyStremioSyncData(syncData)
                refreshCatalogs()
                _statusBanner.value = "تم تحديث المزامنة السريعة (${syncData.addons.size} إضافة • ${syncData.libraryItems.size} بالمكتبة)"
            }.onFailure {
                _statusBanner.value = "تعذر تحديث المزامنة حالياً"
            }
            _isRefreshing.value = false
        }
    }

    private suspend fun syncStremioAccountSilently(acc: StremioAccountState) {
        val res = stremioService.syncWithAuthKey(acc.authKey, acc.email, acc.userId)
        res.onSuccess { syncData ->
            applyStremioSyncData(syncData)
        }
    }

    private suspend fun applyStremioSyncData(syncData: com.filmr.tv.data.remote.StremioSyncResult) {
        _stremioAccount.value = syncData.account
        cacheStore.saveStremioAccount(syncData.account)

        if (syncData.addons.isNotEmpty()) {
            dao.clearSyncedStremioAddons()
            dao.upsertAddons(syncData.addons)
        }
        if (syncData.libraryItems.isNotEmpty()) {
            dao.upsertLibraryItems(syncData.libraryItems)
        }
        if (syncData.progressItems.isNotEmpty()) {
            dao.upsertAllProgress(syncData.progressItems)
        }
        syncData.watchedItems.forEach { dao.markWatched(it) }
    }

    fun logoutStremioAccount() {
        viewModelScope.launch {
            dao.clearSyncedStremioAddons()
            val cleared = StremioAccountState()
            _stremioAccount.value = cleared
            cacheStore.saveStremioAccount(cleared)
            _statusBanner.value = "تم تسجيل الخروج من حساب Stremio"
        }
    }

    /**
     * Manual Stremio Addon Installation
     */
    fun installStremioAddon(url: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val addon = stremioService.installAddonFromUrl(url, isFromAccount = false)
            if (addon != null) {
                dao.upsertAddon(addon)
                refreshCatalogs()
                onDone(true, "تم تثبيت إضافة Stremio: ${addon.name}")
            } else {
                onDone(false, "تعذر قراءة ملف manifest.json من الرابط")
            }
        }
    }

    /**
     * Manual CloudStream Repository / Plugin Installation (Completely Separate from Stremio)
     */
    fun installCloudStreamRepo(url: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val plugins = cloudStreamService.installCloudStreamRepo(url)
            if (plugins.isNotEmpty()) {
                dao.upsertAddons(plugins)
                onDone(true, "تم تثبيت ${plugins.size} إضافة من مستودع CloudStream")
            } else {
                onDone(false, "تعذر العثور على إضافات صالحة في رابط CloudStream")
            }
        }
    }

    fun toggleAddon(id: String, enabled: Boolean) {
        viewModelScope.launch {
            dao.setAddonEnabled(id, enabled)
        }
    }

    fun removeAddon(id: String) {
        viewModelScope.launch {
            dao.deleteAddon(id)
        }
    }

    suspend fun enrichDetails(item: MediaItem): MediaItem {
        return tmdbService.enrichMediaDetails(item, _settings.value)
    }

    suspend fun loadSeasonEpisodes(tmdbId: Int, season: Int, seriesId: String): List<EpisodeItem> {
        return tmdbService.fetchSeasonEpisodes(tmdbId, season, seriesId, _settings.value)
    }

    suspend fun loadPersonDetails(personId: Int): PersonDetails? {
        return tmdbService.fetchPersonDetails(personId, _settings.value)
    }

    suspend fun discoverCompany(id: Int, isNetwork: Boolean, sortBy: String): List<MediaItem> {
        return tmdbService.discoverByCompanyOrNetwork(id, isNetwork, sortBy, _settings.value)
    }

    /**
     * Unified Stream & Subtitle Loader:
     * Queries BOTH enabled Stremio addons AND enabled CloudStream plugins in parallel,
     * merging all servers into one Nuvio-style categorized list!
     */
    fun loadStreamsAndSubtitles(media: MediaItem, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _isLoadingStreams.value = true
            _streams.value = emptyList()
            _subtitles.value = emptyList()

            try {
                val baseId = media.imdbId ?: media.id
                val streamId = if (season != null && episode != null) "$baseId:$season:$episode" else baseId
                val stremioAddons = dao.getEnabledAddonsByType("stremio")
                val cloudPlugins = dao.getEnabledAddonsByType("cloudstream")

                val stremioStreamJobs = stremioAddons.map { addon ->
                    async {
                        withTimeoutOrNull(10_000L) {
                            stremioService.fetchStreamsForAddon(addon, media.type, streamId)
                        } ?: emptyList()
                    }
                }

                val cloudStreamJobs = cloudPlugins.map { plugin ->
                    async {
                        withTimeoutOrNull(10_000L) {
                            cloudStreamService.fetchStreamsForCloudStreamPlugin(plugin, media, season, episode)
                        } ?: emptyList()
                    }
                }

                val subtitleJobs = stremioAddons.map { addon ->
                    async {
                        withTimeoutOrNull(8_000L) {
                            stremioService.fetchSubtitlesForAddon(addon, media.type, streamId)
                        } ?: emptyList()
                    }
                }

                val mergedStreams = (stremioStreamJobs.awaitAll().flatten() + cloudStreamJobs.awaitAll().flatten())
                val embeddedSubs = mergedStreams.flatMap { it.subtitles }
                val externalSubs = subtitleJobs.awaitAll().flatten()

                _streams.value = mergedStreams
                _subtitles.value = (embeddedSubs + externalSubs).distinctBy { it.url }
            } finally {
                _isLoadingStreams.value = false
            }
        }
    }

    fun searchMedia(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            try {
                dao.saveSearchQuery(SearchHistoryEntity(query.trim()))
                val tmdbDeferred = async { tmdbService.searchMulti(query, _settings.value) }
                val stremioAddons = dao.getEnabledAddonsByType("stremio").take(4)
                val addonJobs = stremioAddons.map { addon ->
                    async {
                        withTimeoutOrNull(6_000L) {
                            stremioService.searchInAddon(addon, query)
                        } ?: emptyList()
                    }
                }
                val results = (tmdbDeferred.await() + addonJobs.awaitAll().flatten())
                    .distinctBy { it.imdbId ?: "${it.type}_${it.titleEn.lowercase()}_${it.year}" }
                _searchResults.value = results
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            dao.clearSearchHistory()
        }
    }

    fun toggleLibrary(media: MediaItem) {
        viewModelScope.launch {
            val exists = libraryItems.value.any { it.mediaId == media.id }
            if (exists) {
                dao.removeFromLibrary(media.id)
            } else {
                dao.addToLibrary(
                    LibraryEntity(
                        mediaId = media.id,
                        imdbId = media.imdbId,
                        mediaType = media.type,
                        titleAr = media.titleAr,
                        titleEn = media.titleEn,
                        posterUrl = media.posterUrl,
                        backdropUrl = media.backdropUrl,
                        year = media.year,
                        rating = media.imdbRating ?: media.tmdbRating
                    )
                )
            }
        }
    }

    fun toggleWatched(media: MediaItem) {
        viewModelScope.launch {
            val exists = watchHistory.value.any { it.mediaId == media.id }
            if (exists) {
                dao.unmarkWatched(media.id)
            } else {
                dao.markWatched(
                    WatchHistoryEntity(
                        mediaId = media.id,
                        mediaType = media.type,
                        title = media.displayTitle(_settings.value.preferEnglishTitles),
                        posterUrl = media.posterUrl
                    )
                )
            }
        }
    }

    fun saveWatchProgress(
        media: MediaItem,
        season: Int,
        episode: Int,
        episodeTitle: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L || positionMs < 5_000L) return
        viewModelScope.launch {
            val key = if (season > 0 && episode > 0) "${media.id}:$season:$episode" else media.id
            val fraction = positionMs.toFloat() / durationMs.toFloat()
            if (fraction >= 0.92f) {
                dao.removeProgress(key)
                dao.markWatched(
                    WatchHistoryEntity(
                        mediaId = media.id,
                        mediaType = media.type,
                        title = media.displayTitle(_settings.value.preferEnglishTitles),
                        posterUrl = media.posterUrl
                    )
                )
            } else {
                dao.upsertProgress(
                    WatchProgressEntity(
                        key = key,
                        mediaId = media.id,
                        imdbId = media.imdbId,
                        mediaType = media.type,
                        title = media.displayTitle(_settings.value.preferEnglishTitles),
                        posterUrl = media.posterUrl,
                        backdropUrl = media.backdropUrl,
                        season = season,
                        episode = episode,
                        episodeTitle = episodeTitle,
                        positionMs = positionMs,
                        durationMs = durationMs
                    )
                )
            }
        }
    }

    override fun onCleared() {
        torrentEngine.stopCurrentStream(keepSession = false)
        super.onCleared()
    }
}
