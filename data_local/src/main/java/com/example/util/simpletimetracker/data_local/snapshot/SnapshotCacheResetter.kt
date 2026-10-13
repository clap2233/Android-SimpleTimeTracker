package com.example.util.simpletimetracker.data_local.snapshot

import com.example.util.simpletimetracker.data_local.activityFilter.ActivityFilterRepoImpl
import com.example.util.simpletimetracker.data_local.activitySuggestion.ActivitySuggestionRepoImpl
import com.example.util.simpletimetracker.data_local.category.CategoryRepoImpl
import com.example.util.simpletimetracker.data_local.category.RecordTypeCategoryRepoImpl
import com.example.util.simpletimetracker.data_local.complexRule.ComplexRuleRepoImpl
import com.example.util.simpletimetracker.data_local.favourite.FavouriteIconRepoImpl
import com.example.util.simpletimetracker.data_local.favourite.RecordTypeToFavouriteCommentRepoImpl
import com.example.util.simpletimetracker.data_local.record.RunningRecordRepoImpl
import com.example.util.simpletimetracker.data_local.recordsFilter.FavouriteRecordsFilterRepoImpl
import com.example.util.simpletimetracker.data_local.recordShortcut.RecordShortcutRepoImpl
import com.example.util.simpletimetracker.data_local.recordTag.RecordTagRepoImpl
import com.example.util.simpletimetracker.data_local.recordTag.RecordTypeToDefaultTagRepoImpl
import com.example.util.simpletimetracker.data_local.recordTag.RecordTypeToTagRepoImpl
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeGoalRepoImpl
import com.example.util.simpletimetracker.data_local.recordType.RecordTypeRepoImpl
import com.example.util.simpletimetracker.data_local.record.RecordRepoImpl
import javax.inject.Inject

class SnapshotCacheResetter @Inject constructor(
    private val repo0: ActivityFilterRepoImpl,
    private val repo1: ActivitySuggestionRepoImpl,
    private val repo2: CategoryRepoImpl,
    private val repo3: RecordTypeCategoryRepoImpl,
    private val repo4: ComplexRuleRepoImpl,
    private val repo5: FavouriteIconRepoImpl,
    private val repo6: RecordTypeToFavouriteCommentRepoImpl,
    private val repo7: RunningRecordRepoImpl,
    private val repo8: FavouriteRecordsFilterRepoImpl,
    private val repo9: RecordShortcutRepoImpl,
    private val repo10: RecordTagRepoImpl,
    private val repo11: RecordTypeToDefaultTagRepoImpl,
    private val repo12: RecordTypeToTagRepoImpl,
    private val repo13: RecordTypeGoalRepoImpl,
    private val repo14: RecordTypeRepoImpl,
    private val repo15: RecordRepoImpl,
) {
    suspend fun reset() {
        repo0.invalidateCache()
        repo1.invalidateCache()
        repo2.invalidateCache()
        repo3.invalidateCache()
        repo4.invalidateCache()
        repo5.invalidateCache()
        repo6.invalidateCache()
        repo7.invalidateCache()
        repo8.invalidateCache()
        repo9.invalidateCache()
        repo10.invalidateCache()
        repo11.invalidateCache()
        repo12.invalidateCache()
        repo13.invalidateCache()
        repo14.invalidateCache()
        repo15.invalidateCache()
    }
}
