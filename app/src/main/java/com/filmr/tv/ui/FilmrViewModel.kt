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
    private val dao = FilmrDatabase.get(app).dao()
    private val stremioEngine = StremioEngine()
    private val cloudStreamEngine = CloudStreamEngine()
    private val tmdbEngine = TmdbArtworkEngine()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val addons = dao.observeAddons().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val watchProgress = dao.observeProgress().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history = dao.observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val customArtworkMap = dao.observeAllCustomArtwork()
        .map { l -> l.associateBy { it.mediaId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _session = MutableStateFlow<UserSessionEntity?>(null)
    val session = _session.asStateFlow()

    private val _watchPartyCode = MutableStateFlow("")
    val watchPartyCode = _watchPartyCode.asStateFlow()

    private val _profiles = MutableStateFlow(listOf(UserProfile("p1", "الرئيسي"), UserProfile("p2", "الأطفال", true)))
    val profiles = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow(_profiles.value.first())
    val activeProfile = _activeProfile.asStateFlow()

    private val _rawMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawSeries = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawDisney = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _rawHbo = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _addonCatalogRows = MutableStateFlow<List<AddonCatalogRow>>(emptyList())

    private fun applyCustom(list: List<MediaItem>, art: Map<String, CustomArtworkEntity>, s: AppSettings) = list.map { i ->
        val a = art[i.id]
        i.copy(customPosterUrl = a?.posterUrl ?: i.customPosterUrl, customBackdropUrl = a?.backdropUrl ?: i.customBackdropUrl, customLogoUrl = a?.logoUrl ?: i.customLogoUrl, customTitle = a?.customTitle ?: i.customTitle, useEnglishTitle = s.preferEnglishTitles)
    }

    val trendingMovies = combine(_rawMovies, customArtworkMap, _settings) { l, a, s -> applyCustom(l, a, s) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val trendingSeries = combine(_rawSeries, customArtworkMap, _settings) { l, a, s -> applyCustom(l, a, s) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val disneyRow = combine(_rawDisney, customArtworkMap, _settings) { l, a, s -> applyCustom(l, a, s) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hboRow = combine(_rawHbo, customArtworkMap, _settings) { l, a, s -> applyCustom(l, a, s) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val addonCatalogRows = combine(_addonCatalogRows, customArtworkMap, _settings) { r, a, s -> r.map { it.copy(items = applyCustom(it.items, a, s)) } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val library = combine(dao.observeLibrary(), customArtworkMap, _settings) { e, a, s ->
        applyCustom(e.map { MediaItem(it.id, it.imdbId, it.tmdbId, it.type, it.titleAr, it.titleEn, it.posterUrl, it.backdropUrl, "", it.overviewAr, "", it.rating, "", it.year) }, a, s)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val saved = dao.getSession()
            _session.value = saved
            if (dao.getEnabledAddons().isEmpty()) {
                dao.upsertAddon(AddonEntity("com.linvo.cinemeta", "Cinemeta", "https://v3-cinemeta.strem.io/manifest.json", "الكتالوج الرسمي"))
                dao.upsertAddon(AddonEntity("org.stremio.opensubtitlesv3", "OpenSubtitles v3", "https://opensubtitles-v3.strem.io/manifest.json", "ترجمات تلقائية"))
            }
            if (saved != null) syncAllStremioData(saved.authKey)
            refreshHome()
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

    fun updateSettings(transform: (AppSettings) -> AppSettings) { _settings.update(transform) }
    fun setWatchPartyCode(code: String) { _watchPartyCode.value = code.uppercase().take(6) }
    fun createWatchPartyCode() { _watchPartyCode.value = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("") }
    fun selectProfile(p: UserProfile) { _activeProfile.value = p }
    fun addProfile(name: String, isKids: Boolean = false) { if (_profiles.value.size < 5 && name.isNotBlank()) _profiles.update { it + UserProfile("p_${System.currentTimeMillis()}", name, isKids) } }
    fun addStremioAddon(url: String) { viewModelScope.launch { stremioEngine.fetchManifest(url)?.let { dao.upsertAddon(it); _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons()) } } }
    fun addCloudStreamRepo(repoUrl: String) { viewModelScope.launch { cloudStreamEngine.parseRepository(repoUrl).forEach { dao.upsertAddon(it) } } }
    fun updateAllAddons() { viewModelScope.launch { for (a in dao.getEnabledAddons()) { if (a.type == "stremio") stremioEngine.fetchManifest(a.url)?.let { dao.upsertAddon(it.copy(isEnabled = a.isEnabled, orderIndex = a.orderIndex)) } else cloudStreamEngine.parseRepository(a.url).forEach { dao.upsertAddon(it) } }; _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons()) } }
    fun toggleAddon(addon: AddonEntity) { viewModelScope.launch { dao.upsertAddon(addon.copy(isEnabled = !addon.isEnabled)) } }
    fun deleteAddon(id: String) { viewModelScope.launch { dao.deleteAddon(id) } }
    fun loginStremio(email: String, pass: String) { viewModelScope.launch { stremioEngine.loginStremio(email, pass)?.let { s -> dao.saveSession(s); _session.value = s; syncAllStremioData(s.authKey) } } }
    fun logoutStremio() { viewModelScope.launch { dao.clearSession(); _session.value = null } }
    fun syncAllStremioData(authKey: String) { viewModelScope.launch { stremioEngine.fetchUserAddonsFromAccount(authKey).forEach { dao.upsertAddon(it) }; stremioEngine.fetchUserLibraryFromAccount(authKey).forEach { dao.upsertLibraryItem(it) }; _addonCatalogRows.value = stremioEngine.fetchAddonCatalogs(dao.getEnabledAddons()) } }
    suspend fun search(query: String) = applyCustom(tmdbEngine.searchArabic(query, _settings.value.tmdbApiKey), customArtworkMap.value, _settings.value)
    suspend fun loadFullDetails(item: MediaItem): MediaItem { val e = tmdbEngine.enrichDetails(item, _settings.value); val a = dao.getCustomArtwork(e.id); return e.copy(customPosterUrl = a?.posterUrl ?: e.customPosterUrl, customBackdropUrl = a?.backdropUrl ?: e.customBackdropUrl, customLogoUrl = a?.logoUrl ?: e.customLogoUrl, customTitle = a?.customTitle ?: e.customTitle, useEnglishTitle = _settings.value.preferEnglishTitles) }
    suspend fun loadPersonDetails(personId: Int) = tmdbEngine.getPersonDetails(personId, _settings.value)
    suspend fun loadNetworkWorks(networkId: Int) = tmdbEngine.getByNetwork(networkId, _settings.value.tmdbApiKey)
    suspend fun loadEpisodes(item: MediaItem, season: Int) = tmdbEngine.getSeasonEpisodes(item.tmdbId, season, _settings.value.tmdbApiKey)

    suspend fun fetchStreams(item: MediaItem, season: Int, episode: Int): List<UnifiedStream> {
        var imdb = item.imdbId.ifBlank { if (item.id.startsWith("tt")) item.id else "" }
        if (imdb.isBlank() && item.tmdbId != 0) imdb = tmdbEngine.enrichDetails(item, _settings.value).imdbId
        if (imdb.isBlank()) imdb = stremioEngine.resolveImdbIdFromCinemeta(item.type, item.titleEn.ifBlank { item.titleAr })
        val baseId = imdb.ifBlank { item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        val enabled = dao.getEnabledAddons()
        val s1 = stremioEngine.fetchStreams(enabled, item.type, mediaId, _settings.value.torrentEnabled)
        val s2 = enabled.filter { it.type == "cloudstream" }.flatMap { cloudStreamEngine.fetchStreamsFromPlugin(it, item.titleEn, item.titleAr, imdb, item.type, season, episode) }
        return (s1 + s2).distinctBy { it.url }
    }

    suspend fun fetchSubtitles(item: MediaItem, season: Int, episode: Int): List<SubtitleTrack> {
        val baseId = item.imdbId.ifBlank { item.id }
        val mediaId = if (item.type == "series") "$baseId:$season:$episode" else baseId
        return stremioEngine.fetchSubtitles(dao.getEnabledAddons(), item.type, mediaId)
    }

    fun saveProgress(item: MediaItem, season: Int, episode: Int, pos: Long, dur: Long, streamUrl: String = "", addonName: String = "") { viewModelScope.launch { dao.upsertProgress(WatchProgressEntity("${item.id}_${season}_$episode", item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode, pos, dur, streamUrl, addonName)); dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type)) } }
    fun markAsWatched(item: MediaItem, season: Int, episode: Int) { viewModelScope.launch { val k = "${item.id}_${season}_$episode"; if (item.type == "series") dao.upsertProgress(WatchProgressEntity(k, item.id, item.imdbId, item.type, item.displayTitle, item.displayPoster, item.displayBackdrop, season, episode + 1, 0L, 1440000L)) else dao.deleteProgress(k); dao.upsertHistory(WatchHistoryEntity(item.id, item.id, item.displayTitle, item.displayPoster, item.type)) } }
    fun removeProgress(key: String) { viewModelScope.launch { dao.deleteProgress(key) } }
    fun toggleLibrary(item: MediaItem) { viewModelScope.launch { val ex = dao.getAllLibrary().any { it.id == item.id }; val ent = LibraryItemEntity(item.id, item.imdbId, item.tmdbId, item.type, item.titleAr.ifBlank { item.displayTitle }, item.titleEn.ifBlank { item.displayTitle }, item.displayPoster, item.displayBackdrop, item.overviewAr, item.rating, item.year); if (ex) { dao.deleteLibraryItem(item.id); _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioEngine.pushLibraryItemToAccount(it, ent, true) } } else { dao.upsertLibraryItem(ent); _session.value?.authKey?.takeIf { it.isNotBlank() }?.let { stremioEngine.pushLibraryItemToAccount(it, ent, false) } } } }
    suspend fun loadArtworkGallery(item: MediaItem) = tmdbEngine.fetchArtworkOptions((if (item.tmdbId == 0) tmdbEngine.enrichDetails(item, _settings.value) else item).tmdbId, item.type, _settings.value.tmdbApiKey)
    fun saveCustomArtwork(mediaId: String, poster: String, backdrop: String, logo: String = "", customTitle: String = "") { viewModelScope.launch { dao.saveCustomArtwork(CustomArtworkEntity(mediaId, poster, backdrop, logo, customTitle)) } }

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
