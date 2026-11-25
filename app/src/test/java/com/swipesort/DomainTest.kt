package com.swipesort

import com.swipesort.domain.FileFilter
import com.swipesort.domain.FileKind
import com.swipesort.domain.FileQuery
import com.swipesort.domain.MediaQueryBuilder
import com.swipesort.domain.Names
import com.swipesort.domain.SortOrder
import com.swipesort.domain.TrashEntry
import com.swipesort.domain.TrashIndexCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    @Test fun fileKinds() {
        assertEquals(FileKind.GIF, FileKind.from("image/gif", "a.gif"))
        assertEquals(FileKind.IMAGE, FileKind.from("image/jpeg", "a.jpg"))
        assertEquals(FileKind.VIDEO, FileKind.from(null, "clip.MOV"))
        assertEquals(FileKind.PDF, FileKind.from("application/pdf", "x"))
        assertEquals(FileKind.APK, FileKind.from("application/vnd.android.package-archive", "a.apk"))
        assertEquals(FileKind.ARCHIVE, FileKind.from("application/zip", "a.zip"))
        assertEquals(FileKind.TEXT, FileKind.from("text/plain", "a.txt"))
        assertEquals(FileKind.OTHER, FileKind.from(null, "weird.bin"))
    }

    @Test fun queryEscapesSearchAndExcludesSortedFolders() {
        val b = MediaQueryBuilder.build(FileQuery(FileFilter.IMAGES, "50%_off", SortOrder.NAME), "/storage/emulated/0/SwipeSort/")
        assertEquals("/storage/emulated/0/SwipeSort/%", b.args[0])
        assertEquals("%50\\%\\_off%", b.args[1])
        assertTrue(b.selection.contains("media_type = 1"))
        assertEquals(b.args.size, b.selection.count { it == '?' })
        assertTrue(MediaQueryBuilder.build(FileQuery(FileFilter.DOCUMENTS), "/x").selection.contains("NOT IN (1, 2, 3)"))
    }

    @Test fun folderNames() {
        assertNotNull(Names.validateFolderName("  ", emptyList()))
        assertNotNull(Names.validateFolderName("Trips", listOf("trips")))
        assertNotNull(Names.validateFolderName("a/b", emptyList()))
        assertNotNull(Names.validateFolderName(".hidden", emptyList()))
        assertNull(Names.validateFolderName("Memes 2026", listOf("Trips")))
    }

    @Test fun uniqueNames() {
        val taken = setOf("photo.jpg", "photo (2).jpg", "README")
        assertEquals("photo (3).jpg", Names.uniqueName("photo.jpg") { it in taken })
        assertEquals("README (2)", Names.uniqueName("README") { it in taken })
        assertEquals("new.png", Names.uniqueName("new.png") { it in taken })
    }

    @Test fun trashIndexRoundTrip() {
        val es = listOf(
            TrashEntry("/t/1_a.jpg", "/DCIM/a.jpg", "a.jpg", 123, "image/jpeg", 1700000000000),
            TrashEntry("/t/2_we\tird\\name\n.txt", "/Docs/we\tird\\name\n.txt", "we\tird\\name\n.txt", 5, null, 42),
        )
        assertEquals(es, TrashIndexCodec.decode(TrashIndexCodec.encode(es)))
        assertTrue(TrashIndexCodec.decode("garbage").isEmpty())
    }
}
