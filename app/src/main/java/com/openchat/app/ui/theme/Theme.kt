package com.openchat.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.openchat.app.data.ThemeMode

data class ChatColors(
    val sidebar: Color,
    val userBubble: Color,
    val composerBackground: Color,
    val composerBorder: Color,
    val codeBackground: Color,
    val codeHeader: Color,
    val codeText: Color,
    val inlineCodeBackground: Color,
    val subtleText: Color,
    val chipBackground: Color,
    val danger: Color,
    val dangerSurface: Color,
    val suggestionCard: Color
)

private val LightChatColors = ChatColors(
    sidebar = Color(0xFFF9F9F9),
    userBubble = Color(0xFFF4F4F4),
    composerBackground = Color(0xFFFFFFFF),
    composerBorder = Color(0xFFD9D9E3),
    codeBackground = Color(0xFF0F0F0F),
    codeHeader = Color(0xFF1E1E1E),
    codeText = Color(0xFFE8E8E8),
    inlineCodeBackground = Color(0xFFF0F0F0),
    subtleText = Color(0xFF6E6E80),
    chipBackground = Color(0xFFF0F0F0),
    danger = Color(0xFFB42318),
    dangerSurface = Color(0xFFFFEDEA),
    suggestionCard = Color(0xFFF7F7F8)
)

private val DarkChatColors = ChatColors(
    sidebar = Color(0xFF171717),
    userBubble = Color(0xFF2F2F2F),
    composerBackground = Color(0xFF2F2F2F),
    composerBorder = Color(0xFF3A3A3A),
    codeBackground = Color(0xFF0D0D0D),
    codeHeader = Color(0xFF1B1B1B),
    codeText = Color(0xFFE8E8E8),
    inlineCodeBackground = Color(0xFF3A3A3A),
    subtleText = Color(0xFF9B9B9B),
    chipBackground = Color(0xFF2F2F2F),
    danger = Color(0xFFFF8A80),
    dangerSurface = Color(0xFF3B1F1F),
    suggestionCard = Color(0xFF2A2A2A)
)

val LocalChatColors = staticCompositionLocalOf { LightChatColors }

private val LightColors = lightColorScheme(
    primary = Color(0xFF10A37F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7F5EC),
    onPrimaryContainer = Color(0xFF002118),
    secondary = Color(0xFF5E5E5E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECECEC),
    onSecondaryContainer = Color(0xFF1A1A1A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D0D0D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF5E5E5E),
    outline = Color(0xFFE3E3E3),
    outlineVariant = Color(0xFFECECEC),
    error = Color(0xFFB42318),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF19C37D),
    onPrimary = Color(0xFF002E20),
    primaryContainer = Color(0xFF00523E),
    onPrimaryContainer = Color(0xFF9EF3D3),
    secondary = Color(0xFFB4B4B4),
    onSecondary = Color(0xFF1B1B1B),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFECECEC),
    background = Color(0xFF212121),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF212121),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF2F2F2F),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF3E3E3E),
    outlineVariant = Color(0xFF303030),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 21.sp
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun OpenChatTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val chatColors = if (darkTheme) DarkChatColors else LightChatColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(activity.window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
