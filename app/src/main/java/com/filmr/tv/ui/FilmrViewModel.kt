package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.local.FilmrDatabase
import com.filmr.tv.data.remote.*
import com.filmr.tv.domain.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilmrViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = FilmrDatabase.get(app).dao()
    private val stremioService = StremioAddonService()
    private val cloudStreamService = CloudStreamRepoService()
    private val tmdbService = TmdbMetadataService()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val addons = dao.observeAddons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchProgress = dao.observeProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchHistory = dao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchHistory = dao.observeSearchHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val library = combine(dao.observeLibrary(), _settings) { list, s ->
        list.map {
            MediaItem(
                id = it.id,
                imdbId = it.imdbId,
                tmdbId = it.tmdbId,
                type = it.type,
                titleAr = it.titleAr,
                titleEn = it.titleEn,
                posterUrl = it.posterUrl,
                backdropUrl = it.backdropUrl,
                overviewAr = it.overviewAr,
                rating = it.rating,
                year = it.year,
                genres = it.genresCsv.split(",").filter { g -> g.isNotBlank() },
                useEnglishTitle = s.preferEnglishTitles
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryEntities = dao.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _session = MutableStateFlow<UserSessionEntity?>(null)
    val session = _session.asStateFlow()

    private val _addonStatusMsg = MutableStateFlow("")
    val addonStatusMsg = _addonStatusMsg.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading = _isInitialLoading.asStateFlow()

    private val _catalogRows = MutableStateFlow<List<AddonCatalogRow>>(emptyList())
    val catalogRows = combine(_catalogRows, _settings) { rows, s ->
        rows.map { r -> r.copy(items = r.items.map { it.copy(useEnglishTitle = s.preferEnglishTitles) }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _focusedHeroItem = MutableStateFlow<MediaItem?>(null)
    val focusedHeroItem = _focusedHeroItem.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = dao.getSession()
            _session.value = saved
            if (dao.getEnabledAddons().isEmpty()) {
                dao.upsertAddon(AddonEntity("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "", "الكتالوج الرسمي للأفلام والمسلسلات", "", "stremio", true, 0))
                dao.upsertAddon(AddonEntity("org.stremio.opensubtitlesv3", "OpenSubtitles v3", "https://opensubtitles-v3.strem.io/manifest.json", "", "ترجمات عربية وعالمية", "", "stremio", true, 1))
            }
            if (saved != null) {
                syncAllStremioData(saved.authKey)
            }
            refreshCatalogs()
            _isInitialLoading.value = false
        }
    }

    fun setFocusedHero(item: MediaItem) {
        if (_focusedHeroItem.value?.id != item.id) {
            _focusedHeroItem.value = item
        }
    }

    fun refreshCatalogs() {
        viewModelScope.launch {
            val enabled = dao.getEnabledAddons()
            val rows = mutableListOf<AddonCatalogRow>()
            val addonRowsDeferred = async { stremioService.fetchAllAddonCatalogs(enabled) }
            val tmdbMoviesDeferred = async { tmdbService.getTrending("movie", _settings.value.tmdbApiKey) }
            val tmdbSeriesDeferred = async { tmdbService.getTrending("series", _settings.value.tmdbApiKey) }

            val addonRows = addonRowsDeferred.await()
            val tMovies = tmdbMoviesDeferred.await()
            val tSeries = tmdbSeriesDeferred.await()

            rows.addAll(addonRows)
            if (tMovies.isNotEmpty()) rows.add(AddonCatalogRow("tmdb_m", "TMDB", "أفلام رائجة الآن", tMovies))
            if (tSeries.isNotEmpty()) rows.add(AddonCatalogRow("tmdb_s", "TMDB", "مسلسلات رائجة الآن", tSeries))

            _catalogRows.value = rows
            if (_focusedHeroItem.value == null) {
                _focusedHeroItem.value = rows.firstOrNull()?.items?.firstOrNull()
            }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) { _settings.update(transform) }

    fun addStremioAddon(rawUrl: String) {
        viewModelScope.launch {
            _addonStatusMsg.value = "جاري تثبيت إضافة Stremio..."
            val entity = stremioService.fetchManifest(rawUrl)
            if (entity != null) {
                dao.upsertAddon(entity)
                _addonStatusMsg.value = "تم تثبيت: ${entity.name}"
                refreshCatalogs()
            } else {
                _addonStatusMsg.value = "تعذّر الوصول لرابط إضافة Stremio"
            }
        }
    }

    fun addCloudStreamRepo(rawRepoUrl: String) {
        viewModelScope.launch {
            _addonStatusMsg.value = "جاري تحليل مستودع CloudStream وتثبيت الإضافات..."
            val plugins = cloudStreamService.parseRepository(rawRepoUrl)
            if (plugins.isNotEmpty()) {
                plugins.forEach { dao.upsertAddon(it) }
                _addonStatusMsg.value = "تم تثبيت ${plugins.size} إضافة من مستودع CloudStream بنجاح"
            } else {
                _addonStatusMsg.value = "تعذّر جلب إضافات من رابط المستودع"
            }
        }
    }

    fun toggleAddon(addon: AddonEntity) {
        viewModelScope.launch {
            dao.upsertAddon(addon.copy(isEnabled = !addon.isEnabled))
            refreshCatalogs()
        }
    }

    fun deleteAddon(id: String) {
        viewModelScope.launch {
            dao.deleteAddon(id)
            refreshCatalogs()
        }
    }

    fun loginStremio(email: String, pass: String) {
        viewModelScope.launch {
            _addonStatusMsg.value = "جاري تسجيل الدخول إلى Stremio..."
            val s = stremioService.loginStremio(email, pass)
            if (s != null) {
                dao.saveSession(s)
                _session.value = s
                _addonStatusMsg.value = "تم تسجيل الدخول ومزامنة الإضافات"
                syncAllStremioData(s.authKey)
            } else {
                _addonStatusMsg.value = "فشل تسجيل الدخول؛ تحقق من البريد وكلمة المرور"
            }
        }
    }

    fun logoutStremio() {
        viewModelScope.launch {
            dao.clearSession()
            _session.value = null
        }
    }

    fun syncAllStremioData(authKey: String) {
        viewModelScope.launch {
            stremioService.fetchUserAddonsFromAccount(authKey).forEach { dao.upsertAddon(it) }
            stremioService.fetchUserLibraryFromAccount(authKey).forEach { dao.upsertLibraryItem(it) }
            refreshCatalogs()
        }
    }

    suspend fun liveSearch(query: String): List<AddonCatalogRow> {
        if (query.isBlank()) return emptyList()
        val enabled = dao.getEnabledAddons()
        val rows = stremioService.searchAcrossAddons(enabled, query).toMutableList()
        if (rows.isEmpty()) {
            val tmdbRes = tmdbService.searchMulti(query, _settings.value.tmdbApiKey)
            if (tmdbRes.isNotEmpty()) {
                rows.add(AddonCatalogRow("tmdb", "TMDB", "نتائج البحث", tmdbRes))
            }
        }
        return rows
    }

    fun recordSearchQuery(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch { dao.upsertSearchQuery(SearchHistoryEntity(query.trim())) }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch { dao.deleteSearchQuery(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { dao.clearSearchHistory() }
    }

    suspend fun loadFullDetails(item: MediaItem): MediaItem {
        val enriched = tmdbService.enrichDetails(item, _settings.value.tmdbApiKey)
        return enriched.copy(useEnglishTitle = _settings.value.preferEnglishTitles)
    }

    suspend fun loadEpisodes(item: MediaItem, season: Int): List<EpisodeItem> {
        return tmdbService.getSeasonEpisodes(item.tmdbId, season, _settings.value.tmdbApiKey)
    }

    suspend fun fetchStreamsParallel(item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        var imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank() && item.tmdbId != 0) {
            imdb = tmdbService.enrichDetails(item, _settings.value.tmdbApiKey).imdbId
        }
        if (imdb.isBlank()) {
            imdb = stremioService.resolveImdbIdFromCinemeta(item.type, item.titleEn.ifBlank { item.titleAr })
        }
        val baseId = imdb.ifBlank { item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        val enabled = dao.getEnabledAddons()
        val stremioStreams = stremioService.fetchStreams(enabled, item.type, mediaId, _settings.value.torrentEnabled)
        val csPlugins = enabled.filter { it.type == "cloudstream" }
        val csStreams = cloudStreamService.fetchStreamsFromPlugins(csPlugins, item.titleEn, item.titleAr, imdb, item.type, season, episode)
        return (stremioStreams + csStreams).distinctBy { it.url }
    }

    suspend fun fetchSubtitles(item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> {
        val baseId = item.imdbId.ifBlank { item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        return stremioService.fetchSubtitles(dao.getEnabledAddons(), item.type, mediaId)
    }

    fun saveProgress(item: MediaItem, season: Int, episode: Int, pos: Long, dur: Long, streamUrl: String = "", addonName: String = "") {
        viewModelScope.launch {
            dao.upsertProgress(WatchProgressEntity("${item.id}_${season}_$episode", item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode, pos, dur, streamUrl, addonName))
            dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type))
        }
    }

    fun toggleLibrary(item: MediaItem) {
        viewModelScope.launch {
            val exists = dao.getAllLibrary().any { it.id == item.id }
            val ent = LibraryItemEntity(
                id = item.id,
                imdbId = item.imdbId,
                tmdbId = item.tmdbId,
                type = item.type,
                titleAr = item.titleAr.ifBlank { item.displayTitle },
                titleEn = item.titleEn.ifBlank { item.displayTitle },
                posterUrl = item.displayPoster,
                backdropUrl = item.displayBackdrop,
                overviewAr = item.overviewAr,
                genresCsv = item.genres.joinToString(","),
                rating = item.rating,
                year = item.year,
                isCloud = _session.value != null
            )
            if (exists) {
                dao.deleteLibraryItem(item.id)
                _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioService.pushLibraryItemToAccount(it, ent, true) }
            } else {
                dao.upsertLibraryItem(ent)
                _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioService.pushLibraryItemToAccount(it, ent, false) }
            }
        }
    }
}
