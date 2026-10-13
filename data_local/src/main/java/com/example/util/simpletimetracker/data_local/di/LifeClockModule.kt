package com.example.util.simpletimetracker.data_local.di

import com.example.util.simpletimetracker.data_local.lifeClock.LifeClockSettingsRepoImpl
import com.example.util.simpletimetracker.data_local.review.ReviewRepoImpl
import com.example.util.simpletimetracker.data_local.record.RecordMutationRepoImpl
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationRepo
import com.example.util.simpletimetracker.domain.record.repo.RecordMutationFeedback
import com.example.util.simpletimetracker.core.interactor.RecordMutationFeedbackImpl
import com.example.util.simpletimetracker.domain.lifeClock.repo.LifeClockSettingsRepo
import com.example.util.simpletimetracker.domain.review.repo.ReviewRepo
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface LifeClockModule {
    @Binds
    fun bindFullBackupRepo(impl: com.example.util.simpletimetracker.data_local.snapshot.FullBackupRepoImpl): com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo

    @Binds
    fun bindPhotoRepo(impl: com.example.util.simpletimetracker.data_local.recordPhoto.RecordPhotoRepoImpl): com.example.util.simpletimetracker.domain.record.repo.RecordPhotoRepo

    @Binds
    fun bindSettingsRepo(impl: LifeClockSettingsRepoImpl): LifeClockSettingsRepo

    @Binds
    fun bindReviewRepo(impl: ReviewRepoImpl): ReviewRepo

    @Binds
    fun bindRecordMutationRepo(impl: RecordMutationRepoImpl): RecordMutationRepo

    @Binds
    fun bindMutationFeedback(impl: RecordMutationFeedbackImpl): RecordMutationFeedback
}
