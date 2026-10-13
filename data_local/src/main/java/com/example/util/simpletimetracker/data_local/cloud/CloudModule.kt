package com.example.util.simpletimetracker.data_local.cloud

import com.example.util.simpletimetracker.data_local.snapshot.FullBackupRepoImpl
import com.example.util.simpletimetracker.domain.backup.repo.CloudBackupRepo
import com.example.util.simpletimetracker.domain.backup.repo.CloudSnapshotSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface CloudModule {
    @Binds fun backup(impl: CloudBackupRepoImpl): CloudBackupRepo
    @Binds fun source(impl: FullBackupRepoImpl): CloudSnapshotSource
}
