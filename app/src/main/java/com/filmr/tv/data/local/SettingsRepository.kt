package com.filmr.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.filmr.tv.domain.model.AppSettings
import com.filmr.tv.domain.model.UserProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "filmr_settings_v6")

class SettingsRepository(private val context: Context) {
    private val gson = Gson()

    private object Keys {
        val STREMIO_EMAIL = stringPreferencesKey("stremio_email")
        val STREMIO_AUTH_KEY = stringPreferencesKey("stremio_auth_key")
        val TRAKT_CONNECTED = booleanPreferencesKey("trakt_connected")
        val TRAKT_USERNAME = stringPreferencesKey("trakt_username")
        val SIMKL_CONNECTED = booleanPreferencesKey("simkl_connected")
        val MDBLIST_KEY = stringPreferencesKey("mdblist_key")
        val ANIMESKIP_ID = stringPreferencesKey("animeskip_id")
        val TMDB_KEY = stringPreferencesKey("tmdb_key")
        val ACCENT_COLOR = longPreferencesKey("accent_color")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val POSTER_SCALE = floatPreferencesKey("poster_scale")
        val SHOW_CW_BADGE = booleanPreferencesKey("show_cw_badge")
        val SHOW_PLATFORM_FOLDERS = booleanPreferencesKey("show_platform_folders")
        val AUTO_TRANSLATE_AR = booleanPreferencesKey("auto_translate_ar")
        val PREFER_AR_TITLES = booleanPreferencesKey("prefer_ar_titles")
        val LOW_RAM_MODE = booleanPreferencesKey("low_ram_mode")
        val TORRSERVER_URL = stringPreferencesKey("torrserver_url")
        val AUTO_RESOLVE_TORRENTS = booleanPreferencesKey("auto_resolve_torrents")
        val SUB_SIZE = intPreferencesKey("sub_size")
        val SUB_COLOR = longPreferencesKey("sub_color")
        val SUB_BG_OPACITY = floatPreferencesKey("sub_bg_opacity")
        val AUDIO_BOOST = intPreferencesKey("audio_boost")
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
        val AUTO_SKIP_ANIME = booleanPreferencesKey("auto_skip_anime")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            stremioEmail = prefs[Keys.STREMIO_EMAIL] ?: "",
            stremioAuthKey = prefs[Keys.STREMIO_AUTH_KEY] ?: "",
            traktConnected = prefs[Keys.TRAKT_CONNECTED] ?: false,
            traktUsername = prefs[Keys.TRAKT_USERNAME] ?: "",
            simklConnected = prefs[Keys.SIMKL_CONNECTED] ?: false,
            mdblistApiKey = prefs[Keys.MDBLIST_KEY] ?: "5b8f4d91a3c2e76094128f3d1c9a0b4e",
            animeSkipClientId = prefs[Keys.ANIMESKIP_ID] ?: "th2oogUKsi6ZmFUQPXrNcJZSnFmLI0WN",
            tmdbApiKey = prefs[Keys.TMDB_KEY] ?: "439c478a771f35c05022f9feabcca01c",
            accentColorHex = prefs[Keys.ACCENT_COLOR] ?: 0xFFE5B869,
            pureBlackTheme = prefs[Keys.PURE_BLACK] ?: true,
            posterSizeScale = prefs[Keys.POSTER_SCALE] ?: 1.0f,
            showContinueWatchingBadge = prefs[Keys.SHOW_CW_BADGE] ?: true,
            showPlatformFolders = prefs[Keys.SHOW_PLATFORM_FOLDERS] ?: true,
            autoTranslateArabic = prefs[Keys.AUTO_TRANSLATE_AR] ?: true,
            preferArabicTitles = prefs[Keys.PREFER_AR_TITLES] ?: true,
            lowRamPerformanceMode = prefs[Keys.LOW_RAM_MODE] ?: false,
            torrServerUrl = prefs[Keys.TORRSERVER_URL] ?: "http://127.0.0.1:8090",
            autoResolveTorrents = prefs[Keys.AUTO_RESOLVE_TORRENTS] ?: true,
            subtitleTextSizeSp = prefs[Keys.SUB_SIZE] ?: 22,
            subtitleColorHex = prefs[Keys.SUB_COLOR] ?: 0xFFFFFFFF,
            subtitleBgOpacity = prefs[Keys.SUB_BG_OPACITY] ?: 0.55f,
            audioBoostDb = prefs[Keys.AUDIO_BOOST] ?: 0,
            nightModeAudio = prefs[Keys.NIGHT_MODE] ?: false,
            autoSkipAnimeIntro = prefs[Keys.AUTO_SKIP_ANIME] ?: true
        )
    }

    val profilesFlow: Flow<List<UserProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON]
        if (raw.isNullOrBlank()) {
            listOf(
                UserProfile(1, "Otred", 0xFFE5B869, isActive = true),
                UserProfile(2, "العائلة", 0xFF4F86F7, isActive = false)
            )
        } else {
            val type = object : TypeToken<List<UserProfile>>() {}.type
            runCatching { gson.fromJson<List<UserProfile>>(raw, type) }.getOrDefault(
                listOf(UserProfile(1, "Otred", 0xFFE5B869, isActive = true))
            )
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings, current: AppSettings) {
        val updated = transform(current)
        context.dataStore.edit { prefs ->
            prefs[Keys.STREMIO_EMAIL] = updated.stremioEmail
            prefs[Keys.STREMIO_AUTH_KEY] = updated.stremioAuthKey
            prefs[Keys.TRAKT_CONNECTED] = updated.traktConnected
            prefs[Keys.TRAKT_USERNAME] = updated.traktUsername
            prefs[Keys.SIMKL_CONNECTED] = updated.simklConnected
            prefs[Keys.MDBLIST_KEY] = updated.mdblistApiKey
            prefs[Keys.ANIMESKIP_ID] = updated.animeSkipClientId
            prefs[Keys.TMDB_KEY] = updated.tmdbApiKey
            prefs[Keys.ACCENT_COLOR] = updated.accentColorHex
            prefs[Keys.PURE_BLACK] = updated.pureBlackTheme
            prefs[Keys.POSTER_SCALE] = updated.posterSizeScale
            prefs[Keys.SHOW_CW_BADGE] = updated.showContinueWatchingBadge
            prefs[Keys.SHOW_PLATFORM_FOLDERS] = updated.showPlatformFolders
            prefs[Keys.AUTO_TRANSLATE_AR] = updated.autoTranslateArabic
            prefs[Keys.PREFER_AR_TITLES] = updated.preferArabicTitles
            prefs[Keys.LOW_RAM_MODE] = updated.lowRamPerformanceMode
            prefs[Keys.TORRSERVER_URL] = updated.torrServerUrl
            prefs[Keys.AUTO_RESOLVE_TORRENTS] = updated.autoResolveTorrents
            prefs[Keys.SUB_SIZE] = updated.subtitleTextSizeSp
            prefs[Keys.SUB_COLOR] = updated.subtitleColorHex
            prefs[Keys.SUB_BG_OPACITY] = updated.subtitleBgOpacity
            prefs[Keys.AUDIO_BOOST] = updated.audioBoostDb
            prefs[Keys.NIGHT_MODE] = updated.nightModeAudio
            prefs[Keys.AUTO_SKIP_ANIME] = updated.autoSkipAnimeIntro
        }
    }

    suspend fun saveProfiles(profiles: List<UserProfile>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PROFILES_JSON] = gson.toJson(profiles)
        }
    }
}
