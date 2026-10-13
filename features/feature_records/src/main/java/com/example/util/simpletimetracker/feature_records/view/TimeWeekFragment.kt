package com.example.util.simpletimetracker.feature_records.view

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.example.util.simpletimetracker.core.base.BaseFragment
import com.example.util.simpletimetracker.core.utils.InsetConfiguration
import androidx.lifecycle.Lifecycle
import com.example.util.simpletimetracker.feature_records.R
import com.example.util.simpletimetracker.feature_records.databinding.TimeWeekFragmentBinding
import com.example.util.simpletimetracker.feature_statistics.review.WeekReviewFragment

class TimeWeekFragment : BaseFragment<TimeWeekFragmentBinding>() {
    override val inflater: (LayoutInflater, ViewGroup?, Boolean) -> TimeWeekFragmentBinding = TimeWeekFragmentBinding::inflate
    override var insetConfiguration: InsetConfiguration = InsetConfiguration.DoNotApply
    private var showingList = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showingList = savedInstanceState?.getBoolean("list") ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (childFragmentManager.findFragmentById(R.id.timeWeekOverview) == null) {
            childFragmentManager.beginTransaction().replace(
                R.id.timeWeekOverview,
                WeekReviewFragment().apply { arguments = Bundle().apply { putBoolean("timeOnly", true) } },
            ).commitNow()
        }
        requireNotNull(binding).timeWeekToggle.setOnClickListener {
            showingList = true
            display()
        }
        requireNotNull(binding).timeWeekShowGrid.setOnClickListener {
            showingList = false
            display()
        }
        display()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("list", showingList)
        super.onSaveInstanceState(outState)
    }

    private fun display() {
        val ui = binding
        if (showingList && childFragmentManager.findFragmentById(R.id.timeWeekList) == null) {
            childFragmentManager.beginTransaction().replace(R.id.timeWeekList, RecordsContainerFragment.newInstance()).commitNow()
        }
        ui.timeWeekList.isVisible = showingList
        ui.timeWeekOverview.isVisible = !showingList
        ui.timeWeekToggle.isSelected = showingList
        ui.timeWeekShowGrid.isSelected = !showingList
        childFragmentManager.findFragmentById(R.id.timeWeekList)?.let {
            childFragmentManager.beginTransaction().setMaxLifecycle(it, if (showingList) Lifecycle.State.RESUMED else Lifecycle.State.STARTED).commit()
        }
        childFragmentManager.findFragmentById(R.id.timeWeekOverview)?.let {
            childFragmentManager.beginTransaction().setMaxLifecycle(it, if (showingList) Lifecycle.State.STARTED else Lifecycle.State.RESUMED).commit()
        }
    }
}
