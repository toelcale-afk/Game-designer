package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.ProjectLayout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectRepositoryTest {
    private val repo = ProjectRepository(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun createsStandardFoldersAndStarterFiles() {
        val dir = repo.create("Ork Waaagh")
        ProjectLayout.FOLDERS.forEach { assertTrue(it, File(dir, it).isDirectory) }
        assertTrue(File(dir, "code/index.html").exists())
        assertEquals("Ork Waaagh", repo.readMeta(dir).name)
        assertEquals("ork-waaagh (2)", repo.create("Ork Waaagh").name)
    }

    @Test
    fun renameMoveDelete() {
        val dir = repo.create("Files")
        val f = repo.saveNew(File(dir, "sprites"), "a.png", byteArrayOf(1, 2))
        val renamed = repo.rename(f, "b.png")
        val moved = repo.move(renamed, File(dir, "tiles"))
        assertEquals(File(dir, "tiles/b.png"), moved)
        repo.delete(moved)
        assertFalse(moved.exists())
    }

    @Test(expected = IOException::class)
    fun refusesToTouchFilesOutsideProjects() {
        repo.delete(File(ApplicationProvider.getApplicationContext<Context>().filesDir, "quickbuild"))
    }

    @Test
    fun exportsZipAndWebFiles() {
        val dir = repo.create("Zip Me")
        repo.saveNew(File(dir, "sounds"), "laser.wav", byteArrayOf(9))
        repo.saveNew(File(dir, "builds"), "old.apk", byteArrayOf(1))
        val bos = ByteArrayOutputStream()
        repo.exportZip(dir, bos)
        val names = ZipInputStream(bos.toByteArray().inputStream()).use { z ->
            generateSequence { z.nextEntry }.map { it.name }.toList()
        }
        assertTrue(names.contains("${dir.name}/sounds/laser.wav"))
        assertFalse(names.any { it.contains("/builds/") })
        val web = repo.webFiles(dir)
        assertTrue(web.containsKey("index.html"))
        assertTrue(web.containsKey("sounds/laser.wav"))
    }
}
