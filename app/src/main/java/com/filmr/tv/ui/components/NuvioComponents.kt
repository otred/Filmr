package com.filmr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.WatchProgressEntity

val NuvioGold = Color(0xFFE5A93C)
val NuvioBg = Color(0xFF0A0D12)
val NuvioCardBg = Color(0xFF141923)
val NuvioSurface = Color(0xFF1A2130)

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    onFocused: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1.0f, label = "poster_scale")

    Column(
        modifier = Modifier
            .width(136.dp)
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(198.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            AsyncImage(
                model = item.displayPoster,
                contentDescription = item.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.rating > 0.0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC0A0D12))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "★ ${String.format("%.1f", item.rating)}",
                        color = NuvioGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.displayTitle,
            color = if (focused) Color.White else Color(0xFFD0D6E0),
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val ratio = if (progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0.02f, 0.98f)
    } else 0.1f
    val remMinutes = ((progress.durationMs - progress.positionMs).coerceAtLeast(0L) / 60_000L).toInt()

    Column(
        modifier = Modifier
            .width(240.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(134.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (focused) 2.5.dp else 0.dp,
                    color = if (focused) Color.White else Color.Transparent,
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0A0D12))))
            )
            if (remMinutes > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC0A0D12))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "متبقي $remMinutes د",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = if (progress.type == "series") "${progress.title} • م${progress.season} ح${progress.episode}" else progress.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio)
                            .fillMaxHeight()
                            .background(NuvioGold)
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
    onClick: () -> Unit = {}
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> NuvioGold
                    focused -> Color(0xFF2B3447)
                    else -> Color(0xFF171C26)
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x26FFFFFF),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun NuvioWhiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color(0xFF8E97A8), fontSize = 14.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NuvioGold,
            focusedBorderColor = Color.White,
            unfocusedBorderColor = Color(0x44FFFFFF),
            focusedContainerColor = Color(0xFF161B26),
            unfocusedContainerColor = Color(0xFF161B26)
        ),
        modifier = modifier
    )
}

@Composable
fun NuvioRightDrawer(
    currentRoute: String,
    onSelectRoute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val navItems = listOf(
        "home" to "الرئيسية",
        "search" to "البحث",
        "library" to "المكتبة",
        "settings" to "الإعدادات"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(260.dp)
                .fillMaxHeight()
                .background(Color(0xF50E121A))
                .padding(horizontal = 20.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "FILMR",
                color = NuvioGold,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = "واجهة التلفزيون الذكية",
                color = Color(0xFF8E97A8),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))
            navItems.forEach { (route, label) ->
                var focused by remember { mutableStateOf(false) }
                val active = currentRoute == route
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                active -> NuvioGold
                                focused -> Color(0xFF283142)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            width = if (focused) 2.dp else 0.dp,
                            color = if (focused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable {
                            onSelectRoute(route)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Text(
                        text = label,
                        color = if (active) Color.Black else Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
