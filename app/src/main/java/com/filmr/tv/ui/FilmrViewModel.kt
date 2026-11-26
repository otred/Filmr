package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.FilmrDatabase
import com.filmr.tv.engine.CloudStreamEngine
import com.filmr.tv.engine.StremioEngine
import com.filmr.tv.engine.TmdbArtworkEngine
import com.filmr.tv.model.*
import kotlinx.coroutines.delay
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

    val addons: StateFlow<List<AddonEntity>> = dao.observeAddons()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watchProgress: StateFlow<List<WatchProgressEntity>> = dao.observeProgress()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<WatchHistoryEntity>> = dao.observeHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val customArtworkMap: StateFlow> = dao.observeAllCustomArtwork()
        .map { list -> list.associateBy { it.mediaId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _session = MutableStateFlow<UserSessionEntity?>(null)
    val session: StateFlow<UserSessionEntity?> = _session.asStateFlow()

    private val _watchPartyCode = MutableStateFlow("")
    val watchPartyCode: StateFlow<String> = _watchPartyCode.asStateFlow()

    private val _profiles = MutableStateFlow(listOf(UserProfile("p1", "الرئيسي"), UserProfile("p2", "الأطفال", true)))
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(_profiles.value.first())
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    private val _rawMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawSeries = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawDisney = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawHbo = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _addonCatalogRows = MutableStateFlow<List<AddonCatalogRow>>(emptyList())

    private fun applyCustomizations(
        list: List<MediaItem>,
        artMap: Map<String, CustomArtworkEntity>,
        s: AppSettings
    ): List<MediaItem> = list.map { item ->
        val art = artMap[item.id]
        item.copy(
            customPosterUrl = art?.posterUrl ?: item.customPosterUrl,
            customBackdropUrl = art?.backdropUrl ?: item.customBackdropUrl,
            customLogoUrl = art?.logoUrl ?: item.customLogoUrl,
            customTitle = art?.customTitle ?: item.customTitle,
            useEnglishTitle = s.preferEnglishTitles
        )
    }

    val trendingMovies: StateFlow<List<MediaItem>> = combine(_rawMovies, customArtworkMap, _settings) { l, a, s ->
        applyCustomizations(l, a, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val trendingSeries: StateFlow<List<MediaItem>> = combine(_rawSeries, customArtworkMap, _settings) { l, a, s ->
        applyCustomizations(l, a, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val disneyRow: StateFlow<List<MediaItem>> = combine(_rawDisney, customArtworkMap, _settings) { l, a, s ->
        applyCustomizations(l, a, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hboRow: StateFlow<List<MediaItem>> = combine(_rawHbo, customArtworkMap, _settings) { l, a, s ->
        applyCustomizations(l, a, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val addonCatalogRows: StateFlow<List<AddonCatalogRow>> = combine(_addonCatalogRows, customArtworkMap, _settings) { rows, a, s ->
        rows.map { it.copy(items = applyCustomizations(it.items, a, s)) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val library: StateFlow<List<MediaItem>> = combine(dao.observeLibrary(), customArtworkMap, _settings) { entities, art, s ->
        applyCustomizations(
            entities.map { e ->
                MediaItem(e.id, e.imdbId, e.tmdbId, e.type, e.titleAr, e.titleEn, e.posterUrl, e.backdropUrl, "", e.overviewAr, "", e.rating, "", e.year)
            },
            art,
            s
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            val savedSession = dao.getSession()
            _session.value = savedSession
            if (dao.getEnabledAddons().isEmpty()) {
                dao.upsertAddon(AddonEntity("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "الكتالوج الرسمي للأفلام والمسلسلات"))
                dao.upsertAddon(AddonEntity("org.stremio.opensubtitlesv3", "OpenSubtitles v3", "https://opensubtitles-v3.strem.io/manifest.json", "ترجمات عربية وعالمية تلقائية"))
            }
            if (savedSession != null) syncAllStremioData(savedSession.authKey)
            refreshHome()
        }
        viewModelScope.launch {
            while (true) {
                delay(60_000L)
                _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { syncAllStremioData(it) }
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            val key = _settings.value.tmdbApiKey
            launch { _rawMovies.value = tmdbEngine.getTrending("movie", key) }
            launch { _rawSeries.value = tmdbEngine.getTrending("series", key) }
            launch { _rawDisney.value = tmdbEngine.getByNetwork(2739, key) }
            launch { _rawHbo.value = tmdbEngine.getByNetwork(49, key) }
            launch { _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons()) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
    }

    fun setWatchPartyCode(code: String) {
        _watchPartyCode.value = code.uppercase().take(6)
    }

    fun createWatchPartyCode() {
        _watchPartyCode.value = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
    }

    fun selectProfile(p: UserProfile) {
        _activeProfile.value = p
    }

    fun addProfile(name: String, isKids: Boolean = false) {
        if (_profiles.value.size < 5 && name.isNotBlank()) {
            _profiles.update { it + UserProfile("p_${System.currentTimeMillis()}", name, isKids) }
        }
    }

    fun addStremioAddon(url: String) {
        viewModelScope.launch {
            stremioEngine.fetchManifest(url)?.let {
                dao.upsertAddon(it)
                _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons())
            }
        }
    }

    fun addCloudStreamRepo(repoUrl: String) {
        viewModelScope.launch {
            cloudStreamEngine.parseRepository(repoUrl).forEach { dao.upsertAddon(it) }
        }
    }

    fun updateAllAddons() {
        viewModelScope.launch {
            for (a in dao.getEnabledAddons()) {
                if (a.type == "stremio") {
                    stremioEngine.fetchManifest(a.url)?.let {
                        dao.upsertAddon(it.copy(isEnabled = a.isEnabled, orderIndex = a.orderIndex))
                    }
                } else {
                    cloudStreamEngine.parseRepository(a.url).forEach { dao.upsertAddon(it) }
                }
            }
            _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons())
        }
    }

    fun toggleAddon(addon: AddonEntity) {
        viewModelScope.launch { dao.upsertAddon(addon.copy(isEnabled = !addon.isEnabled)) }
    }

    fun deleteAddon(id: String) {
        viewModelScope.launch { dao.deleteAddon(id) }
    }

    fun loginStremio(email: String, pass: String) {
        viewModelScope.launch {
            stremioEngine.loginStremio(email, pass)?.let { s ->
                dao.saveSession(s)
                _session.value = s
                syncAllStremioData(s.authKey)
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
            stremioEngine.fetchUserAddonsFromAccount(authKey).forEach { dao.upsertAddon(it) }
            stremioEngine.fetchUserLibraryFromAccount(authKey).forEach { dao.upsertLibraryItem(it) }
            _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons())
        }
    }

    suspend fun search(query: String): List<MediaItem> =
        applyCustomizations(tmdbEngine.searchArabic(query, _settings.value.tmdbApiKey), customArtworkMap.value, _settings.value)

    suspend fun loadFullDetails(item: MediaItem): MediaItem {
        val enriched = tmdbEngine.enrichDetails(item, _settings.value)
        val art = dao.getCustomArtwork(enriched.id)
        return enriched.copy(
            customPosterUrl = art?.posterUrl ?: enriched.customPosterUrl,
            customBackdropUrl = art?.backdropUrl ?: enriched.customBackdropUrl,
            customLogoUrl = art?.logoUrl ?: enriched.customLogoUrl,
            customTitle = art?.customTitle ?: enriched.customTitle,
            useEnglishTitle = _settings.value.preferEnglishTitles
        )
    }

    suspend fun loadPersonDetails(personId: Int): PersonDetails? = tmdbEngine.getPersonDetails(personId, _settings.value)
    suspend fun loadNetworkWorks(networkId: Int): List<MediaItem> = tmdbEngine.getByNetwork(networkId, _settings.value.tmdbApiKey)
    suspend fun loadEpisodes(item: MediaItem, season: Int): List<EpisodeItem> = tmdbEngine.getSeasonEpisodes(item.tmdbId, season, _settings.value.tmdbApiKey)

    suspend fun fetchStreams(item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        var imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank() && item.tmdbId != 0) {
            imdb = tmdbEngine.enrichDetails(item, _settings.value).imdbId
        }
        if (imdb.isBlank()) {
            imdb = stremioEngine.resolveImdbIdFromCinemeta(item.type, item.titleEn.ifBlank { item.titleAr })
        }
        val baseId = imdb.ifBlank { item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        val enabled = dao.getEnabledAddons()
        val s1 = stremioEngine.fetchStreams(enabled, item.type, mediaId, _settings.value.torrentEnabled)
        val s2 = enabled.filter { it.type == "cloudstream" }.flatMap {
            cloudStreamEngine.fetchStreamsFromPlugin(it, item.titleEn, item.titleAr, imdb, item.type, season, episode)
        }
        return (s1 + s2).distinctBy { it.url }
    }

    suspend fun fetchSubtitles(item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> {
        val baseId = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        return stremioEngine.fetchSubtitles(dao.getEnabledAddons(), item.type, mediaId)
    }

    fun saveProgress(item: MediaItem, season: Int, episode: Int, pos: Long, dur: Long, streamUrl: String = "", addonName: String = "") {
        viewModelScope.launch {
            dao.upsertProgress(WatchProgressEntity("${item.id}_${season}_$episode", item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode, pos, dur, streamUrl, addonName))
            dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type))
        }
    }

    fun markAsWatched(item: MediaItem, season: Int, episode: Int) {
        viewModelScope.launch {
            val key = "${item.id}_${season}_$episode"
            if (item.type == "series") {
                dao.upsertProgress(WatchProgressEntity(key, item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode + 1, 0L, 1440000L))
            } else {
                dao.deleteProgress(key)
            }
            dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type))
        }
    }

    fun removeProgress(key: String) {
        viewModelScope.launch { dao.deleteProgress(key) }
    }

    fun toggleLibrary(item: MediaItem) {
        viewModelScope.launch {
            val existing = dao.getAllLibrary().any { it.id == item.id }
            val entity = LibraryItemEntity(item.id, item.imdbId, item.tmdbId, item.type, item.titleAr.ifBlank { item.displayTitle }, item.titleEn.ifBlank { item.displayTitle }, item.displayPoster, item.displayBackdrop, item.overviewAr, item.rating, item.year)
            if (existing) {
                dao.deleteLibraryItem(item.id)
                _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioEngine.pushLibraryItemToAccount(it, entity, true) }
            } else {
                dao.upsertLibraryItem(entity)
                _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioEngine.pushLibraryItemToAccount(it, entity, false) }
            }
        }
    }

    suspend fun loadArtworkGallery(item: MediaItem): ArtworkOptions {
        val enriched = if (item.tmdbId == 0) tmdbEngine.enrichDetails(item, _settings.value) else item
        return tmdbEngine.fetchArtworkOptions(enriched.tmdbId, enriched.type, _settings.value.tmdbApiKey)
    }

    fun saveCustomArtwork(mediaId: String, poster: String, backdrop: String, logo: String = "", customTitle: String = "") {
        viewModelScope.launch { dao.saveCustomArtwork(CustomArtworkEntity(mediaId, poster, backdrop, logo, customTitle)) }
    }

    suspend fun loadCalendarEntries(year: Int, month: Int): List<CalendarEntry> {
        val key = _settings.value.tmdbApiKey
        val shows = (library.value.filter { it.type == "series" } + _rawSeries.value.take(6)).distinctBy { it.id }
        val entries = mutableListOf<CalendarEntry>()
        val targetPrefix = String.format("%04d-%02d", year, month)
        for (show in shows) {
            val details = tmdbEngine.enrichDetails(show, _settings.value)
            for (ep in tmdbEngine.getSeasonEpisodes(details.tmdbId, details.seasonsCount.coerceAtLeast(1), key)) {
                if (ep.released.startsWith(targetPrefix)) {
                    val day = ep.released.takeLast(2).toIntOrNull() ?: continue
                    entries.add(CalendarEntry(day, ep.released, details, ep.season, ep.episode, ep.title))
                }
            }
        }
        return entries
    }
}
