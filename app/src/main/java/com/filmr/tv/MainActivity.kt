package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.domain.model.*
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioRightDrawer
import com.filmr.tv.ui.player.NuvioPlayerScreen
import com.filmr.tv.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: FilmrViewModel = viewModel()
            val isSplash by vm.isInitialLoading.collectAsState()
            val navStack = remember { mutableStateListOf<Any>("home") }
            var drawerOpen by remember { mutableStateOf(false) }
            val current = navStack.lastOrNull() ?: "home"

            BackHandler(enabled = true) {
                when {
                    drawerOpen -> drawerOpen = false
                    navStack.size > 1 -> navStack.removeLastOrNull()
                    else -> finishAndRemoveTask()
                }
            }

            if (isSplash) {
                NuvioSplashScreen()
            } else {
                Box(Modifier.fillMaxSize()) {
                    when (current) {
                        "home" -> NuvioHomeScreen(
                            vm = vm,
                            onOpenDetails = { navStack.add(it) },
                            onResumeContinue = { p ->
                                val item = MediaItem(p.mediaId, p.imdbId, 0, p.type, p.title, p.title, p.posterUrl, p.backdropUrl)
                                if (p.streamUrl.isNotBlank()) {
                                    navStack.add(PlayerRoute(item, UnifiedStream("resume", p.addonName, p.title, p.streamUrl, "HD", 720, p.addonName), p.season, p.episode, p.title, p.positionMs))
                                } else {
                                    navStack.add(item)
                                }
                            },
                            onOpenDrawer = { drawerOpen = true }
                        )
                        "search" -> NuvioSearchScreen(vm, onOpenDetails = { navStack.add(it) })
                        "library" -> NuvioLibraryScreen(vm, onOpenDetails = { navStack.add(it) })
                        "addons" -> NuvioAddonsScreen(vm)
                        "settings" -> NuvioSettingsScreen(vm)
                        is MediaItem -> NuvioDetailsScreen(
                            initialItem = current,
                            vm = vm,
                            onStartStream = { item, st, s, e, title ->
                                navStack.add(PlayerRoute(item, st, s, e, title, 0L))
                            }
                        )
                        is PlayerRoute -> NuvioPlayerScreen(
                            item = current.item,
                            initialStream = current.stream,
                            season = current.season,
                            episode = current.episode,
                            epTitle = current.title,
                            startPositionMs = current.posMs,
                            vm = vm
                        )
                    }

                    if (drawerOpen) {
                        val activeRoute = (current as? String) ?: "home"
                        NuvioRightDrawer(
                            currentRoute = activeRoute,
                            onSelectRoute = { target ->
                                if (target == "home") {
                                    navStack.clear()
                                    navStack.add("home")
                                } else {
                                    navStack.add(target)
                                }
                            },
                            onDismiss = { drawerOpen = false }
                        )
                    }
                }
            }
        }
    }
}

private data class PlayerRoute(
    val item: MediaItem,
    val stream: UnifiedStream,
    val season: Int,
    val episode: Int,
    val title: String,
    val posMs: Long
)
