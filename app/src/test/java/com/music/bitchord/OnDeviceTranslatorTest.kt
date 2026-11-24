package com.music.bitchord

import com.music.bitchord.data.lyrics.OnDeviceTranslator
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class OnDeviceTranslatorTest {
    @Test
    fun `numbered reply maps back to the lines in order`() {
        val reply = "1. 你好\n2) 世界\n3、再見"
        assertEquals(listOf("你好", "世界", "再見"), OnDeviceTranslator.parse(reply, 3))
    }

    @Test
    fun `a reply with a missing line is rejected`() {
        assertNull(OnDeviceTranslator.parse("1. 你好\n3. 再見", 3))
    }
}
