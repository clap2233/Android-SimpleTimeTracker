package com.example.util.simpletimetracker.feature_records.di

import com.example.util.simpletimetracker.core.model.NavigationTab
import com.example.util.simpletimetracker.core.model.NavigationTabKey
import com.example.util.simpletimetracker.core.model.NavigationTabProvider
import com.example.util.simpletimetracker.feature_records.api.RecordsContainerOptionsListMapper
import com.example.util.simpletimetracker.feature_records.mapper.RecordsContainerOptionsListMapperImpl
import com.example.util.simpletimetracker.feature_records.view.TimeWeekFragment
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
interface RecordsModule {

    @Binds
    fun bindRecordsContainerOptionsListMapper(impl: RecordsContainerOptionsListMapperImpl): RecordsContainerOptionsListMapper

    companion object {
        @Provides
        @IntoMap
        @NavigationTabKey(NavigationTab.Records::class)
        fun bindNavigationTab(): NavigationTabProvider {
            return NavigationTabProvider { TimeWeekFragment() }
        }
    }
}
