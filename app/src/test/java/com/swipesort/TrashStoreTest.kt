package com.swipesort

import com.swipesort.data.FileOps
import com.swipesort.data.TrashStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun trashAndRestoreWithoutOverwriting() = runBlocking {
        val root = tmp.root
        val dcim = File(root, "DCIM").apply { mkdirs() }
        val photo = File(dcim, "photo.jpg").apply { writeBytes(ByteArray(1000)) }
        val trash = TrashStore(File(root, "SwipeSort/.trash"))

        val e = trash.trash(photo, "image/jpeg")
        assertFalse(photo.exists())
        assertTrue(File(e.trashedPath).exists())
        assertTrue(File(root, "SwipeSort/.trash/.nomedia").exists())

        File(dcim, "photo.jpg").writeText("new") // name taken again meanwhile
        val restored = trash.restore(e)
        assertEquals("photo (2).jpg", restored.name)
        assertEquals(1000L, restored.length())
        assertTrue(trash.list().isEmpty())
    }

    @Test fun indexSurvivesRestart() = runBlocking {
        val f = File(tmp.root, "a.txt").apply { writeText("x") }
        val e = TrashStore(File(tmp.root, ".trash")).trash(f, null)
        assertEquals(e.trashedPath, TrashStore(File(tmp.root, ".trash")).list().single().trashedPath)
    }

    @Test fun copyReportsProgressAndKeepsDate() = runBlocking {
        val big = File(tmp.root, "big.bin").apply { writeBytes(ByteArray(3_000_000)); setLastModified(1_500_000_000_000) }
        val dst = File(tmp.root, "out/big.bin")
        val seen = mutableListOf<Float>()
        FileOps.copy(big, dst) { seen += it }
        assertEquals(big.length(), dst.length())
        assertEquals(big.lastModified(), dst.lastModified())
        assertEquals(1f, seen.last())
    }
}
