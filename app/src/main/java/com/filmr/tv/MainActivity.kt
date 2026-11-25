package com.filmr.tv
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.model.*
import com.filmr.tv.ui.*
import com.filmr.tv.ui.FilmrViewModel
class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent {
val vm: FilmrViewModel = viewModel()
var scr by remember { mutableStateOf("home") }
var item by remember { mutableStateOf<MediaItem?>(null) }
var play by remember { mutableStateOf<Triple<UnifiedStream, Int, Int>?>(null) }
when (scr) {
"home" -> HomeScreen(vm = vm, onOpenDetails = { item = it; scr = "details" }, onOpenSearch = { scr = "search" }, onOpenAddons = { scr = "addons" }, onOpenCalendar = { scr = "cal" }, onOpenSettings = { scr = "set" })
"details" -> item?.let { DetailsScreen(item = it, vm = vm, onBack = { scr = "home" }, onPlay = { st, s, e -> play = Triple(st, s, e); scr = "play" }) }
"play" -> if (item != null && play != null) FilmrPlayerScreen(item!!, play!!.first, play!!.second, play!!.third, "", vm) { scr = "details" }
"search" -> SearchScreen(vm, { scr = "home" }) { item = it; scr = "details" }
"addons" -> AddonsAndSyncScreen(vm) { scr = "home" }
else -> CalendarAndSettingsScreen(scr == "cal", vm, { scr = "home" }) { item = it; scr = "details" }
} } } }
