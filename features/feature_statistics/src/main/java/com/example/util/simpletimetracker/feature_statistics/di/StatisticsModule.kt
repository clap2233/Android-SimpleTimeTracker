package com.example.util.simpletimetracker.feature_statistics.di

import com.example.util.simpletimetracker.core.model.NavigationTab
import com.example.util.simpletimetracker.core.model.NavigationTabKey
import com.example.util.simpletimetracker.core.model.NavigationTabProvider
import com.example.util.simpletimetracker.feature_statistics.api.StatisticsContainerOptionsListMapper
import com.example.util.simpletimetracker.feature_statistics.mapper.StatisticsContainerOptionsListMapperImpl
import com.example.util.simpletimetracker.feature_statistics.review.WeekReviewFragment
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
interface StatisticsModule {

    @Binds
    fun bindStatisticsContainerOptionsListMapper(impl: StatisticsContainerOptionsListMapperImpl): StatisticsContainerOptionsListMapper

    companion object {
        @Provides
        @IntoMap
        @NavigationTabKey(NavigationTab.Statistics::class)
        fun bindNavigationTab(): NavigationTabProvider {
            return NavigationTabProvider { WeekReviewFragment() }
        }
    }
}
