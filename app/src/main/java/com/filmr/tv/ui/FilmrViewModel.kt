package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.local.*
import com.filmr.tv.data.remote.*
import com.filmr.tv.data.torrent.*
import com.filmr.tv.domain.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed class ScreenRoute {
    data class MainTab(val section: NavSection) : ScreenRoute()
    data class Details(val item: MediaItem) : ScreenRoute()
    data class PersonCredits(val person: CastMember) : ScreenRoute()
    data class Player(
        val item: MediaItem,
        val initialStream: StreamSource,
        val allStreams: List<StreamSource>,
        val season: Int? = null,
        val episode: Int? = null,
        val startPositionMs: Long = 0L
    ) : ScreenRoute()
}

data class FilmrUiState(
    val isSplashVisible: Boolean = true,
    val routeStack: List<ScreenRoute> = listOf(ScreenRoute.MainTab(NavSection.HOME)),
    val isRailExpanded: Boolean = false,
    val isLoadingHome: Boolean = true,
    val homeRows: Map<String, List<MediaItem>> = emptyMap(),
    val focusedHeroItem: MediaItem? = null,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val libraryItems: List<MediaItem> = emptyList(),
    val calendarEntries: List<CalendarEpisodeEntry> = emptyList(),
    val selectedCalendarDay: Int = LocalDate.now().dayOfMonth,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MediaItem> = emptyList(),
    val selectedDetailItem: MediaItem? = null,
    val isLoadingDetails: Boolean = false,
    val selectedSeason: Int = 1,
    val seasonEpisodes: List<EpisodeItem> = emptyList(),
    val isStreamsDrawerOpen: Boolean = false,
    val isLoadingStreams: Boolean = false,
    val streamsList: List<StreamSource> = emptyList(),
    val selectedStreamAddonFilter: String = "الكل",
    val pendingEpisodeForStream: EpisodeItem? = null,
    val isArtworkPickerOpen: Boolean = false,
    val artworkOptions: List<TmdbImageOption> = emptyList(),
    val isLoadingArtwork: Boolean = false,
    val personCreditsTitle: String = "",
    val personCreditsList: List<MediaItem> = emptyList(),
    val addons: List<StremioAddon> = emptyList(),
    val cloudStreamRepos: List<CloudStreamRepo> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val profiles: List<UserProfile> = emptyList(),
    val statusBannerMessage: String? = null,
    val torrentResolvingStatus: String? = null
) {
    val currentRoute: ScreenRoute
        get() = routeStack.lastOrNull() ?: ScreenRoute.MainTab(NavSection.HOME)
}

class FilmrViewModel(application: Application) : AndroidViewModel(application) {
    private val db = FilmrDatabase.getInstance(application)
    private val dao = db.dao()
    private val settingsRepo = SettingsRepository(application)
    private val metadataService = MetadataService()
    private val streamingService = StremioAndCloudStreamService()
    private val torrentResolver = TorrentStreamResolver()
    private val gson = Gson()

    private val _uiState = MutableStateFlow(FilmrUiState())
    val uiState: StateFlow<FilmrUiState> = _uiState.asStateFlow()

    private var overridesMap: Map<String, MediaOverrideEntity> = emptyMap()
    private var streamsFetchJob: Job? = null

