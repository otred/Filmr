package com.filmr.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.filmr.tv.ui.FilmrAppRoot
import com.filmr.tv.ui.FilmrViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val darkScheme = darkColorScheme(
            primary = Color(0xFFE50914),
            background = Color(0xFF090B10),
            surface = Color(0xFF141821),
            onPrimary = Color.White,
            onBackground = Color.White,
            onSurface = Color.White
        )

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = darkScheme) {
                    val vm: FilmrViewModel = viewModel()
                    FilmrAppRoot(viewModel = vm)
                }
            }
        }
    }
}
