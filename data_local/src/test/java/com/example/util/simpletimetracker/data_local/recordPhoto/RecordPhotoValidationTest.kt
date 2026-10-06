package com.example.util.simpletimetracker.data_local.recordPhoto

import org.junit.Assert.assertThrows
import org.junit.Test

class RecordPhotoValidationTest {

    @Test
    fun photoRejectsMissingOrAmbiguousOwner() {
        val photo = RecordPhotoDBO(recordId = 1, relativePath = "photos/a.jpg", sha256 = "a".repeat(64))
        assertThrows(IllegalArgumentException::class.java) { photo.copy(recordId = null) }
        assertThrows(IllegalArgumentException::class.java) { photo.copy(runningRecordId = 2) }
        assertThrows(IllegalArgumentException::class.java) { photo.copy(recordId = 0) }
    }

    @Test
    fun photoRejectsPathsOutsideManagedDirectoryAndInvalidHashes() {
        val photo = RecordPhotoDBO(recordId = 1, relativePath = "photos/a.jpg", sha256 = "a".repeat(64))
        listOf("../photos/a.jpg", "photos/../a.jpg", "/photos/a.jpg", "photos//a.jpg", "photos/a:bad.jpg", "photos/a\\b.jpg").forEach { path ->
            assertThrows(path, IllegalArgumentException::class.java) { photo.copy(relativePath = path) }
        }
        assertThrows(IllegalArgumentException::class.java) { photo.copy(sha256 = "invalid") }
        assertThrows(IllegalArgumentException::class.java) { photo.copy(position = -1) }
    }
}
