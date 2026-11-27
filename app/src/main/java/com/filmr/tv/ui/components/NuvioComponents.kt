package com.filmr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.WatchProgressEntity

val NuvioBg = Color(0xFF09090B)
val NuvioSurface = Color(0xFF141418)
val NuvioCardBg = Color(0xFF1C1C22)
val NuvioGold = Color(0xFFF5C518)
val NuvioAccent = Color(0xFF6366F1)

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    preferEnglish: Boolean,
    onFocusItem: (MediaItem) -> Unit = {},
    onClick: (MediaItem) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.07f else 1.0f, label = "cardScale")

    Column(
        modifier = Modifier
            .width(136.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusItem(item)
            }
            .focusable()
            .clickable { onClick(item) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(198.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = item.posterUrl.ifBlank { item.backdropUrl },
                contentDescription = item.displayTitle(preferEnglish),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            val score = item.imdbRating.ifBlank { item.tmdbRating }
            if (score.isNotBlank() && score != "0.0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ $score",
                        color = NuvioGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.displayTitle(preferEnglish),
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    onClick: (WatchProgressEntity) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val fraction = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0.03f, 1f)
    } else 0.1f

    Column(
        modifier = Modifier
            .width(230.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick(progress) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = progress.backdropUrl.ifBlank { progress.posterUrl },
                contentDescription = progress.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = progress.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (progress.type == "series") {
                    Text(
                        text = "الموسم ${progress.season} • الحلقة ${progress.episode}",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NuvioGold,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }
    }
}

@Composable
fun NuvioChip(
    label: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> Color.White
                    isFocused -> Color.White.copy(alpha = 0.22f)
                    else -> NuvioCardBg
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
