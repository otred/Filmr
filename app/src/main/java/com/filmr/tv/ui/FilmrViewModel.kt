package com.filmr.tv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.filmr.tv.data.*
import com.filmr.tv.engine.*
import com.filmr.tv.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilmrViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = FilmrDatabase.getInstance(application).dao()
    private val stremioEngine = StremioEngine()
    private val cloudStreamEngine = CloudStreamEngine(application)
    private val tmdbEngine = TmdbArtworkEngine()

    val addonsFlow = dao.observeAddons().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val historyFlow = dao.observeHistory().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessionFlow = dao.observeSession().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _featuredMovies = MutableStateFlow<List<MediaItem>>(emptyList())
    val featuredMovies: StateFlow<List<MediaItem>> = _featuredMovies

    private val _featuredSeries = MutableStateFlow<List<MediaItem>>(emptyList())
    val featuredSeries: StateFlow<List<MediaItem>> = _featuredSeries

    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia

    private val _artworkOptions = MutableStateFlow(ArtworkOptions())
    val artworkOptions: StateFlow<ArtworkOptions> = _artworkOptions

    private val _streams = MutableStateFlow<List<UnifiedStream>>(emptyList())
    val streams: StateFlow<List<UnifiedStream>> = _streams

    private val _subtitles = MutableStateFlow<List<SubtitleTrack>>(emptyList())
    val subtitles: StateFlow<List<SubtitleTrack>> = _subtitles

    private val _activeStream = MutableStateFlow<UnifiedStream?>(null)
    val activeStream: StateFlow<UnifiedStream?> = _activeStream

    private val _playerSettings = MutableStateFlow(PlayerSettings())
    val playerSettings: StateFlow<PlayerSettings> = _playerSettings

    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage

    private val _isLoadingStreams = MutableStateFlow(false)
    val isLoadingStreams: StateFlow<Boolean> = _isLoadingStreams

    init {
        viewModelScope.launch {
            ensureDefaultAddons()
            loadHomeCatalogs()
            dao.getSession()?.let { s ->
                _playerSettings.value = _playerSettings.value.copy(
                    seekStepSeconds = s.seekStepSeconds,
                    engine = runCatching { PlayerEngineType.valueOf(s.defaultEngine) }.getOrDefault(PlayerEngineType.EXOPLAYER)
                )
            }
        }
    }

    private suspend fun ensureDefaultAddons() {
        if (dao.getEnabledAddons().isEmpty()) {
            dao.upsertAddon(
                AddonEntity(
                    url = "https://v3-cinemeta.strem.io/manifest.json",
                    name = "Cinemeta Official",
                    description = "الكتالوج الرسمي للأفلام والمسلسلات",
                    iconUrl = null,
                    type = "STREMIO",
                    isEnabled = true
                )
            )
            dao.upsertAddon(
                AddonEntity(
                    url = "https://opensubtitles-v3.strem.io/manifest.json",
                    name = "OpenSubtitles v3",
                    description = "ترجمات عربية وعالمية تلقائية",
                    iconUrl = null,
                    type = "STREMIO",
                    isEnabled = true
                )
            )
        }
    }

    fun loadHomeCatalogs() {
        viewModelScope.launch {
            val catalogAddon = dao.getEnabledAddons().firstOrNull { it.type == "STREMIO" }?.url
                ?: "https://v3-cinemeta.strem.io/manifest.json"
            val movies = stremioEngine.fetchCatalog(catalogAddon, "movie", "top")
            val series = stremioEngine.fetchCatalog(catalogAddon, "series", "top")
            _featuredMovies.value = movies.map { applySavedArtwork(it) }
            _featuredSeries.value = series.map { applySavedArtwork(it) }
        }
    }

    private suspend fun applySavedArtwork(item: MediaItem): MediaItem {
        val custom = dao.getCustomArtwork(item.id) ?: return item
        return item.copy(
            posterUrl = custom.customPosterUrl ?: item.posterUrl,
            backdropUrl = custom.customBackdropUrl ?: item.backdropUrl,
            logoUrl = custom.customLogoUrl ?: item.logoUrl
        )
    }

    fun loginWithStremio(email: String, password: String) {
        viewModelScope.launch {
            _statusMessage.value = "جاري الاتصال بحساب Stremio ومزامنة الإضافات..."
            stremioEngine.loginStremio(email, password).onSuccess { account ->
                val existing = dao.getSession()
                dao.saveSession(
                    UserSessionEntity(
                        authKey = account.authKey,
                        email = account.email,
                        fullName = account.fullName,
                        avatarUrl = account.avatarUrl,
                        customAvatarUrl = existing?.customAvatarUrl,
                        seekStepSeconds = _playerSettings.value.seekStepSeconds,
                        defaultEngine = _playerSettings.value.engine.name,
                        tmdbApiKey = existing?.tmdbApiKey ?: ""
                    )
                )
                stremioEngine.fetchUserAddonsFromAccount(account.authKey).onSuccess { remoteAddons ->
                    remoteAddons.forEach { dao.upsertAddon(it) }
                    loadHomeCatalogs()
                    _statusMessage.value = "تم ربط حساب ${account.fullName} ومزامنة ${remoteAddons.size} إضافة بنجاح!"
                }
            }.onFailure {
                _statusMessage.value = "خطأ في تسجيل الدخول: ${it.message}"
            }
        }
    }

    fun logoutStremio() {
        viewModelScope.launch {
            dao.clearSession()
            _statusMessage.value = "تم تسجيل الخروج من الحساب"
        }
    }

    fun updateCustomProfileAvatar(url: String) {
        viewModelScope.launch {
            val current = dao.getSession() ?: return@launch
            dao.saveSession(current.copy(customAvatarUrl = url.trim()))
            _statusMessage.value = "تم تحديث صورة الملف الشخصي"
        }
    }

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            val current = dao.getSession() ?: UserSessionEntity(
                authKey = "", email = "مستخدم محلي", fullName = "مستخدم فلمر",
                avatarUrl = null, customAvatarUrl = null
            )
            dao.saveSession(current.copy(tmdbApiKey = key.trim()))
            _statusMessage.value = "تم حفظ مفتاح TMDB لتفعيل تغيير البوسترات والشعارات"
        }
    }

    fun installAddonOrRepo(url: String) {
        viewModelScope.launch {
            val clean = url.trim()
            if (clean.endsWith("manifest.json") || clean.startsWith("stremio://")) {
                stremioEngine.fetchManifest(clean).onSuccess {
                    dao.upsertAddon(it)
                    loadHomeCatalogs()
                    _statusMessage.value = "تم تثبيت إضافة Stremio: ${it.name}"
                }.onFailure {
                    _statusMessage.value = "تعذر قراءة رابط Stremio: ${it.message}"
                }
            } else {
                cloudStreamEngine.parseRepository(clean).onSuccess { list ->
                    list.forEach { dao.upsertAddon(it) }
                    _statusMessage.value = "تم تثبيت ${list.size} إضافة من مستودع CloudStream!"
                }.onFailure {
                    _statusMessage.value = "تعذر قراءة مستودع CloudStream: ${it.message}"
                }
            }
        }
    }

    fun removeAddon(addon: AddonEntity) {
        viewModelScope.launch { dao.deleteAddon(addon) }
    }

    fun openMediaDetails(item: MediaItem) {
        viewModelScope.launch {
            val withCustom = applySavedArtwork(item)
            _selectedMedia.value = withCustom
            _streams.value = emptyList()
            _subtitles.value = emptyList()

            val detailed = stremioEngine.fetchMeta("https://v3-cinemeta.strem.io/manifest.json", item.type, item.id)
            if (detailed != null) {
                _selectedMedia.value = applySavedArtwork(detailed)
            }

            val apiKey = dao.getSession()?.tmdbApiKey.orEmpty()
            if (apiKey.isNotBlank()) {
                _artworkOptions.value = tmdbEngine.fetchArtworkOptions(apiKey, item.id, item.type == "series")
            }
        }
    }

    fun selectCustomArtwork(poster: String?, backdrop: String?, logo: String?) {
        viewModelScope.launch {
            val current = _selectedMedia.value ?: return@launch
            val updated = current.copy(
                posterUrl = poster ?: current.posterUrl,
                backdropUrl = backdrop ?: current.backdropUrl,
                logoUrl = logo ?: current.logoUrl
            )
            _selectedMedia.value = updated
            dao.saveCustomArtwork(
                CustomArtworkEntity(
                    mediaId = current.id,
                    customPosterUrl = updated.posterUrl,
                    customBackdropUrl = updated.backdropUrl,
                    customLogoUrl = updated.logoUrl
                )
            )
            _statusMessage.value = "تم حفظ المظهر المخصص بنجاح"
        }
    }

    fun loadStreamsForVideo(media: MediaItem, videoId: String, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _isLoadingStreams.value = true
            _streams.value = emptyList()
            val enabledAddons = dao.getEnabledAddons()

            val streamJobs = enabledAddons.map { addon ->
                async {
                    if (addon.type == "STREMIO") {
                        stremioEngine.fetchStreams(addon, media.type, videoId)
                    } else {
                        cloudStreamEngine.fetchStreamsFromPlugin(addon, media.title, season, episode)
                    }
                }
            }
            val subJobs = enabledAddons.filter { it.type == "STREMIO" }.map { addon ->
                async { stremioEngine.fetchSubtitles(addon, media.type, videoId) }
            }

            val allStreams = streamJobs.awaitAll().flatten().sortedByDescending { it.qualityScore }
            val allSubs = subJobs.awaitAll().flatten()

            _streams.value = allStreams
            _subtitles.value = allSubs
            _isLoadingStreams.value = false
        }
    }

    fun startPlayback(stream: UnifiedStream, media: MediaItem, season: Int = 1, episode: Int = 1) {
        _activeStream.value = stream
        viewModelScope.launch {
            dao.upsertHistory(
                WatchHistoryEntity(
                    mediaId = media.id,
                    title = media.title,
                    posterUrl = media.posterUrl,
                    backdropUrl = media.backdropUrl,
                    type = media.type,
                    season = season,
                    episode = episode
                )
            )
        }
    }

    fun updatePlayerSettings(transform: (PlayerSettings) -> PlayerSettings) {
        _playerSettings.value = transform(_playerSettings.value)
        viewModelScope.launch {
            dao.getSession()?.let { s ->
                dao.saveSession(
                    s.copy(
                        seekStepSeconds = _playerSettings.value.seekStepSeconds,
                        defaultEngine = _playerSettings.value.engine.name
                    )
                )
            }
        }
    }

    fun searchAll(query: String) {
        viewModelScope.launch {
            val apiKey = dao.getSession()?.tmdbApiKey.orEmpty()
            val tmdbRes = if (apiKey.isNotBlank()) tmdbEngine.searchArabic(apiKey, query) else emptyList()
            val localMatches = (_featuredMovies.value + _featuredSeries.value)
                .filter { it.title.contains(query, ignoreCase = true) }
            _searchResults.value = (tmdbRes + localMatches).distinctBy { it.title }
        }
    }

    fun clearStatus() {
        _statusMessage.value = null
    }
}
