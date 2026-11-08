package jp.yukirawa.standby.ui.theme

import androidx.compose.ui.graphics.Color
import jp.yukirawa.standby.model.AccentColor

data class AccentPalette(
    val primary: Color,
    val secondary: Color,
    val text: Color,
    val muted: Color,
    val card: Color,
    val cardBorder: Color,
    val background: Color = Color.Black,
)

fun accentPalette(accent: AccentColor, nightMode: Boolean): AccentPalette {
    if (nightMode) {
        return AccentPalette(
            primary = Color(0xFFFF453A),
            secondary = Color(0xFF8E1B14),
            text = Color(0xFFFF6B60),
            muted = Color(0xFFB33A31),
            card = Color(0xFF0A0403),
            cardBorder = Color(0xFF3A1411),
        )
    }
    val primary = when (accent) {
        AccentColor.WHITE -> Color(0xFFFFFFFF)
        AccentColor.CYAN -> Color(0xFF5AC8FA)
        AccentColor.AMBER -> Color(0xFFFFB340)
        AccentColor.VIOLET -> Color(0xFFBF5AF2)
        AccentColor.RED -> Color(0xFFFF453A)
        AccentColor.GREEN -> Color(0xFF30D158)
    }
    val secondary = when (accent) {
        AccentColor.WHITE -> Color(0xFFB8B8BD)
        AccentColor.CYAN -> Color(0xFF0A84FF)
        AccentColor.AMBER -> Color(0xFFFF9F0A)
        AccentColor.VIOLET -> Color(0xFF8E5CF7)
        AccentColor.RED -> Color(0xFFFF6961)
        AccentColor.GREEN -> Color(0xFF64D2A0)
    }
    return AccentPalette(
        primary = primary,
        secondary = secondary,
        text = Color(0xFFF5F5F7),
        muted = Color(0xFF8E8E93),
        card = Color(0xFF0B0B0B),
        cardBorder = Color(0x14FFFFFF),
    )
}
