package com.filmr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.WatchProgressEntity

val NuvioBg = Color(0xFF0A0A0D)
val NuvioSurface = Color(0xFF141419)
val NuvioCardBg = Color(0xFF1C1C23)
val NuvioGold = Color(0xFFF5C518)
val NuvioAccent = Color(0xFF6366F1)

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    preferEnglish: Boolean,
    isFirstInRow: Boolean = false,
    onRequestOpenDrawer: () -> Unit = {},
    onFocusItem: (MediaItem) -> Unit = {},
    onClick: (MediaItem) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.07f else 1.0f, label = "posterScale")

    Box(
        modifier = Modifier
            .width(114.dp)
            .height(168.dp)
            .scale(scale)
            .onPreviewKeyEvent { event ->
                if (isFirstInRow && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRequestOpenDrawer()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocusItem(item)
            }
            .focusable()
            .clickable { onClick(item) }
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
        if (item.posterUrl.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    text = item.displayTitle(preferEnglish),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    isFirstInRow: Boolean = false,
    onRequestOpenDrawer: () -> Unit = {},
    onClick: (WatchProgressEntity) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, label = "cwScale")
    val fraction = if (progress.durationMs > 0L) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0.03f, 1f)
    } else 0.1f

    val remainingMs = (progress.durationMs - progress.positionMs).coerceAtLeast(0L)
    val remMins = (remainingMs / 60000L).toInt()
    val remH = remMins / 60
    val remM = remMins % 60
    val remainingText = when {
        remainingMs <= 0L -> ""
        remH > 0 -> "متبقي ${remH} س ${remM} د"
        else -> "متبقي ${remM} د"
    }

    Box(
        modifier = Modifier
            .width(215.dp)
            .height(122.dp)
            .scale(scale)
            .onPreviewKeyEvent { event ->
                if (isFirstInRow && event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                    onRequestOpenDrawer()
                    true
                } else {
                    false
                }
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick(progress) }
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))
                    )
                )
        )
        if (remainingText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(text = remainingText, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
        }
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
                    text = "الحلقة ${progress.episode} • م${progress.season}",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(5.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = NuvioGold,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
    }
}

@Composable
fun NuvioSideDrawer(
    visible: Boolean,
    currentRoute: String,
    drawerFocusRequester: FocusRequester,
    onSelectRoute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionLeft || event.key == Key.Back)) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(210.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF101015).copy(alpha = 0.96f), Color(0xFF101015).copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 18.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PlayCircleFilled, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("FILMR", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            val navItems = listOf(
                Triple("home", "الرئيسية", Icons.Default.Home),
                Triple("search", "البحث", Icons.Default.Search),
                Triple("library", "المكتبة", Icons.Default.Bookmark),
                Triple("settings", "الإعدادات", Icons.Default.Settings)
            )

            navItems.forEachIndexed { idx, (routeKey, label, icon) ->
                var itemFocused by remember { mutableStateOf(false) }
                val isSelected = currentRoute == routeKey
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (idx == 0) Modifier.focusRequester(drawerFocusRequester) else Modifier)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                itemFocused -> Color.White
                                isSelected -> Color.White.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            }
                        )
                        .onFocusChanged { itemFocused = it.isFocused }
                        .focusable()
                        .clickable {
                            onDismiss()
                            onSelectRoute(routeKey)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (itemFocused) Color.Black else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        color = if (itemFocused) Color.Black else Color.White,
                        fontSize = 15.sp,
                        fontWeight = if (itemFocused || isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun NuvioChip(
    label: String,
    selected: Boolean = false,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                when {
                    isFocused -> Color.White
                    selected -> Color.White.copy(alpha = 0.22f)
                    else -> NuvioCardBg
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(22.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun NuvioCircleButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White
                    active -> Color.White.copy(alpha = 0.25f)
                    else -> NuvioCardBg
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
