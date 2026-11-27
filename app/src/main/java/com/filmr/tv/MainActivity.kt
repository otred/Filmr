package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.domain.model.NavSection
import com.filmr.tv.ui.FilmrViewModel
import com.filmr.tv.ui.ScreenRoute
import com.filmr.tv.ui.components.FloatingRightNavRail
import com.filmr.tv.ui.player.NuvioPlayerScreen
import com.filmr.tv.ui.screens.*
import com.filmr.tv.ui.theme.FilmrGold
import com.filmr.tv.ui.theme.FilmrNuvioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: FilmrViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            val accentColor = Color(state.settings.accentColorHex)

            FilmrNuvioTheme(
                accentColor = accentColor,
                pureBlack = state.settings.pureBlackTheme
            ) {
                // Intercept Android TV / Phone Back button cleanly so it NEVER exits app unexpectedly
                BackHandler(enabled = true) {
                    val handled = vm.handleBackPress()
                    if (!handled) {
                        finish()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val route = state.currentRoute) {
                        is ScreenRoute.Player -> {
                            NuvioPlayerScreen(
                                mediaItem = route.item,
                                initialStream = route.initialStream,
                                allStreams = route.allStreams,
                                season = route.season,
                                episode = route.episode,
                                startPositionMs = route.startPositionMs,
                                settings = state.settings,
                                onSaveProgress = { pos, dur ->
                                    vm.savePlaybackProgress(route.item, route.season, route.episode, pos, dur)
                                },
                                onSwitchStreamRequested = { newStream, pos ->
                                    vm.handleBackPress()
                                    vm.playStreamSource(newStream, pos)
                                },
                                onExitPlayer = {
                                    vm.handleBackPress()
                                }
                            )
                        }

                        is ScreenRoute.Details -> {
                            val detailItem = state.selectedDetailItem ?: route.item
                            DetailsScreen(
                                item = detailItem,
                                isLoadingDetails = state.isLoadingDetails,
                                selectedSeason = state.selectedSeason,
                                seasonEpisodes = state.seasonEpisodes,
                                isStreamsDrawerOpen = state.isStreamsDrawerOpen,
                                isLoadingStreams = state.isLoadingStreams,
                                streamsList = state.streamsList,
                                selectedAddonFilter = state.selectedStreamAddonFilter,
                                torrentResolvingStatus = state.torrentResolvingStatus,
                                isArtworkPickerOpen = state.isArtworkPickerOpen,
                                artworkOptions = state.artworkOptions,
                                isLoadingArtwork = state.isLoadingArtwork,
                                accentColor = accentColor,
                                onBack = { vm.handleBackPress() },
                                onOpenStreams = { ep -> vm.openStreamsDrawer(ep) },
                                onCloseStreams = { vm.handleBackPress() },
                                onFilterAddon = { vm.filterStreamsByAddon(it) },
                                onPlayStream = { src -> vm.playStreamSource(src) },
                                onSelectSeason = { vm.selectSeason(it) },
                                onToggleLibrary = { vm.toggleLibrary(it) },
                                onToggleWatched = { vm.toggleWatched(it) },
                                onOpenArtworkPicker = { vm.openArtworkAndTitlePicker(it) },
                                onCloseArtworkPicker = { vm.handleBackPress() },
                                onApplyArtwork = { it, opt -> vm.applyCustomPosterOrBackdrop(it, opt) },
                                onToggleTitleLang = { vm.toggleTitleLanguage(it) },
                                onOpenPerson = { person -> vm.openPersonCredits(person) }
                            )
                        }

                        is ScreenRoute.PersonCredits -> {
                            PersonCreditsScreen(
                                personName = state.personCreditsTitle,
                                works = state.personCreditsList,
                                settings = state.settings,
                                onOpenDetails = { vm.openDetails(it) }
                            )
                        }

                        is ScreenRoute.MainTab -> {
                            when (route.section) {
                                NavSection.HOME -> {
                                    HomeScreen(
                                        heroItem = state.focusedHeroItem,
                                        continueWatching = state.continueWatching,
                                        homeRows = state.homeRows,
                                        isLoading = state.isLoadingHome,
                                        settings = state.settings,
                                        onItemFocused = { vm.onHeroItemFocused(it) },
                                        onOpenDetails = { vm.openDetails(it) },
                                        onOpenArtworkPicker = { vm.openArtworkAndTitlePicker(it) },
                                        onResumeContinueWatching = { vm.resumeContinueWatching(it) },
                                        onRemoveContinueWatching = { vm.removeFromContinueWatching(it) },
                                        onQuickFilterPlatform = { platform ->
                                            vm.navigateToTab(NavSection.SEARCH)
                                            vm.updateSearchQuery(platform)
                                        }
                                    )
                                }

                                NavSection.SEARCH -> {
                                    SearchScreen(
                                        query = state.searchQuery,
                                        isSearching = state.isSearching,
                                        results = state.searchResults,
                                        settings = state.settings,
                                        onQueryChange = { vm.updateSearchQuery(it) },
                                        onOpenDetails = { vm.openDetails(it) },
                                        onOpenArtworkPicker = { vm.openArtworkAndTitlePicker(it) }
                                    )
                                }

                                NavSection.LIBRARY -> {
                                    LibraryScreen(
                                        libraryItems = state.libraryItems,
                                        settings = state.settings,
                                        onOpenDetails = { vm.openDetails(it) },
                                        onOpenArtworkPicker = { vm.openArtworkAndTitlePicker(it) }
                                    )
                                }

                                NavSection.CALENDAR -> {
                                    CalendarScreen(
                                        entries = state.calendarEntries,
                                        selectedDay = state.selectedCalendarDay,
                                        accentColor = accentColor,
                                        onSelectDay = { vm.selectCalendarDay(it) },
                                        onOpenSeriesById = { id ->
                                            state.libraryItems.find { it.id == id }?.let { vm.openDetails(it) }
                                        }
                                    )
                                }

                                NavSection.SETTINGS -> {
                                    SettingsScreen(
                                        settings = state.settings,
                                        profiles = state.profiles,
                                        addons = state.addons,
                                        cloudStreamRepos = state.cloudStreamRepos,
                                        onUpdateSettings = { vm.updateSettings(it) },
                                        onSyncStremio = { e, p -> vm.syncStremioAccount(e, p) },
                                        onInstallAddon = { vm.installStremioAddon(it) },
                                        onToggleAddon = { vm.toggleAddonEnabled(it) },
                                        onRemoveAddon = { vm.removeAddon(it) },
                                        onInstallCloudStreamRepo = { vm.installCloudStreamRepo(it) },
                                        onToggleCloudStreamRepo = { vm.toggleCloudStreamRepo(it) },
                                        onRemoveCloudStreamRepo = { vm.removeCloudStreamRepo(it) },
                                        onSwitchProfile = { vm.switchActiveProfile(it) },
                                        onAddProfile = { vm.addNewProfile(it) }
                                    )
                                }
                            }
                        }
                    }

                    // Floating Right Navigation Rail (Visible on all non-player screens, just like Nuvio!)
                    if (state.currentRoute !is ScreenRoute.Player) {
                        val activeTab = (state.currentRoute as? ScreenRoute.MainTab)?.section ?: NavSection.HOME
                        FloatingRightNavRail(
                            currentSection = activeTab,
                            expanded = state.isRailExpanded,
                            accentColor = accentColor,
                            activeProfile = state.profiles.find { it.isActive } ?: state.profiles.firstOrNull(),
                            onExpandedChange = { vm.setRailExpanded(it) },
                            onSelectSection = { vm.navigateToTab(it) },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }

                    // Global Status Toast Notification
                    AnimatedVisibility(
                        visible = state.statusBannerMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 28.dp)
                    ) {
                        state.statusBannerMessage?.let { msg ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xEE14161D))
                                    .border(1.5.dp, accentColor, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 22.dp, vertical = 12.dp)
                            ) {
                                Text(text = msg, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Branded Filmr Splash Screen (Exact user logo 17279.jpg)
                    AnimatedVisibility(
                        visible = state.isSplashVisible,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0A0B0E))
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.filmr_brand_logo),
                                contentDescription = "Filmr Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(0.72f)
                            )
                            CircularProgressIndicator(
                                color = FilmrGold,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
