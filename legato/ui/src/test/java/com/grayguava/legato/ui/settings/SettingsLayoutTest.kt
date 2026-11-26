package com.grayguava.legato.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    /** Pages with no row of their own on the main card: opened from the row of a page they belong to. */
    private val subPages = setOf("sources", "covers")

    /** A page left off the main list can only be reached by searching. */
    @Test fun everyPageIsOnTheMainList() {
        val listed = SETTINGS_HOME.flatMap { it.second }.toSet()
        val pages = SETTINGS_CATEGORIES.map { it.key }.toSet()
        assertEquals(emptySet<String>(), pages - listed - subPages)
        assertEquals(emptySet<String>(), listed - pages)
    }

    /** A page that isn't on the main card is at least named by one that is. */
    @Test fun everySubPageIsOpenedFromItsParent() {
        val titles = SETTINGS_CATEGORIES.flatMap { c -> c.groups.flatMap { it.second } }
        subPages.forEach { key ->
            val page = SETTINGS_CATEGORIES.first { it.key == key }
            assertTrue("${page.title} has no row on another page", page.title in titles)
        }
    }
}
