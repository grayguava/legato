package com.grayguava.legato.source.covers

import com.grayguava.legato.settings.CoverSource
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSourcesTest {
    /**
     * A catalogue added to the settings but forgotten here would simply never
     * be asked, and nothing would say so. This is the line that says so.
     */
    @Test
    fun everySourceHasAResolver() {
        assertEquals(CoverSource.entries.toSet(), CoverSources.registered)
    }
}
