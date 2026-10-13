package com.example.util.simpletimetracker.data_local.recordShortcut

import com.example.util.simpletimetracker.data_local.recordTag.RecordShortcutToRecordTagDBO
import com.example.util.simpletimetracker.domain.record.model.RecordBase
import com.example.util.simpletimetracker.domain.recordShortcut.model.RecordShortcut
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeClockTemplateMapperTest {
    @Test
    fun templateKeepsStableIdentityNameSummaryAndContext() {
        val shortcut = RecordShortcut(
            id = 7,
            target = RecordShortcut.Target.Record(
                typeId = 2, comment = "泡茶后", tags = listOf(RecordBase.Tag(4, null)),
                name = "早晨阅读", summary = "泡茶后阅读",
            ),
        )
        val mapper = RecordShortcutDataLocalMapper()
        val stored = RecordShortcutWithRecordTagsDBO(
            shortcut = mapper.map(shortcut),
            recordTags = listOf(RecordShortcutToRecordTagDBO(7, 4, null)),
        )
        assertEquals(shortcut, mapper.map(stored))
    }
}
