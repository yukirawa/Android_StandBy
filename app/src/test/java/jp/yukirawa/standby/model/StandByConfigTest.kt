package jp.yukirawa.standby.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandByConfigTest {
    @Test
    fun `json round trip preserves values`() {
        val config = StandByConfig(
            presetId = Preset.AURORA.id,
            layout = StandByLayout.SPLIT,
            clockFace = ClockFace.WORLD,
            accent = AccentColor.CYAN,
            fontWeight = FontWeightOption.LIGHT,
            use24Hour = false,
            showSeconds = true,
            showDate = false,
            datePattern = "yyyy/MM/dd",
            worldCities = listOf("Asia/Tokyo", "Europe/Paris"),
            widgetSlots = listOf(WidgetType.MUSIC, null, WidgetType.BATTERY),
            nightMode = true,
            brightness = 0.66f,
            dimDelaySeconds = 120,
            dimBrightness = 0.12f,
            burnInShift = false,
            burnInIntervalSeconds = 120,
            autoLaunch = false,
            wakeOnCharge = false,
            onboardingCompleted = true,
            hyperOsConfirmed = true,
        ).normalized()

        val restored = StandByConfig.fromJson(config.toJson())
        assertEquals(config, restored)
    }

    @Test
    fun `null and malformed json fall back to defaults`() {
        assertEquals(StandByConfig(), StandByConfig.fromJson(null))
        assertEquals(StandByConfig(), StandByConfig.fromJson(""))
        assertEquals(StandByConfig(), StandByConfig.fromJson("not json"))
    }

    @Test
    fun `normalization clamps out of range values`() {
        val config = StandByConfig(
            brightness = 4f,
            dimBrightness = 0f,
            dimDelaySeconds = -5,
            burnInIntervalSeconds = 5,
            worldCities = listOf("Asia/Tokyo", "Asia/Tokyo", "Europe/Paris", "Asia/Seoul"),
            datePattern = "  ",
        ).normalized()

        assertEquals(1f, config.brightness, 0.0001f)
        assertEquals(0.05f, config.dimBrightness, 0.0001f)
        assertEquals(0, config.dimDelaySeconds)
        assertEquals(60, config.burnInIntervalSeconds)
        assertEquals(listOf("Asia/Tokyo", "Europe/Paris", "Asia/Seoul"), config.worldCities)
        assertEquals("M月d日 (E)", config.datePattern)
    }

    @Test
    fun `widget slots never contain duplicates`() {
        val config = StandByConfig(
            widgetSlots = listOf(WidgetType.WEATHER, WidgetType.WEATHER, WidgetType.BATTERY),
        ).normalized()

        assertEquals(listOf(WidgetType.WEATHER, null, WidgetType.BATTERY), config.widgetSlots)
    }

    @Test
    fun `setting a widget moves it out of other slots`() {
        val config = StandByConfig()
            .withWidget(WidgetSlot.LEFT, WidgetType.MUSIC)

        assertEquals(WidgetType.MUSIC, config.widgetAt(WidgetSlot.LEFT))
        // MUSIC was in the center slot before, so that slot is now empty.
        assertNull(config.widgetAt(WidgetSlot.CENTER))
        assertEquals(WidgetType.BATTERY, config.widgetAt(WidgetSlot.RIGHT))
        assertEquals(1, config.widgetSlots.count { it == WidgetType.MUSIC })
    }

    @Test
    fun `preset application updates look and feel`() {
        val config = StandByConfig().applyPreset(Preset.EMBER)
        assertEquals(Preset.EMBER.id, config.presetId)
        assertEquals(ClockFace.ANALOG, config.clockFace)
        assertEquals(StandByLayout.STACK, config.layout)
        assertEquals(AccentColor.AMBER, config.accent)
        assertEquals(Preset.EMBER.widgetSlots, config.widgetSlots)
    }

    @Test
    fun `every preset resolves by id`() {
        Preset.entries.forEach { preset ->
            assertEquals(preset, Preset.fromId(preset.id))
        }
        assertEquals(Preset.MIDNIGHT, Preset.fromId("does-not-exist"))
    }

    @Test
    fun `widget list shorter than three is padded`() {
        val config = StandByConfig(widgetSlots = listOf(WidgetType.BATTERY)).normalized()
        assertEquals(3, config.widgetSlots.size)
        assertEquals(WidgetType.BATTERY, config.widgetSlots[0])
        assertTrue(config.widgetSlots.drop(1).all { it == null })
    }
}
