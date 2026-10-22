package io.github.metricambient.luxtunes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URL
import java.nio.file.Files

class HardeningTest {
    @Test
    fun libraryPathsStayInsideTheLuxTunesFolder() {
        val root = Files.createTempDirectory("lux").toFile()
        val folder = File(root, "LuxTunes").apply { mkdirs() }
        File(root, "secret.jpg").writeText("not music")
        val song = "LuxTunes Media/Music/A/B/01 Song.mp3"
        assertEquals(File(folder, song).canonicalFile, LuxFolder.inside(folder, song))
        assertNull(LuxFolder.inside(folder, "../secret.jpg"))
        assertNull(LuxFolder.inside(folder, "LuxTunes Media/../../secret.jpg"))
        assertNull(LuxFolder.inside(folder, File(root, "secret.jpg").path))
        assertNull(LuxFolder.inside(folder, ""))
        assertNull(LuxFolder.inside(folder, null))
        Files.createSymbolicLink(File(folder, "out").toPath(), root.toPath())
        assertNull(LuxFolder.inside(folder, "out/secret.jpg"))           // a link can't lead out either
        root.deleteRecursively()
    }

    @Test
    fun updatesOnlyComeFromGitHubOverHttps() {
        assertTrue(Updater.allowed(URL("https://api.github.com/repos/x/releases/latest")))
        assertTrue(Updater.allowed(URL("https://github.com/x/releases/download/v1/a.apk")))
        assertTrue(Updater.allowed(URL("https://release-assets.githubusercontent.com/x")))
        assertFalse(Updater.allowed(URL("http://github.com/x")))
        assertFalse(Updater.allowed(URL("https://github.com.example.com/x")))
        assertFalse(Updater.allowed(URL("https://evilgithub.com/x")))
    }
}
