package com.example.util.simpletimetracker.feature_dialogs.defaultTypesSelection.interactor

import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.rhythm.ActivityDefaults
import javax.inject.Inject

class GetDefaultRecordTypesInteractor @Inject constructor() {
    fun execute(): List<RecordType> = ActivityDefaults.roots.mapIndexed { index, type -> type.copy(id = index.toLong()) }
}
