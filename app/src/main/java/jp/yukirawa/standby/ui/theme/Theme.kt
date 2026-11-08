package jp.yukirawa.standby.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StandByColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5AC8FA),
    onSecondary = Color.Black,
    tertiary = Color(0xFFBF5AF2),
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0B0B0B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF171717),
    onSurfaceVariant = Color(0xFFB8B8B8),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF252525),
    error = Color(0xFFFF453A),
)

private val StandByShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StandByTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = StandByColors,
        motionScheme = MotionScheme.expressive(),
        shapes = StandByShapes,
        typography = Typography(),
        content = content,
    )
}
