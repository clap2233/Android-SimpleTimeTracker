package com.example.util.simpletimetracker.feature_statistics.review

import com.example.util.simpletimetracker.core.interactor.GetChangeRecordNavigationParamsInteractor
import com.example.util.simpletimetracker.core.mapper.RecordViewDataMapper
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.Record
import com.example.util.simpletimetracker.navigation.Router
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRecordFromMainParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRecordParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRunningRecordFromMainParams
import com.example.util.simpletimetracker.navigation.params.screen.ChangeRunningRecordParams
import javax.inject.Inject

class ReviewRecordNavigator @Inject constructor(
    private val router: Router,
    private val mapper: RecordViewDataMapper,
    private val params: GetChangeRecordNavigationParamsInteractor,
    private val prefs: PrefsInteractor,
) {
    fun openRunning(id: Long) {
        router.navigate(
            ChangeRunningRecordFromMainParams(
                ChangeRunningRecordParams(
                    "", id,
                    ChangeRunningRecordParams.From.Records, null,
                ),
            ),
        )
    }

    suspend fun open(record: Record?, data: WeekReviewScreen, gapStart: Long = 0, gapEnd: Long = 0) {
        val dark = prefs.getDarkMode()
        val military = prefs.getUseMilitaryTimeFormat()
        val seconds = prefs.getShowSeconds()
        val duration = prefs.getDurationFormat()
        val item = if (record == null) {
            mapper.mapToUntracked(gapStart, gapEnd, dark, military, duration, seconds)
        } else {
            mapper.map(record, data.types.find { it.id == record.typeId }, data.tags, dark, military, duration, seconds)
        }
        router.navigate(
            ChangeRecordFromMainParams(
                params.execute(
                    item, ChangeRecordParams.From.Records,
                    0, military, seconds, duration, null,
                ),
            ),
        )
    }
}
