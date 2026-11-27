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
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilmrViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = FilmrDatabase.get(app).dao()
    private val stremioService = StremioAddonService()
    private val cloudStreamService = CloudStreamRepoService()
    private val tmdbService = TmdbMetadataService()

    private val _settings = MutableStateFlow(CatalogCacheStore.loadSettings(app))
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val addons = dao.observeAddons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchProgress = dao.observeProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchHistory = dao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchHistory = dao.observeSearchHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryEntities = dao.observeLibrary().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
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
                rating = it.rating,
                year = it.year,
                useEnglishTitle = s.preferEnglishTitles
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _addonCatalogRows = MutableStateFlow(CatalogCacheStore.loadCatalogRows(app))
    private val _isInitialLoading = MutableStateFlow(_addonCatalogRows.value.isEmpty())
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    val catalogRows = combine(_addonCatalogRows, _settings) { rows, s ->
        rows.map { r ->
            r.copy(items = r.items.map { it.copy(useEnglishTitle = s.preferEnglishTitles) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _addonCatalogRows.value)

    private val _focusedHeroItem = MutableStateFlow<MediaItem?>(
        _addonCatalogRows.value.firstOrNull()?.items?.firstOrNull()
    )
    val focusedHeroItem: StateFlow<MediaItem?> = _focusedHeroItem.asStateFlow()

    private val _addonStatusMsg = MutableStateFlow("")
    val addonStatusMsg: StateFlow<String> = _addonStatusMsg.asStateFlow()

    init {
        viewModelScope.launch {
            if (dao.getEnabledAddons().isEmpty()) {
                dao.upsertAddon(AddonEntity(
                    id = "com.linvo.cinemeta",
                    name = "Cinemeta",
                    description = "الفهرس الرسمي للأفلام والمسلسلات",
                    manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                    type = "stremio",
                    isEnabled = true,
                    orderIndex = 0
                ))
                dao.upsertAddon(AddonEntity(
                    id = "org.stremio.opensubtitlesv3",
                    name = "OpenSubtitles v3",
                    description = "ترجمات عربية وعالمية تلقائية",
                    manifestUrl = "https://opensubtitles-v3.strem.io/manifest.json",
                    type = "stremio",
                    isEnabled = true,
                    orderIndex = 1
                ))
            }
            refreshCatalogs()
        }
    }

    fun setFocusedHeroItem(item: MediaItem) {
        if (_focusedHeroItem.value?.id != item.id) {
            _focusedHeroItem.value = item
        }
    }

    fun refreshCatalogs() {
        viewModelScope.launch {
            val enabled = dao.getEnabledAddons()
            val s = _settings.value
            val addonDeferred = async { stremioService.fetchAllAddonCatalogs(enabled) }
            val tmdbMoviesDeferred = async {
                if (s.tmdbEnabled) tmdbService.getTrending("movie", s.tmdbApiKey, s.tmdbLanguage) else emptyList()
            }
            val tmdbSeriesDeferred = async {
                if (s.tmdbEnabled) tmdbService.getTrending("series", s.tmdbApiKey, s.tmdbLanguage) else emptyList()
            }

            val addonRows = addonDeferred.await()
            val tmdbMovies = tmdbMoviesDeferred.await()
            val tmdbSeries = tmdbSeriesDeferred.await()

            val merged = mutableListOf<AddonCatalogRow>()
            if (tmdbMovies.isNotEmpty()) {
                merged.add(AddonCatalogRow("tmdb_trending_m", "TMDB", "trending_m", "الأفلام الرائجة عالمياً", "ar", tmdbMovies))
            }
            if (tmdbSeries.isNotEmpty()) {
                merged.add(AddonCatalogRow("tmdb_trending_s", "TMDB", "trending_s", "المسلسلات الرائجة عالمياً", "ar", tmdbSeries))
            }
            merged.addAll(addonRows)

            if (merged.isNotEmpty()) {
                _addonCatalogRows.value = merged
                CatalogCacheStore.saveCatalogRows(getApplication(), merged)
                if (_focusedHeroItem.value == null) {
                    _focusedHeroItem.value = merged.firstOrNull()?.items?.firstOrNull()
                }
            }
            _isInitialLoading.value = false
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        CatalogCacheStore.saveSettings(getApplication(), updated)
    }

    fun addStremioAddon(rawUrl: String) {
        viewModelScope.launch {
            _addonStatusMsg.value = "جاري جلب بيانات الإضافة..."
            val entity = stremioService.fetchManifest(rawUrl)
            if (entity != null) {
                dao.upsertAddon(entity)
                _addonStatusMsg.value = "تم تثبيت ${entity.name} بنجاح"
                refreshCatalogs()
            } else {
                _addonStatusMsg.value = "تعذر الاتصال برابط الإضافة"
            }
        }
    }

    fun importCloudStreamRepo(rawUrl: String) {
        viewModelScope.launch {
            _addonStatusMsg.value = "جاري استيراد مستودع CloudStream..."
            val plugins = cloudStreamService.importRepository(rawUrl)
            if (plugins.isNotEmpty()) {
                plugins.forEach { dao.upsertAddon(it) }
                _addonStatusMsg.value = "تم تثبيت ${plugins.size} إضافة من المستودع"
            } else {
                _addonStatusMsg.value = "لم يتم العثور على إضافات في الرابط"
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

    suspend fun enrichDetails(item: MediaItem): MediaItem {
        return tmdbService.enrichDetails(item, _settings.value)
    }

    suspend fun getSeasonEpisodes(tmdbId: Int, season: Int): List<EpisodeItem> {
        val s = _settings.value
        return tmdbService.getSeasonEpisodes(tmdbId, season, s.tmdbApiKey, s.tmdbLanguage)
    }

    suspend fun getPersonDetails(personId: Int): PersonDetails? {
        val s = _settings.value
        return tmdbService.getPersonDetails(personId, s.tmdbApiKey, s.tmdbLanguage)
    }

    suspend fun discoverByCompanyOrNetwork(id: Int, isNetwork: Boolean, sortBy: String): List<MediaItem> {
        val s = _settings.value
        return tmdbService.discoverByCompanyOrNetwork(id, isNetwork, sortBy, s.tmdbApiKey, s.tmdbLanguage)
    }

    suspend fun searchAll(query: String): List<AddonCatalogRow> {
        if (query.isBlank()) return emptyList()
        dao.upsertSearchQuery(SearchHistoryEntity(query.trim()))
        val s = _settings.value
        val enabled = dao.getEnabledAddons()
        val tmdbRes = if (s.tmdbEnabled) tmdbService.searchMulti(query, s.tmdbApiKey, s.tmdbLanguage) else emptyList()
        val addonRes = stremioService.searchAcrossAddons(enabled, query)
        val rows = mutableListOf<AddonCatalogRow>()
        if (tmdbRes.isNotEmpty()) {
            rows.add(AddonCatalogRow("tmdb_search", "TMDB", "search", "نتائج البحث الفوري", "ar", tmdbRes))
        }
        rows.addAll(addonRes)
        return rows
    }

    suspend fun fetchUnifiedStreams(item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        val enabled = dao.getEnabledAddons()
        val stremioStreams = stremioService.fetchStreams(enabled, item, season, episode)
        val csStreams = cloudStreamService.fetchStreamsFromPlugins(enabled, item, season, episode)
        return (stremioStreams + csStreams).sortedByDescending { it.qualityScore }
    }

    suspend fun fetchSubtitles(item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> {
        val enabled = dao.getEnabledAddons()
        return stremioService.fetchSubtitles(enabled, item, season, episode)
    }

    fun toggleLibrary(item: MediaItem) {
        viewModelScope.launch {
            val exists = dao.getAllLibrary().any { it.id == item.id }
            if (exists) {
                dao.deleteLibraryItem(item.id)
            } else {
                dao.upsertLibraryItem(LibraryEntity(
                    id = item.id,
                    imdbId = item.imdbId,
                    tmdbId = item.tmdbId,
                    type = item.type,
                    titleAr = item.titleAr,
                    titleEn = item.titleEn,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    rating = item.rating,
                    year = item.year
                ))
            }
        }
    }

    fun saveWatchProgress(
        item: MediaItem,
        season: Int,
        episode: Int,
        positionMs: Long,
        durationMs: Long,
        streamUrl: String,
        addonName: String
    ) {
        if (durationMs <= 0L) return
        viewModelScope.launch {
            val key = if (item.type == "series") "${item.id}_s${season}_e$episode" else item.id
            dao.upsertProgress(WatchProgressEntity(
                key = key,
                mediaId = item.id,
                imdbId = item.imdbId,
                type = item.type,
                title = item.displayTitle,
                posterUrl = item.displayPoster,
                backdropUrl = item.displayBackdrop,
                season = season,
                episode = episode,
                positionMs = positionMs,
                durationMs = durationMs,
                streamUrl = streamUrl,
                addonName = addonName
            ))
        }
    }

    fun markAsWatched(item: MediaItem) {
        viewModelScope.launch {
            dao.upsertHistory(WatchHistoryEntity(
                key = item.id,
                mediaId = item.id,
                title = item.displayTitle,
                posterUrl = item.displayPoster,
                type = item.type
            ))
        }
    }
}
