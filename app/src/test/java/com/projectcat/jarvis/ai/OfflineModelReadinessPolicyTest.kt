package com.projectcat.jarvis.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineModelReadinessPolicyTest {
    @Test
    fun blocksLoadWhenAvailableRamIsBelowSafetyThreshold() {
        assertFalse(OfflineModelReadinessPolicy.canLoad(2_799_999_999L))
    }

    @Test
    fun permitsLoadAtOrAboveSafetyThreshold() {
        assertTrue(OfflineModelReadinessPolicy.canLoad(2_800_000_000L))
        assertTrue(OfflineModelReadinessPolicy.canLoad(3_200_000_000L))
    }
}
