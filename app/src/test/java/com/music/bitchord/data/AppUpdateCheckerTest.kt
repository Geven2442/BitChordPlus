package com.music.bitchord.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun plusBuildIsNotOlderThanItsUpstreamRelease() {
        assertFalse(AppUpdateChecker.isNewer("1.10", "1.10-plus6"))
    }

    @Test
    fun newerUpstreamStillNudges() {
        assertTrue(AppUpdateChecker.isNewer("1.11", "1.10-plus6"))
        assertTrue(AppUpdateChecker.isNewer("1.10.1", "1.10-plus6"))
    }

    @Test
    fun betaStillOlderThanItsRelease() {
        assertTrue(AppUpdateChecker.isNewer("1.10", "1.10-beta2"))
    }
}
