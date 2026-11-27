package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioRightDrawer
import com.filmr.tv.ui.player.NuvioPlayerScreen
import com.filmr.tv.ui.screens.*
import kotlinx.coroutines.delay

private data class PlayerRoute(
    val item: MediaItem,
    val stream: UnifiedStream,
    val season: Int,
    val episode: Int,
    val epTitle: String,
    val startPosMs: Long
)

private data class CompanyRoute(
    val id: Int,
    val name: String,
    val isNetwork: Boolean
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val vm: FilmrViewModel = viewModel()
                var showSplash by remember { mutableStateOf(true) }
                val navStack = remember { mutableStateListOf<Any>("home") }
                var drawerOpen by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(900)
                    showSplash = false
                }

                val current = navStack.lastOrNull() ?: "home"

                BackHandler(enabled = drawerOpen || navStack.size > 1) {
                    if (drawerOpen) {
                        drawerOpen = false
                    } else if (navStack.size > 1) {
                        navStack.removeAt(navStack.lastIndex)
                    }
                }

                if (showSplash) {
                    NuvioSplashScreen()
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (current) {
                            "home" -> NuvioHomeScreen(
                                vm = vm,
                                onOpenDetails = { navStack.add(it) },
                                onResumeContinue = { p ->
                                    val m = MediaItem(
                                        id = p.mediaId,
                                        imdbId = p.imdbId,
                                        type = p.type,
                                        titleAr = p.title,
                                        titleEn = p.title,
                                        posterUrl = p.posterUrl,
                                        backdropUrl = p.backdropUrl
                                    )
                                    if (p.streamUrl.isNotBlank()) {
                                        val st = UnifiedStream(
                                            id = "resume",
                                            name = p.addonName,
                                            title = p.title,
                                            url = p.streamUrl,
                                            addonName = p.addonName,
                                            isTorrent = p.streamUrl.startsWith("magnet:", true)
                                        )
                                        navStack.add(PlayerRoute(m, st, p.season, p.episode, "", p.positionMs))
                                    } else {
                                        navStack.add(m)
                                    }
                                },
                                onOpenDrawer = { drawerOpen = true }
                            )
                            "search" -> NuvioSearchScreen(
                                vm = vm,
                                onOpenDetails = { navStack.add(it) },
                                onOpenDrawer = { drawerOpen = true }
                            )
                            "library" -> NuvioLibraryScreen(
                                vm = vm,
                                onOpenDetails = { navStack.add(it) },
                                onOpenDrawer = { drawerOpen = true }
                            )
                            "settings" -> NuvioSettingsScreen(
                                vm = vm,
                                onOpenDrawer = { drawerOpen = true }
                            )
                            is MediaItem -> NuvioDetailsScreen(
                                vm = vm,
                                initialItem = current,
                                onStartStream = { m, st, s, e, title ->
                                    navStack.add(PlayerRoute(m, st, s, e, title, 0L))
                                },
                                onOpenPerson = { pid -> navStack.add(pid) },
                                onOpenCompany = { cid, cname, isNet -> navStack.add(CompanyRoute(cid, cname, isNet)) },
                                onBack = { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }
                            )
                            is Int -> NuvioPersonScreen(
                                vm = vm,
                                personId = current,
                                onOpenDetails = { navStack.add(it) },
                                onBack = { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }
                            )
                            is CompanyRoute -> NuvioCompanyScreen(
                                vm = vm,
                                companyId = current.id,
                                companyName = current.name,
                                isNetwork = current.isNetwork,
                                onOpenDetails = { navStack.add(it) },
                                onBack = { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }
                            )
                            is PlayerRoute -> NuvioPlayerScreen(
                                item = current.item,
                                initialStream = current.stream,
                                season = current.season,
                                episode = current.episode,
                                episodeTitle = current.epTitle,
                                startPositionMs = current.startPosMs,
                                vm = vm,
                                onBack = { if (navStack.size > 1) navStack.removeAt(navStack.lastIndex) }
                            )
                        }

                        if (drawerOpen) {
                            val activeRoute = (current as? String) ?: "home"
                            NuvioRightDrawer(
                                currentRoute = activeRoute,
                                onSelectRoute = { target ->
                                    navStack.clear()
                                    navStack.add(target)
                                },
                                onDismiss = { drawerOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }
}