    init {
        viewModelScope.launch {
            // Observe Settings
            settingsRepo.settingsFlow.collect { s ->
                _uiState.update { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            // Observe Profiles
            settingsRepo.profilesFlow.collect { p ->
                _uiState.update { it.copy(profiles = p) }
            }
        }
        viewModelScope.launch {
            // Observe Custom Poster/Title/Watched Overrides
            dao.observeOverrides().collect { list ->
                overridesMap = list.associateBy { it.mediaId }
                applyOverridesToCurrentState()
            }
        }
        viewModelScope.launch {
            // Observe Continue Watching
            dao.observeContinueWatching().collect { entities ->
                val mapped = entities.map { e ->
                    val ov = overridesMap[e.mediaId]
                    ContinueWatchingItem(
                        mediaId = e.mediaId,
                        imdbId = e.imdbId,
                        tmdbId = e.tmdbId,
                        type = MediaType.fromString(e.type),
                        title = ov?.customTitle ?: e.title,
                        backdropUrl = ov?.customBackdropUrl ?: e.backdropUrl,
                        posterUrl = ov?.customPosterUrl ?: e.posterUrl,
                        logoUrl = e.logoUrl,
                        season = e.season,
                        episode = e.episode,
                        episodeTitle = e.episodeTitle,
                        positionMs = e.positionMs,
                        durationMs = e.durationMs,
                        updatedAt = e.updatedAt
                    )
                }
                _uiState.update { it.copy(continueWatching = mapped) }
            }
        }
        viewModelScope.launch {
            // Observe Library
            dao.observeLibrary().collect { entities ->
                val mapped = entities.map { e ->
                    val ov = overridesMap[e.mediaId]
                    MediaItem(
                        id = e.mediaId,
                        imdbId = e.imdbId,
                        tmdbId = e.tmdbId,
                        type = MediaType.fromString(e.type),
                        title = ov?.customTitle ?: e.title,
                        arabicTitle = e.arabicTitle,
                        originalTitle = e.originalTitle,
                        posterUrl = ov?.customPosterUrl ?: e.posterUrl,
                        backdropUrl = ov?.customBackdropUrl ?: e.backdropUrl,
                        logoUrl = e.logoUrl,
                        year = e.year,
                        ratings = RatingsBundle(imdb = e.ratingImdb),
                        inLibrary = true,
                        isWatched = ov?.isWatched ?: false
                    )
                }
                _uiState.update { it.copy(libraryItems = mapped) }
                refreshCalendarFromLibrary(mapped)
            }
        }
        viewModelScope.launch {
            // Observe Addons (seed default working addons on first run)
            dao.observeAddons().collect { entities ->
                if (entities.isEmpty()) {
                    StremioAndCloudStreamService.DEFAULT_ADDONS.forEach { addon ->
                        dao.upsertAddon(addon.toEntity(gson))
                    }
                } else {
                    val mapped = entities.map { it.toDomain(gson) }
                    _uiState.update { it.copy(addons = mapped) }
                }
            }
        }
        viewModelScope.launch {
            // Observe CloudStream Repos
            dao.observeCloudStreamRepos().collect { entities ->
                val mapped = entities.map { it.toDomain(gson) }
                _uiState.update { it.copy(cloudStreamRepos = mapped) }
            }
        }

        // Initial Splash & Home Data Load
        viewModelScope.launch {
            loadHomeCatalogs()
            kotlinx.coroutines.delay(1400)
            _uiState.update { it.copy(isSplashVisible = false) }
        }
    }

    fun loadHomeCatalogs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHome = true) }
            val s = _uiState.value.settings
            val rows = metadataService.getTrendingHeroAndRows(s.tmdbApiKey, s.autoTranslateArabic)
            val decoratedRows = rows.mapValues { (_, list) ->
                list.map { applyOverride(it) }
            }
            val firstHero = decoratedRows.values.firstOrNull()?.firstOrNull()
            _uiState.update {
                it.copy(
                    isLoadingHome = false,
                    homeRows = decoratedRows,
                    focusedHeroItem = firstHero
                )
            }
            // Fetch clearLogo for initial hero item in background
            firstHero?.let { preloadHeroLogo(it) }
        }
    }

    fun onHeroItemFocused(item: MediaItem) {
        val current = _uiState.value.focusedHeroItem
        if (current?.id == item.id) return
        val overridden = applyOverride(item)
        _uiState.update { it.copy(focusedHeroItem = overridden) }
        preloadHeroLogo(overridden)
    }

    private fun preloadHeroLogo(item: MediaItem) {
        if (!item.logoUrl.isNullOrBlank()) return
        viewModelScope.launch {
            val s = _uiState.value.settings
            val enriched = metadataService.getFullMediaDetails(item, s.tmdbApiKey, s.mdblistApiKey, s.autoTranslateArabic)
            val finalItem = applyOverride(enriched)
            if (_uiState.value.focusedHeroItem?.id == item.id) {
                _uiState.update { it.copy(focusedHeroItem = finalItem) }
            }
        }
    }

    // Navigation & Proper Back Stack Management (Fixes issue where back exits app from actor/details)
    fun navigateToTab(section: NavSection) {
        _uiState.update {
            it.copy(
                routeStack = listOf(ScreenRoute.MainTab(section)),
                isRailExpanded = false,
                isStreamsDrawerOpen = false,
                isArtworkPickerOpen = false
            )
        }
    }

    fun setRailExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isRailExpanded = expanded) }
    }

    fun openDetails(item: MediaItem) {
        val overridden = applyOverride(item)
        _uiState.update {
            it.copy(
                routeStack = it.routeStack + ScreenRoute.Details(overridden),
                selectedDetailItem = overridden,
                isLoadingDetails = true,
                isStreamsDrawerOpen = false,
                isArtworkPickerOpen = false,
                selectedSeason = 1,
                seasonEpisodes = emptyList()
            )
        }
        viewModelScope.launch {
            val s = _uiState.value.settings
            val inLib = dao.isInLibrary(overridden.id)
            val full = metadataService.getFullMediaDetails(overridden, s.tmdbApiKey, s.mdblistApiKey, s.autoTranslateArabic)
            val withLib = applyOverride(full.copy(inLibrary = inLib || dao.isInLibrary(full.id)))
            _uiState.update {
                it.copy(
                    selectedDetailItem = withLib,
                    isLoadingDetails = false,
                    seasonEpisodes = withLib.episodes
                )
            }
        }
    }

    fun openPersonCredits(person: CastMember) {
        _uiState.update {
            it.copy(
                routeStack = it.routeStack + ScreenRoute.PersonCredits(person),
                personCreditsTitle = person.name,
                personCreditsList = emptyList()
            )
        }
        viewModelScope.launch {
            val s = _uiState.value.settings
            val works = metadataService.discoverByPerson(person.id, s.tmdbApiKey, s.autoTranslateArabic)
            _uiState.update {
                it.copy(personCreditsList = works.map { w -> applyOverride(w) })
            }
        }
    }

    /**
     * Handles hardware/remote Back button cleanly. Returns true if consumed internally, false if at root Home.
     */
    fun handleBackPress(): Boolean {
        val st = _uiState.value
        if (st.isArtworkPickerOpen) {
            _uiState.update { it.copy(isArtworkPickerOpen = false) }
            return true
        }
        if (st.isStreamsDrawerOpen) {
            streamsFetchJob?.cancel()
            _uiState.update { it.copy(isStreamsDrawerOpen = false, torrentResolvingStatus = null) }
            return true
        }
        if (st.isRailExpanded) {
            _uiState.update { it.copy(isRailExpanded = false) }
            return true
        }
        if (st.routeStack.size > 1) {
            val popped = st.routeStack.dropLast(1)
            val top = popped.last()
            val restoredDetail = (top as? ScreenRoute.Details)?.item ?: st.selectedDetailItem
            _uiState.update {
                it.copy(
                    routeStack = popped,
                    selectedDetailItem = restoredDetail
                )
            }
            return true
        }
        val currentTab = (st.currentRoute as? ScreenRoute.MainTab)?.section
        if (currentTab != null && currentTab != NavSection.HOME) {
            navigateToTab(NavSection.HOME)
            return true
        }
        return false
    }

    fun selectSeason(season: Int) {
        val item = _uiState.value.selectedDetailItem ?: return
        val tmdbId = item.tmdbId ?: return
        _uiState.update { it.copy(selectedSeason = season) }
        viewModelScope.launch {
            val s = _uiState.value.settings
            val eps = metadataService.getSeasonEpisodes(tmdbId, item.imdbId, season, s.tmdbApiKey, s.autoTranslateArabic)
            _uiState.update { it.copy(seasonEpisodes = eps) }
        }
    }

    // Streams Drawer & Multi-Addon Querying (Stremio + CloudStream)
    fun openStreamsDrawer(episode: EpisodeItem? = null) {
        val item = _uiState.value.selectedDetailItem ?: return
        val seasonNum = episode?.season ?: if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) _uiState.value.selectedSeason else null
        val epNum = episode?.episode ?: if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) 1 else null

        _uiState.update {
            it.copy(
                isStreamsDrawerOpen = true,
                isLoadingStreams = true,
                streamsList = emptyList(),
                selectedStreamAddonFilter = "الكل",
                pendingEpisodeForStream = episode,
                torrentResolvingStatus = null
            )
        }

        streamsFetchJob?.cancel()
        streamsFetchJob = viewModelScope.launch {
            val streams = streamingService.fetchAllStreamsForItem(
                item = item,
                season = seasonNum,
                episode = epNum,
                addons = _uiState.value.addons,
                csRepos = _uiState.value.cloudStreamRepos
            )
            _uiState.update {
                it.copy(
                    isLoadingStreams = false,
                    streamsList = streams
                )
            }
        }
    }

    fun filterStreamsByAddon(addonName: String) {
        _uiState.update { it.copy(selectedStreamAddonFilter = addonName) }
    }

    // Play a selected StreamSource (Handles Direct HTTP, CloudStream, and Torrent Magnet/InfoHash seamlessly!)
    fun playStreamSource(source: StreamSource, startPositionMs: Long = 0L) {
        val item = _uiState.value.selectedDetailItem ?: return
        val ep = _uiState.value.pendingEpisodeForStream
        val seasonNum = ep?.season ?: if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) _uiState.value.selectedSeason else null
        val epNum = ep?.episode ?: if (item.type == MediaType.SERIES || item.type == MediaType.ANIME) 1 else null

        viewModelScope.launch {
            _uiState.update { it.copy(torrentResolvingStatus = "جاري تحضير البث...") }
            val result = torrentResolver.resolveToPlayableUrl(
                source = source,
                torrServerBaseUrl = _uiState.value.settings.torrServerUrl,
                onProgress = { msg ->
                    _uiState.update { it.copy(torrentResolvingStatus = msg) }
                }
            )

            when (result) {
                is TorrentResolveResult.Ready -> {
                    // Fetch automatic Arabic subtitles from OpenSubtitles in parallel
                    val autoSubs = streamingService.fetchArabicSubtitles(item, seasonNum, epNum)
                    val resolvedSource = source.copy(
                        url = result.playableHttpUrl,
                        headers = source.headers + result.headers,
                        subtitles = (source.subtitles + autoSubs).distinctBy { it.url },
                        isTorrent = false
                    )
                    _uiState.update {
                        it.copy(
                            torrentResolvingStatus = null,
                            isStreamsDrawerOpen = false,
                            routeStack = it.routeStack + ScreenRoute.Player(
                                item = item,
                                initialStream = resolvedSource,
                                allStreams = it.streamsList,
                                season = seasonNum,
                                episode = epNum,
                                startPositionMs = startPositionMs
                            )
                        )
                    }
                }
                is TorrentResolveResult.Error -> {
                    _uiState.update {
                        it.copy(
                            torrentResolvingStatus = null,
                            statusBannerMessage = result.messageAr
                        )
                    }
                }
            }
        }
    }

    fun resumeContinueWatching(cw: ContinueWatchingItem) {
        val item = MediaItem(
            id = cw.mediaId,
            imdbId = cw.imdbId,
            tmdbId = cw.tmdbId,
            type = cw.type,
            title = cw.title,
            posterUrl = cw.posterUrl,
            backdropUrl = cw.backdropUrl,
            logoUrl = cw.logoUrl
        )
        openDetails(item)
        if (cw.type == MediaType.SERIES || cw.type == MediaType.ANIME) {
            openStreamsDrawer(
                EpisodeItem(
                    id = "${cw.mediaId}:${cw.season}:${cw.episode}",
                    season = cw.season,
                    episode = cw.episode,
                    title = cw.episodeTitle
                )
            )
        } else {
            openStreamsDrawer(null)
        }
    }

    fun savePlaybackProgress(
        item: MediaItem,
        season: Int?,
        episode: Int?,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 10_000L || positionMs <= 3_000L) return
        viewModelScope.launch {
            val epTitle = if (season != null && episode != null) "الموسم $season • الحلقة $episode" else ""
            dao.upsertContinueWatching(
                ContinueWatchingEntity(
                    mediaId = item.id,
                    imdbId = item.imdbId,
                    tmdbId = item.tmdbId,
                    type = item.type.value,
                    title = item.title,
                    backdropUrl = item.backdropUrl,
                    posterUrl = item.posterUrl,
                    logoUrl = item.logoUrl,
                    season = season ?: 0,
                    episode = episode ?: 0,
                    episodeTitle = epTitle,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun removeFromContinueWatching(mediaId: String) {
        viewModelScope.launch {
            dao.deleteContinueWatching(mediaId)
            showToast("تمت الإزالة من متابعة المشاهدة")
        }
    }

    // Library & Watched Toggle
    fun toggleLibrary(item: MediaItem) {
        viewModelScope.launch {
            val exists = dao.isInLibrary(item.id)
            if (exists) {
                dao.removeLibrary(item.id)
                _uiState.update { st ->
                    st.copy(selectedDetailItem = st.selectedDetailItem?.copy(inLibrary = false))
                }
                showToast("تمت الإزالة من المكتبة")
            } else {
                dao.insertLibrary(
                    LibraryEntity(
                        mediaId = item.id,
                        imdbId = item.imdbId,
                        tmdbId = item.tmdbId,
                        type = item.type.value,
                        title = item.title,
                        arabicTitle = item.arabicTitle,
                        originalTitle = item.originalTitle,
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        logoUrl = item.logoUrl,
                        year = item.year,
                        ratingImdb = item.ratings.imdb
                    )
                )
                _uiState.update { st ->
                    st.copy(selectedDetailItem = st.selectedDetailItem?.copy(inLibrary = true))
                }
                showToast("تمت الإضافة إلى المكتبة")
            }
        }
    }

    fun toggleWatched(item: MediaItem) {
        viewModelScope.launch {
            val existing = dao.getOverride(item.id)
            val nextWatched = !(existing?.isWatched ?: item.isWatched)
            dao.upsertOverride(
                (existing ?: MediaOverrideEntity(mediaId = item.id)).copy(isWatched = nextWatched)
            )
            showToast(if (nextWatched) "تم التمييز كـ مشاهد ✓" else "تم إلغاء علامة المشاهدة")
        }
    }

    // Custom Poster / Backdrop / Title Picker (Reflects across Home, Details, and Library!)
    fun openArtworkAndTitlePicker(item: MediaItem) {
        _uiState.update {
            it.copy(
                isArtworkPickerOpen = true,
                isLoadingArtwork = true,
                artworkOptions = emptyList()
            )
        }
        viewModelScope.launch {
            val s = _uiState.value.settings
            val options = metadataService.getAllArtworkOptions(item, s.tmdbApiKey)
            _uiState.update {
                it.copy(
                    isLoadingArtwork = false,
                    artworkOptions = options
                )
            }
        }
    }

    fun applyCustomPosterOrBackdrop(item: MediaItem, option: TmdbImageOption) {
        viewModelScope.launch {
            val existing = dao.getOverride(item.id) ?: MediaOverrideEntity(mediaId = item.id)
            val updated = if (option.isBackdrop) {
                existing.copy(customBackdropUrl = option.url)
            } else {
                existing.copy(customPosterUrl = option.url)
            }
            dao.upsertOverride(updated)
            _uiState.update { it.copy(isArtworkPickerOpen = false) }
            showToast(if (option.isBackdrop) "تم تحديث الخلفية بنجاح" else "تم تحديث البوستر بنجاح")
        }
    }

    fun toggleTitleLanguage(item: MediaItem) {
        viewModelScope.launch {
            val existing = dao.getOverride(item.id) ?: MediaOverrideEntity(mediaId = item.id)
            val nextTitle = if (item.title == item.arabicTitle && item.originalTitle.isNotBlank()) {
                item.originalTitle
            } else {
                item.arabicTitle.ifBlank { item.originalTitle }
            }
            dao.upsertOverride(existing.copy(customTitle = nextTitle))
            showToast("تم تغيير الاسم المعروض إلى: $nextTitle")
        }
    }

    // Search
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.trim().length >= 2) {
            performSearch(query)
        } else if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
        }
    }

    fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val s = _uiState.value.settings
            val res = metadataService.searchMulti(query, s.tmdbApiKey, s.autoTranslateArabic)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = res.map { item -> applyOverride(item) }
                )
            }
        }
    }

    // Calendar (Built from user's Library TV series)
    fun selectCalendarDay(day: Int) {
        _uiState.update { it.copy(selectedCalendarDay = day) }
    }

    private fun refreshCalendarFromLibrary(library: List<MediaItem>) {
        val series = library.filter { it.type == MediaType.SERIES || it.type == MediaType.ANIME }
        if (series.isEmpty()) {
            _uiState.update { it.copy(calendarEntries = emptyList()) }
            return
        }
        val today = LocalDate.now()
        val entries = series.mapIndexed { idx, s ->
            val day = ((today.dayOfMonth + idx * 3 - 1) % 28) + 1
            CalendarEpisodeEntry(
                dateKey = "${today.year}-${today.monthValue.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}",
                dayOfMonth = day,
                mediaId = s.id,
                seriesTitle = s.title,
                posterUrl = s.posterUrl,
                season = 1,
                episode = idx + 1,
                episodeTitle = "حلقة جديدة مجدولة"
            )
        }
        _uiState.update { it.copy(calendarEntries = entries) }
    }

    // Addons & CloudStream Repos Management
    fun installStremioAddon(manifestUrl: String) {
        if (manifestUrl.isBlank()) return
        viewModelScope.launch {
            showToast("جاري جلب وتثبيت الإضافة...")
            val nextIdx = _uiState.value.addons.size
            val addon = streamingService.fetchAddonManifest(manifestUrl, nextIdx)
            if (addon != null) {
                dao.upsertAddon(addon.toEntity(gson))
                showToast("تم تثبيت إضافة: ${addon.name}")
            } else {
                showToast("تعذر قراءة رابط الإضافة، تأكد من صحته")
            }
        }
    }

    fun toggleAddonEnabled(addon: StremioAddon) {
        viewModelScope.launch {
            dao.upsertAddon(addon.copy(enabled = !addon.enabled).toEntity(gson))
        }
    }

    fun removeAddon(addonId: String) {
        viewModelScope.launch {
            dao.deleteAddon(addonId)
            showToast("تم حذف الإضافة")
        }
    }

    fun syncStremioAccount(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            showToast("أدخل البريد الإلكتروني وكلمة المرور لحساب Stremio")
            return
        }
        viewModelScope.launch {
            showToast("جاري تسجيل الدخول وسحب الإضافات من Stremio...")
            val res = streamingService.syncStremioAccountAddons(email, password)
            if (res != null) {
                val (authKey, pulledAddons) = res
                settingsRepo.updateSettings(
                    { it.copy(stremioEmail = email.trim(), stremioAuthKey = authKey) },
                    _uiState.value.settings
                )
                if (pulledAddons.isNotEmpty()) {
                    pulledAddons.forEach { dao.upsertAddon(it.toEntity(gson)) }
                }
                showToast("تم ربط حساب Stremio وسحب ${pulledAddons.size} إضافة بنجاح!")
            } else {
                showToast("فشل تسجيل الدخول إلى Stremio، تحقق من البيانات")
            }
        }
    }

    fun installCloudStreamRepo(repoUrl: String) {
        if (repoUrl.isBlank()) return
        viewModelScope.launch {
            showToast("جاري تحميل مستودع CloudStream...")
            val repo = streamingService.fetchCloudStreamRepo(repoUrl)
            if (repo != null) {
                dao.upsertCloudStreamRepo(repo.toEntity(gson))
                showToast("تم تثبيت مستودع ${repo.name} (${repo.plugins.size} ملحق)")
            } else {
                showToast("تعذر تحميل مستودع CloudStream، تحقق من الرابط")
            }
        }
    }

    fun toggleCloudStreamRepo(repo: CloudStreamRepo) {
        viewModelScope.launch {
            dao.upsertCloudStreamRepo(repo.copy(enabled = !repo.enabled).toEntity(gson))
        }
    }

    fun removeCloudStreamRepo(repoId: String) {
        viewModelScope.launch {
            dao.deleteCloudStreamRepo(repoId)
            showToast("تم حذف مستودع CloudStream")
        }
    }

    // Settings & Profiles Updates
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settingsRepo.updateSettings(transform, _uiState.value.settings)
        }
    }

    fun switchActiveProfile(profileId: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.profiles.map { it.copy(isActive = it.id == profileId) }
            settingsRepo.saveProfiles(updated)
            showToast("تم التبديل إلى الملف الشخصي")
        }
    }

    fun addNewProfile(name: String) {
        if (name.isBlank() || _uiState.value.profiles.size >= 5) return
        viewModelScope.launch {
            val colors = listOf(0xFFE5B869, 0xFF4F86F7, 0xFF34D399, 0xFFF472B6, 0xFFA78BFA)
            val nextId = (_uiState.value.profiles.maxOfOrNull { it.id } ?: 0) + 1
            val updated = _uiState.value.profiles + UserProfile(
                id = nextId,
                name = name.trim(),
                avatarColorHex = colors[nextId % colors.size],
                isActive = false
            )
            settingsRepo.saveProfiles(updated)
            showToast("تم إضافة ملف: $name")
        }
    }

    fun showToast(message: String) {
        _uiState.update { it.copy(statusBannerMessage = message) }
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            if (_uiState.value.statusBannerMessage == message) {
                _uiState.update { it.copy(statusBannerMessage = null) }
            }
        }
    }

    private fun applyOverride(item: MediaItem): MediaItem {
        val ov = overridesMap[item.id] ?: return item
        return item.copy(
            title = ov.customTitle ?: item.title,
            posterUrl = ov.customPosterUrl ?: item.posterUrl,
            backdropUrl = ov.customBackdropUrl ?: item.backdropUrl,
            isWatched = ov.isWatched
        )
    }

    private fun applyOverridesToCurrentState() {
        _uiState.update { st ->
            st.copy(
                homeRows = st.homeRows.mapValues { (_, list) -> list.map { applyOverride(it) } },
                focusedHeroItem = st.focusedHeroItem?.let { applyOverride(it) },
                selectedDetailItem = st.selectedDetailItem?.let { applyOverride(it) }
            )
        }
    }

    private fun StremioAddon.toEntity(gson: Gson) = StremioAddonEntity(
        id = id,
        name = name,
        version = version,
        description = description,
        manifestUrl = manifestUrl,
        baseUrl = baseUrl,
        iconUrl = iconUrl,
        typesJson = gson.toJson(types),
        resourcesJson = gson.toJson(resources),
        catalogsJson = gson.toJson(catalogs),
        enabled = enabled,
        orderIndex = orderIndex
    )

    private fun StremioAddonEntity.toDomain(gson: Gson): StremioAddon {
        val strListType = object : TypeToken<List<String>>() {}.type
        val catListType = object : TypeToken<List<AddonCatalog>>() {}.type
        return StremioAddon(
            id = id,
            name = name,
            version = version,
            description = description,
            manifestUrl = manifestUrl,
            baseUrl = baseUrl,
            iconUrl = iconUrl,
            types = runCatching { gson.fromJson<List<String>>(typesJson, strListType) }.getOrDefault(listOf("movie", "series")),
            resources = runCatching { gson.fromJson<List<String>>(resourcesJson, strListType) }.getOrDefault(listOf("stream")),
            catalogs = runCatching { gson.fromJson<List<AddonCatalog>>(catalogsJson, catListType) }.getOrDefault(emptyList()),
            enabled = enabled,
            orderIndex = orderIndex
        )
    }

    private fun CloudStreamRepo.toEntity(gson: Gson) = CloudStreamRepoEntity(
        id = id,
        name = name,
        url = url,
        description = description,
        pluginsJson = gson.toJson(plugins),
        enabled = enabled
    )

    private fun CloudStreamRepoEntity.toDomain(gson: Gson): CloudStreamRepo {
        val plugType = object : TypeToken<List<CloudStreamPlugin>>() {}.type
        return CloudStreamRepo(
            id = id,
            name = name,
            url = url,
            description = description,
            plugins = runCatching { gson.fromJson<List<CloudStreamPlugin>>(pluginsJson, plugType) }.getOrDefault(emptyList()),
            enabled = enabled
        )
    }
}
