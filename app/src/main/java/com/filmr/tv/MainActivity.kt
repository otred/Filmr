package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.model.*
import com.filmr.tv.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: FilmrViewModel = viewModel()
            val navStack = remember { mutableStateListOf<Any>("home") }
            val current = navStack.lastOrNull() ?: "home"

            // منع الخروج من التطبيق عند ضغط زر الرجوع في الريموت
            BackHandler(enabled = navStack.size > 1) {
                navStack.removeLastOrNull()
            }

            when (current) {
                "home" -> NuvioHomeScreen(
                    vm = vm,
                    onOpenDetails = { navStack.add(it) },
                    onOpenDetailsManual = { navStack.add(it) },
                    onResumeContinue = { p, restart ->
                        val item = MediaItem(p.mediaId, p.imdbId, 0, p.type, p.title, p.title, p.posterUrl, p.backdropUrl)
                        if (p.streamUrl.isNotBlank() && !restart) {
                            navStack.add(PlayerRoute(item, UnifiedStream("resume", p.addonName, p.title, p.streamUrl, "HD", 720, p.addonName), p.season, p.episode, p.title, p.positionMs))
                        } else {
                            navStack.add(item)
                        }
                    },
                    onOpenSearch = { navStack.add("search") },
                    onOpenLibrary = { navStack.add("library") },
                    onOpenCalendar = { navStack.add("calendar") },
                    onOpenAddons = { navStack.add("addons") },
                    onOpenSettings = { navStack.add("settings") }
                )
                "search" -> SearchScreen(vm, onBack = { navStack.removeLastOrNull() }, onOpenDetails = { navStack.add(it) })
                "library" -> LibraryScreen(vm, onBack = { navStack.removeLastOrNull() }, onOpenDetails = { navStack.add(it) })
                "calendar" -> CalendarScreen(vm, onBack = { navStack.removeLastOrNull() }, onOpenDetails = { navStack.add(it) })
                "addons" -> AddonsAndSyncScreen(vm, onBack = { navStack.removeLastOrNull() })
                "settings" -> SettingsScreen(vm, onBack = { navStack.removeLastOrNull() }, onOpenAddons = { navStack.add("addons") })
                is MediaItem -> DetailsAndStreamsScreen(
                    initialItem = current,
                    vm = vm,
                    onBack = { navStack.removeLastOrNull() },
                    onOpenPerson = { navStack.add(PersonRoute(it)) },
                    onOpenNetwork = { id, name -> navStack.add(NetworkRoute(id, name)) },
                    onStartStream = { item, st, s, e, title -> navStack.add(PlayerRoute(item, st, s, e, title, 0L)) }
                )
                is PersonRoute -> PersonProfileScreen(current.id, vm, onBack = { navStack.removeLastOrNull() }, onOpenDetails = { navStack.add(it) })
                is NetworkRoute -> NetworkCatalogScreen(current.id, current.name, vm, onBack = { navStack.removeLastOrNull() }, onOpenDetails = { navStack.add(it) })
                is PlayerRoute -> FilmrPlayerScreen(
                    item = current.item,
                    initialStream = current.stream,
                    season = current.season,
                    episode = current.episode,
                    epTitle = current.title,
                    startPositionMs = current.posMs,
                    vm = vm,
                    onBack = { navStack.removeLastOrNull() }
                )
            }
        }
    }
}

private data class PersonRoute(val id: Int)
private data class NetworkRoute(val id: Int, val name: String)
private data class PlayerRoute(val item: MediaItem, val stream: UnifiedStream, val season: Int, val episode: Int, val title: String, val posMs: Long)
