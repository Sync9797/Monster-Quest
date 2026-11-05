package com.monsterquest.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionsTest {
    @Test
    fun comparesVersions() {
        assertTrue(Versions.newer("v3.15.2", "3.15.1"))
        assertTrue(Versions.newer("3.16.0", "3.15.9"))
        assertTrue(Versions.newer("3.10.0", "3.9.0"))
        assertFalse(Versions.newer("3.15.1", "3.15.1"))
        assertFalse(Versions.newer("latest", "3.15.1"))
        assertEquals("v3.16.0", Versions.newest(listOf("latest", "v3.15.1", "v3.16.0", "v3.9.9"), "3.15.1"))
        assertNull(Versions.newest(listOf("latest", "v3.15.1", "v3.0.0"), "3.15.1"))
    }
}
