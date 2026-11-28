package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.data.model.CompanyItem
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.UnifiedStream
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.components.NuvioBg
import com.filmr.tv.ui.player.NuvioPlayerScreen
import com.filmr.tv.ui.screens.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val vm: FilmrViewModel = viewModel()
                var route by remember { mutableStateOf("splash") }
                var activeCompany by remember { mutableStateOf<CompanyItem?>(null) }
                var playPayload by remember { mutableStateOf<QuadPlay?>(null) }

                LaunchedEffect(Unit) {
                    delay(700L)
                    if (route == "splash") route = "home"
                }

                BackHandler(enabled = route != "home" && route != "splash") {
                    route = when (route) {
                        "player" -> "details"
                        "person", "company" -> "details"
                        else -> "home"
                    }
                }

                when (route) {
                    "splash" -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(NuvioBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "FILMR",
                                color = Color.White,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                        }
                    }

                    "home" -> {
                        NuvioHomeScreen(
                            viewModel = vm,
                            onNavigate = { route = it },
                            onOpenDetails = { item ->
                                vm.openDetails(item)
                                route = "details"
                            },
                            onResumeProgress = { prog ->
                                val item = MediaItem(
                                    id = prog.mediaId,
                                    imdbId = prog.imdbId,
                                    type = prog.type,
                                    titleAr = prog.title,
                                    titleEn = prog.title,
                                    posterUrl = prog.posterUrl,
                                    backdropUrl = prog.backdropUrl
                                )
                                vm.openDetails(item)
                                route = "details"
                            }
                        )
                    }

                    "details" -> {
                        NuvioDetailsScreen(
                            viewModel = vm,
                            onBack = { route = "home" },
                            onPlayStream = { item, stream, s, e ->
                                playPayload = QuadPlay(item, stream, s, e)
                                route = "player"
                            },
                            onOpenPerson = { personId ->
                                vm.openPerson(personId)
                                route = "person"
                            },
                            onOpenCompany = { comp ->
                                activeCompany = comp
                                vm.openCompany(comp)
                                route = "company"
                            }
                        )
                    }

                    "person" -> {
                        NuvioPersonScreen(
                            viewModel = vm,
                            onBack = { route = "details" },
                            onOpenDetails = { item ->
                                vm.openDetails(item)
                                route = "details"
                            }
                        )
                    }

                    "company" -> {
                        activeCompany?.let { comp ->
                            NuvioCompanyScreen(
                                viewModel = vm,
                                company = comp,
                                onBack = { route = "details" },
                                onOpenDetails = { item ->
                                    vm.openDetails(item)
                                    route = "details"
                                }
                            )
                        }
                    }

                    "search" -> {
                        NuvioSearchScreen(
                            viewModel = vm,
                            onNavigate = { route = it },
                            onOpenDetails = { item ->
                                vm.openDetails(item)
                                route = "details"
                            }
                        )
                    }

                    "library" -> {
                        NuvioLibraryScreen(
                            viewModel = vm,
                            onNavigate = { route = it },
                            onOpenDetails = { item ->
                                vm.openDetails(item)
                                route = "details"
                            }
                        )
                    }

                    "settings" -> {
                        NuvioSettingsScreen(
                            viewModel = vm,
                            onNavigate = { route = it }
                        )
                    }

                    "player" -> {
                        playPayload?.let { p ->
                            NuvioPlayerScreen(
                                viewModel = vm,
                                item = p.item,
                                stream = p.stream,
                                season = p.season,
                                episode = p.episode,
                                onBack = { route = "details" }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class QuadPlay(
    val item: MediaItem,
    val stream: UnifiedStream,
    val season: Int,
    val episode: Int
)
