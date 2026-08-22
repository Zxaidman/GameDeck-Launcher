package io.github.zxaidman.kestrel.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.zxaidman.kestrel.core.settings.AppTheme

/**
 * One palette, three surfaces, and a rule about which is used when.
 *
 * The application is built from Material 3 already, so a colour scheme is the whole of the change:
 * every screen, dialog, sheet, chip and button follows it at once, and nothing has to be repainted
 * by hand. What this is *not* is a redesign — the home page is still a developer's diagnostics
 * screen, and painting it does not make it a product. That is `CRIT-2`.
 *
 * **The accent is the same in all three.** A slate blue, chosen because it stays legible on white,
 * on grey and on black without becoming three different products; the schemes differ in what they
 * are painted on, not in what they are.
 *
 * **Three ways to be dark, because they are not the same thing on this hardware.** Grey dark is the
 * ordinary Material dark surface, where an unlit pixel is still a lit grey pixel. AMOLED dark is
 * true black, so on an OLED panel those pixels are actually off — a difference in what the screen
 * draws and what it costs to draw it, which on a handheld being held for hours is worth offering.
 *
 * **The overlay keeps its own palette, deliberately.** A pad is drawn over somebody else's
 * application and has to be legible on a white page and a black one both, so its colours answer to
 * that rather than to this. A pad that followed the application's theme would be invisible half the
 * time — see the note on the palette in `ControllerOverlay`.
 */
private val ACCENT = Color(0xFF5B6CC4)
private val ACCENT_DARK = Color(0xFF9AA6EE)
private val WARNING = Color(0xFFB3261E)
private val WARNING_DARK = Color(0xFFE0603A)

private val LIGHT: ColorScheme = lightColorScheme(
    primary = ACCENT,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3F8),
    onPrimaryContainer = Color(0xFF141A44),
    secondary = Color(0xFF585E72),
    onSecondary = Color.White,
    background = Color(0xFFFBFAFE),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBFAFE),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680),
    error = WARNING,
    onError = Color.White,
)

private val DARK_GREY: ColorScheme = darkColorScheme(
    primary = ACCENT_DARK,
    onPrimary = Color(0xFF1D2456),
    primaryContainer = Color(0xFF343B6E),
    onPrimaryContainer = Color(0xFFE0E3F8),
    secondary = Color(0xFFC1C5DD),
    onSecondary = Color(0xFF2A3042),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF45464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A),
    error = WARNING_DARK,
    onError = Color(0xFF3B1006),
)

/**
 * True black, and it has to be true black everywhere it shows.
 *
 * Material draws elevation as a tint over the surface, so a dialog on a black background comes out
 * dark grey unless the container colours are set as well — which would make this "black background,
 * grey everything" rather than an AMOLED scheme. The containers are dark rather than black, because
 * a sheet that is exactly the colour of the page behind it has no edge at all.
 */
private val DARK_AMOLED: ColorScheme = DARK_GREY.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1C1C21),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0B0B0E),
    surfaceContainer = Color(0xFF121216),
    surfaceContainerHigh = Color(0xFF17171C),
    surfaceContainerHighest = Color(0xFF1E1E24),
    outline = Color(0xFF7C7C86),
)

/** Whether the chosen theme paints on a dark ground, which the system bars need to know. */
public fun isDark(theme: AppTheme, systemIsDark: Boolean): Boolean = when (theme) {
    AppTheme.SYSTEM -> systemIsDark
    AppTheme.LIGHT -> false
    AppTheme.DARK_GREY, AppTheme.DARK_AMOLED -> true
}

@Composable
public fun KestrelTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val systemIsDark = isSystemInDarkTheme()
    val scheme = when (theme) {
        AppTheme.LIGHT -> LIGHT
        AppTheme.DARK_GREY -> DARK_GREY
        AppTheme.DARK_AMOLED -> DARK_AMOLED
        AppTheme.SYSTEM -> if (systemIsDark) DARK_GREY else LIGHT
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
