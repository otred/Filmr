package com.filmr.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.data.*
import com.filmr.tv.model.*
import com.filmr.tv.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: FilmrViewModel = viewModel()
            var scr by remember { mutableStateOf("home") }
            var item by remember { mutableStateOf<MediaItem?>(null) }
            var play by remember { mutableStateOf<List<Any>?>(null) }

            when (scr) {
                "home" -> NuvioHomeScreen(
                    vm = vm,
                    onOpenDetails = { item = it; scr = "details" },
                    onPlayContinue = { _ -> },
                    onOpenSearch = { scr = "search" },
                    onOpenAddons = { scr = "addons" },
                    onOpenCalendar = { scr = "cal" },
                    onOpenSettings = { scr = "set" }
                )
                "details" -> item?.let {
                    DetailsAndStreamsScreen(
                        initialItem = it,
                        vm = vm,
                        onBack = { scr = "home" },
                        onStartStream = { m, st, s, e, ep ->
                            item = m
                            play = listOf(st, s, e, ep)
                            scr = "play"
                        }
                    )
                }
                "play" -> if (item != null && play != null) {
                    FilmrPlayerScreen(
                        item = item!!,
                        stream = play!![0] as UnifiedStream,
                        season = play!![1] as Int,
                        episode = play!![2] as Int,
                        epTitle = play!![3] as String,
                        vm = vm,
                        onBack = { scr = "details" }
                    )
                }
                "search" -> SearchScreen(vm, { scr = "home" }) { item = it; scr = "details" }
                "addons" -> AddonsAndSyncScreen(vm) { scr = "home" }
                else -> CalendarAndSettingsScreen(scr == "cal", vm, { scr = "home" }) { item = it; scr = "details" }
            }
        }
    }
}
