package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Finding the LuxTunes folder, also when the chosen SD card has been taken out. */
class LibraryLocationTest {
    @get:Rule val temp = TemporaryFolder()

    private fun storage(name: String, withLibrary: Boolean): File {
        val root = temp.newFolder(name)
        if (withLibrary) File(root, "LuxTunes").apply { mkdirs() }.resolve("library.db").writeText("x")
        return root
    }

    @Test
    fun theChosenStorageIsUsed() {
        val internal = storage("internal", true)
        val card = storage("card", true)
        assertEquals(File(card, "LuxTunes"), LuxFolder.locateIn(listOf(internal, card), StorageChoice.REMOVABLE).folder)
        assertEquals(File(internal, "LuxTunes"), LuxFolder.locateIn(listOf(internal, card), StorageChoice.INTERNAL).folder)
    }

    @Test
    fun aRemovedCardFallsBackToInternalStorage() {
        val internal = storage("internal", true)
        val found = LuxFolder.locateIn(listOf(internal), StorageChoice.REMOVABLE)
        assertTrue(found.cardMissing)
        assertEquals(File(internal, "LuxTunes"), found.folder)
    }

    @Test
    fun aRemovedCardWithNothingOnInternalStorageFindsNothing() {
        val found = LuxFolder.locateIn(listOf(storage("internal", false)), StorageChoice.REMOVABLE)
        assertTrue(found.cardMissing)
        assertNull(found.folder)
    }

    @Test
    fun aCardInThePhoneWithoutALibraryIsNotSwitchedAway() {
        // The card is in the phone but has no library yet (a sync in progress): don't switch.
        val found = LuxFolder.locateIn(listOf(storage("internal", true), storage("card", false)), StorageChoice.REMOVABLE)
        assertFalse(found.cardMissing)
        assertNull(found.folder)
    }

    @Test
    fun withNoChoiceTheNewestLibraryWins() {
        val internal = storage("internal", true)
        val card = storage("card", true)
        File(internal, "LuxTunes/library.db").setLastModified(1_000_000L)
        File(card, "LuxTunes/library.db").setLastModified(2_000_000L)
        assertEquals(File(card, "LuxTunes"), LuxFolder.locateIn(listOf(internal, card), null).folder)
    }
}
