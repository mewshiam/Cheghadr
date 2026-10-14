package ir.cheghadr.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ir.cheghadr.core.common.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A60),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF2E0),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4B635C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E0),
    onSecondaryContainer = Color(0xFF072019),
    tertiary = Color(0xFF815512),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB7),
    onTertiaryContainer = Color(0xFF291800),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF7FAF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4946),
    outline = Color(0xFF6F7976),
    outlineVariant = Color(0xFFBFC9C4),
    inverseSurface = Color(0xFF2B3230),
    inverseOnSurface = Color(0xFFEDF2EF),
    surfaceTint = Color(0xFF006A60),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C3),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005048),
    onPrimaryContainer = Color(0xFF9CF2E0),
    secondary = Color(0xFFB1CCC4),
    onSecondary = Color(0xFF1D352F),
    secondaryContainer = Color(0xFF334B45),
    onSecondaryContainer = Color(0xFFCDE8E0),
    tertiary = Color(0xFFF6BE62),
    onTertiary = Color(0xFF442B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDB7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1513),
    onBackground = Color(0xFFDFE4E1),
    surface = Color(0xFF0F1513),
    onSurface = Color(0xFFDFE4E1),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    inverseSurface = Color(0xFFDFE4E1),
    inverseOnSurface = Color(0xFF2B3230),
    surfaceTint = Color(0xFF80D5C3),
)

/** Restrained change indicators — never the only signal (arrows accompany color). */
object ChangeColors {
    val UpLight = Color(0xFF1B6E3A)
    val UpDark = Color(0xFF7ADB93)
    val DownLight = Color(0xFFB3261E)
    val DownDark = Color(0xFFF2B8B5)
}

/**
 * «چقدر؟» theme: Material 3 with the fixed brand palette, an optional
 * dynamic Material You palette on Android 12+, and full light/dark support.
 */
@Composable
fun CheghadrTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CheghadrTypography,
        content = content,
    )
}

/** Convenience accessors for change indicators that adapt to the theme. */
@Composable
fun changeUpColor(): Color =
    if (isSystemInDarkTheme()) ChangeColors.UpDark else ChangeColors.UpLight

@Composable
fun changeDownColor(): Color =
    if (isSystemInDarkTheme()) ChangeColors.DownDark else ChangeColors.DownLight
