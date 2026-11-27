package com.filmr.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filmr.tv.domain.model.MediaItem

val NuvioGold = Color(0xFFE5A93C)
val NuvioBg = Color(0xFF0A0D12)
val NuvioCardBg = Color(0xFF141923)

@Composable
fun NuvioPosterCard(
    item: MediaItem,
    onFocused: () -> Unit = {},
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(138.dp)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(10.dp))
            .border(if (focused) 2.5.dp else 1.dp, if (focused) Color.White else Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .background(NuvioCardBg)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        AsyncImage(
            model = item.displayPoster,
            contentDescription = item.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(194.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C2230))
        )
        Spacer(Modifier.height(5.dp))
        Text(
            item.displayTitle,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun NuvioChip(
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
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
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x26FFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
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
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NuvioGold,
            focusedBorderColor = NuvioGold,
            unfocusedBorderColor = Color(0x44FFFFFF),
            focusedContainerColor = Color(0xFF161B26),
            unfocusedContainerColor = Color(0xFF121620)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    )
}

@Composable
fun NuvioRightDrawer(
    currentRoute: String,
    onSelectRoute: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val menuItems = listOf(
        "home" to "الرئيسية",
        "search" to "البحث",
        "library" to "المكتبة",
        "addons" to "الإضافات",
        "settings" to "الإعدادات"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(240.dp)
                .background(Color(0xF20E121B))
                .border(1.dp, Color(0x22FFFFFF))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("FILMR", color = NuvioGold, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            menuItems.forEach { (route, label) ->
                val isSelected = currentRoute == route
                var focused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused }
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                isSelected -> NuvioGold
                                focused -> Color(0xFF283142)
                                else -> Color.Transparent
                            }
                        )
                        .border(if (focused) 2.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
                        .clickable {
                            onSelectRoute(route)
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
