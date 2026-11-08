package jp.yukirawa.standby.model

enum class Preset(
    val id: String,
    val label: String,
    val description: String,
    val clockFace: ClockFace,
    val layout: StandByLayout,
    val accent: AccentColor,
    val widgetSlots: List<WidgetType?>,
    val nightMode: Boolean = false,
) {
    MIDNIGHT(
        id = "midnight",
        label = "Midnight",
        description = "黒・白・シルバー。標準",
        clockFace = ClockFace.DIGITAL,
        layout = StandByLayout.HERO,
        accent = AccentColor.WHITE,
        widgetSlots = listOf(WidgetType.WEATHER, WidgetType.MUSIC, WidgetType.BATTERY),
    ),
    AURORA(
        id = "aurora",
        label = "Aurora",
        description = "寒色のグラデーション",
        clockFace = ClockFace.SOLAR,
        layout = StandByLayout.SPLIT,
        accent = AccentColor.CYAN,
        widgetSlots = listOf(WidgetType.WEATHER, WidgetType.BATTERY, WidgetType.MUSIC),
    ),
    EMBER(
        id = "ember",
        label = "Ember",
        description = "温かいアンバー",
        clockFace = ClockFace.ANALOG,
        layout = StandByLayout.STACK,
        accent = AccentColor.AMBER,
        widgetSlots = listOf(WidgetType.WEATHER, WidgetType.BATTERY, null),
    ),
    MONO(
        id = "mono",
        label = "Mono",
        description = "白黒ミニマル。時計と日付だけ",
        clockFace = ClockFace.DIGITAL,
        layout = StandByLayout.HERO,
        accent = AccentColor.WHITE,
        widgetSlots = listOf(null, null, null),
    ),
    STUDIO(
        id = "studio",
        label = "Studio",
        description = "紫・マゼンタ。音楽向け",
        clockFace = ClockFace.WORLD,
        layout = StandByLayout.SPLIT,
        accent = AccentColor.VIOLET,
        widgetSlots = listOf(WidgetType.MUSIC, WidgetType.WEATHER, null),
    ),
    CRIMSON(
        id = "crimson",
        label = "Crimson",
        description = "赤いナイトモード",
        clockFace = ClockFace.DIGITAL,
        layout = StandByLayout.HERO,
        accent = AccentColor.RED,
        widgetSlots = listOf(WidgetType.WEATHER, WidgetType.MUSIC, WidgetType.BATTERY),
        nightMode = true,
    ),
    ;

    companion object {
        fun fromId(id: String): Preset = entries.firstOrNull { it.id == id } ?: MIDNIGHT
    }
}

fun StandByConfig.applyPreset(preset: Preset): StandByConfig = copy(
    presetId = preset.id,
    clockFace = preset.clockFace,
    layout = preset.layout,
    accent = preset.accent,
    widgetSlots = preset.widgetSlots,
    nightMode = preset.nightMode,
)
