package com.filmr.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.filmr.tv.data.model.MediaItem
import com.filmr.tv.data.model.WatchProgressEntity

// Nuvio TV Luxury Color System
val NuvioBg = Color(0xFF0A0C12)
val NuvioAmoledBg = Color(0xFF000000)
val NuvioSurface = Color(0xFF131722)
val NuvioCardBg = Color(0xFF191E2C)
val NuvioGold = Color(0xFFE5B84B)
val NuvioAccentBlue = Color(0xFF3B82F6)
val NuvioTextPrimary = Color(0xFFF8FAFC)
val NuvioTextSecondary = Color(0xFF94A3B8)

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    preferEnglish: Boolean,
    lowEndMode: Boolean = false,
    onFocused: (MediaItem) -> Unit = {},
    onClick: (MediaItem) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by if (lowEndMode) {
        remember(isFocused) { mutableFloatStateOf(if (isFocused) 1.04f else 1.0f) }
    } else {
        animateFloatAsState(
            targetValue = if (isFocused) 1.07f else 1.0f,
            animationSpec = tween(durationMillis = 140),
            label = "poster_scale"
        )
    }

    val context = LocalContext.current
    val imageRequest = remember(item.posterUrl, lowEndMode) {
        ImageRequest.Builder(context)
            .data(item.posterUrl)
            .crossfade(!lowEndMode)
            .size(if (lowEndMode) 220 else 340, if (lowEndMode) 330 else 510)
            .build()
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .scale(scale)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused(item)
            }
            .focusable()
            .clickable { onClick(item) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(198.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = item.displayTitle(preferEnglish),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = NuvioTextSecondary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Rating Badge
            val rating = item.imdbRating ?: item.tmdbRating
            if (!rating.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = NuvioGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = rating,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.displayTitle(preferEnglish),
            color = if (isFocused) Color.White else NuvioTextPrimary,
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.year.isNotBlank()) {
            Text(
                text = item.year,
                color = NuvioTextSecondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ContinueWatchingCard(
    progress: WatchProgressEntity,
    lowEndMode: Boolean = false,
    onClick: (WatchProgressEntity) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale = if (isFocused) 1.04f else 1.0f

    Column(
        modifier = Modifier
            .width(230.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick(progress) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NuvioCardBg)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color.White else Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            val img = progress.backdropUrl ?: progress.posterUrl
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img,
                    contentDescription = progress.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            Icon(
                imageVector = Icons.Default.PlayCircleFilled,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
            )

            if (progress.season > 0 && progress.episode > 0) {
                Text(
                    text = "S${progress.season}:E${progress.episode}",
                    color = NuvioGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.progressFraction)
                        .fillMaxHeight()
                        .background(NuvioGold)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = progress.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val remainingMin = ((progress.durationMs - progress.positionMs) / 60_000L).coerceAtLeast(1L)
        Text(
            text = "متبقي $remainingMin دقيقة",
            color = NuvioTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun NuvioChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> NuvioGold
        isFocused -> Color.White.copy(alpha = 0.22f)
        else -> NuvioCardBg
    }
    val textColor = if (selected) Color.Black else Color.White

    Surface(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun NuvioFocusButton(
    text: String,
    icon: ImageVector? = null,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isPrimary && isFocused -> Color.White
        isPrimary -> NuvioGold
        isFocused -> Color.White.copy(alpha = 0.25f)
        else -> NuvioCardBg
    }
    val contentColor = when {
        isPrimary -> Color.Black
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(
            width = if (isFocused) 2.5.dp else 1.dp,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun NuvioWhiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(text = placeholder, color = Color.Gray, fontSize = 13.sp)
        },
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF141824),
            unfocusedContainerColor = Color(0xFF141824),
            focusedBorderColor = NuvioGold,
            unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
            cursorColor = NuvioGold
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun NuvioNavigationRail(
    selectedTab: Int,
    isStremioSynced: Boolean,
    onSelectTab: (Int) -> Unit
) {
    val navItems = listOf(
        Triple(0, "الرئيسية", Icons.Default.Home),
        Triple(1, "البحث", Icons.Default.Search),
        Triple(2, "المكتبة", Icons.Default.VideoLibrary),
        Triple(3, "الإعدادات", Icons.Default.Settings)
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(Color(0xFF07090E))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.06f))
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Brand Badge (F)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NuvioGold, Color(0xFF9A7221))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "F",
                    color = Color.Black,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            if (isStremioSynced) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E))
                )
            }
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            navItems.forEach { (idx, label, icon) ->
                var isFocused by remember { mutableStateOf(false) }
                val active = selectedTab == idx
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                active -> NuvioGold.copy(alpha = 0.2f)
                                isFocused -> Color.White.copy(alpha = 0.14f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onSelectTab(idx) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (active) NuvioGold else if (isFocused) Color.White else NuvioTextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (active) NuvioGold else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
