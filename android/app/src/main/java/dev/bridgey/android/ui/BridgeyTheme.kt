package dev.bridgey.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Curated Material 3 Expressive Violet palette matching the frequency-first design.
val ExpressiveBackgroundDark = Color(0xFF0E0D14)
val ExpressiveSurfaceDark = Color(0xFF1A1823)
val ExpressiveSurfaceHighDark = Color(0xFF262334)
val ExpressiveHeroViolet = Color(0xFF4A3B8C)
val ExpressiveLavender = Color(0xFFCFC0FF)
val ExpressiveOnLavender = Color(0xFF241559)
val ExpressiveConnectedGreen = Color(0xFF38D399)
val ExpressiveWarningAmber = Color(0xFFE59A4B)
val ExpressiveMutedTextDark = Color(0xFFA09BB0)

private val ExpressiveDarkScheme = darkColorScheme(
    primary = ExpressiveLavender,
    onPrimary = ExpressiveOnLavender,
    primaryContainer = ExpressiveHeroViolet,
    onPrimaryContainer = Color(0xFFF3EEFF),
    secondary = Color(0xFFBDB2DC),
    onSecondary = Color(0xFF292140),
    secondaryContainer = ExpressiveSurfaceHighDark,
    onSecondaryContainer = Color(0xFFEAE5F7),
    tertiary = ExpressiveConnectedGreen,
    onTertiary = Color(0xFF052E1E),
    background = ExpressiveBackgroundDark,
    onBackground = Color(0xFFF5F3FA),
    surface = ExpressiveSurfaceDark,
    onSurface = Color(0xFFF5F3FA),
    surfaceVariant = ExpressiveSurfaceHighDark,
    onSurfaceVariant = ExpressiveMutedTextDark,
    surfaceContainerLowest = Color(0xFF0A090F),
    surfaceContainerLow = Color(0xFF14121C),
    surfaceContainer = ExpressiveSurfaceDark,
    surfaceContainerHigh = ExpressiveSurfaceHighDark,
    surfaceContainerHighest = Color(0xFF312D42),
    outline = Color(0xFF3F3A52),
    outlineVariant = Color(0xFF2A2638),
    error = Color(0xFFFF8A8A),
)

private val ExpressiveLightScheme = lightColorScheme(
    primary = Color(0xFF5643A8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF53409E),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF60567D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE4F8),
    onSecondaryContainer = Color(0xFF231942),
    tertiary = Color(0xFF15803D),
    onTertiary = Color.White,
    background = Color(0xFFF6F4FB),
    onBackground = Color(0xFF181622),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181622),
    surfaceVariant = Color(0xFFECE8F6),
    onSurfaceVariant = Color(0xFF5E5870),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F7FD),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEEEAF8),
    surfaceContainerHighest = Color(0xFFE4DDF4),
    outline = Color(0xFFC8C1DA),
    outlineVariant = Color(0xFFE4DFF0),
)

private val ExpressiveTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun BridgeyExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ExpressiveDarkScheme else ExpressiveLightScheme,
        typography = ExpressiveTypography,
        content = content,
    )
}

/**
 * Material 3 Expressive segmented card corner shape:
 * 24.dp outer corners and 6.dp inner corners, separated by a 2.dp gap.
 */
fun segmentedItemShape(index: Int, count: Int, outer: Dp = 24.dp, inner: Dp = 6.dp): Shape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    else -> RoundedCornerShape(inner)
}

@Composable
fun SegmentedCard(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = segmentedItemShape(index, count)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = color,
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        Surface(
            shape = shape,
            color = color,
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun SegmentedGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@Composable
fun ExpressiveIconWell(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(containerColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun CircleIconWell(
    containerColor: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 52.dp,
    content: @Composable () -> Unit,
) {
    ExpressiveIconWell(
        containerColor = containerColor,
        size = size,
        shape = CircleShape,
        content = content,
    )
}
