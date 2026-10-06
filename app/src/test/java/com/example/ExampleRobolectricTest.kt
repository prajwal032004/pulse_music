package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.lyrics.LyricsEngine
import com.example.model.EqPreset
import com.example.smart.SmartActionResult
import com.example.smart.SmartFeatureEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PulseMusicRobolectricTest {

    @Test
    fun `verify app name resource is Pulse Music`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Pulse Music", appName)
    }

    @Test
    fun `verify lyrics parser extracts synced time tags correctly`() {
        val sampleLrc = """
            [00:05.50]First beat drops in the neon sky
            [00:12.30]Feel the pulse inside your soul
        """.trimIndent()

        val parsed = LyricsEngine.parseLrc(sampleLrc)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(5500L, parsed.lines[0].timestampMs)
        assertEquals("First beat drops in the neon sky", parsed.lines[0].text)
        assertEquals(12300L, parsed.lines[1].timestampMs)
    }

    @Test
    fun `verify eq presets have 10 frequency bands`() {
        EqPreset.values().forEach { preset ->
            assertEquals("Preset ${preset.name} must have 10 bands", 10, preset.gainsDb.size)
        }
    }
}
