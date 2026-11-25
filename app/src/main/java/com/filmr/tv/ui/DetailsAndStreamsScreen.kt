package com.filmr.tv.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.filmr.tv.model.*
import kotlinx.coroutines.launch

@Composable
fun DetailsAndStreamsScreen(
    initialItem: MediaItem,
    vm: FilmrViewModel,
    onBack: () -> Unit,
    onStartStream: (MediaItem, UnifiedStream, Int, Int, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val accent = Color(settings.accentHex)
    var item by remember { mutableStateOf(initialItem) }
    var season by remember { mutableIntStateOf(1) }
    var episode by remember { mutableIntStateOf(1) }
    var episodes by remember { mutableStateOf<List<EpisodeItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<UnifiedStream>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(false) }

    LaunchedEffect(initialItem.id) {
        item = vm.loadFullDetails(initialItem)
        if (item.type == "series") episodes = vm.loadEpisodes(item, season)
        loadingStreams = true
        streams = vm.fetchStreams(item, season, episode)
        loadingStreams = false
    }

    LazyColumn(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0B0E))) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                AsyncImage(model = item.displayBackdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0xFF0A0B0E)))))
                IconButton(onClick = onBack, modifier = Modifier.padding(12.dp)) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    Text(item.displayTitle, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("${item.year} • ★ ${String.format("%.1f", item.rating)}", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(item.overviewAr, color = Color(0xFFD0D4DC), fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            streams.firstOrNull()?.let { onStartStream(item, it, season, episode, item.displayTitle) }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text("تشغيل سريع")
                    }
                    OutlinedButton(onClick = { vm.toggleLibrary(item) }) {
                        Icon(Icons.Filled.FavoriteBorder, null, tint = Color.White)
                        Text("المفضلة", color = Color.White)
                    }
                }
            }
        }

        if (item.type == "series") {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((1..item.seasonsCount.coerceAtLeast(1)).toList()) { s ->
                        FilterChip(
                            selected = season == s,
                            onClick = {
                                season = s
                                scope.launch { episodes = vm.loadEpisodes(item, s) }
                            },
                            label = { Text("الموسم $s") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(episodes) { ep ->
                        Column(
                            modifier = Modifier.width(180.dp).clip(RoundedCornerShape(10.dp)).background(if (episode == ep.episode) Color(0xFF232734) else Color(0xFF14161D))
                                .clickable {
                                    episode = ep.episode
                                    scope.launch {
                                        loadingStreams = true
                                        streams = vm.fetchStreams(item, season, ep.episode)
                                        loadingStreams = false
                                    }
                                }.padding(8.dp)
                        ) {
                            AsyncImage(model = ep.stillUrl.ifBlank { item.displayBackdrop }, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(95.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.height(4.dp))
                            Text("${ep.episode}. ${ep.title}", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        item {
            Text("السيرفرات المتاحة (${streams.size})", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            if (loadingStreams) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accent)
                }
            }
        }

        items(streams) { st ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151821)).clickable { onStartStream(item, st, season, episode, item.displayTitle) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(st.name, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(st.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(st.quality, color = Color.White, fontSize = 12.sp, modifier = Modifier.background(Color(0xFF252A38), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
