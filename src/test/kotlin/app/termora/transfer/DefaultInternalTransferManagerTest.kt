package app.termora.transfer

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DefaultInternalTransferManagerTest {

    @Test
    fun testReadAttributesReturnsOwnMtime() {
        val targetFile = Files.createTempFile("termora-1488-target", ".txt")
        try {
            targetFile.writeText("target content")
            val expectedMtime = 1_700_000_000_000L
            Files.setLastModifiedTime(targetFile, FileTime.fromMillis(expectedMtime))

            val attrs = readAttributes(targetFile)

            assertEquals(expectedMtime, attrs.lastModifiedTime)
            assertTrue(attrs.isFile)
            assertFalse(attrs.isDirectory)
        } finally {
            targetFile.deleteIfExists()
        }
    }

    // Regression for issue 1488: getTransferAction used to call askTransfer(source, source)
    // for an already-existing target file, so the dialog displayed the source's mtime
    // under the target column. The fix passes readAttributes(targetPath) instead, which
    // must reflect the target's own mtime.
    @Test
    fun testReadAttributesDoesNotLeakSourceMtime() {
        val sourceMtime = 1_700_000_000_000L
        val targetMtime = 1_800_000_000_000L

        val targetFile = Files.createTempFile("termora-1488-distinct", ".txt")
        try {
            targetFile.writeText("target")
            Files.setLastModifiedTime(targetFile, FileTime.fromMillis(targetMtime))

            val targetAttrs = readAttributes(targetFile)

            assertEquals(targetMtime, targetAttrs.lastModifiedTime)
            assertNotEquals(sourceMtime, targetAttrs.lastModifiedTime)
        } finally {
            targetFile.deleteIfExists()
        }
    }
}
