package com.example.util.simpletimetracker.data_local.planning

import com.example.util.simpletimetracker.domain.planning.PlanException
import com.example.util.simpletimetracker.domain.planning.PlanRecordLink
import com.example.util.simpletimetracker.domain.planning.PlanSchedule
import com.example.util.simpletimetracker.domain.planning.PlanTask
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import com.example.util.simpletimetracker.domain.rhythm.TimerSession
import com.google.gson.Gson

internal object PlanningJson {
    private val gson = Gson()
    fun encode(value: Any): String = gson.toJson(value)
    fun task(value: String): PlanTask = gson.fromJson(value, PlanTask::class.java).copy()
    fun schedule(value: String): PlanSchedule = gson.fromJson(value, PlanSchedule::class.java).copy()
    fun exception(value: String): PlanException = gson.fromJson(value, PlanException::class.java).copy()
    fun link(value: String): PlanRecordLink = gson.fromJson(value, PlanRecordLink::class.java).copy()
    fun policy(value: String?): TimerPolicy? = value?.let { gson.fromJson(it, TimerPolicy::class.java).copy() }
    fun session(value: String?): TimerSession? = value?.let {
        val session = gson.fromJson(it, TimerSession::class.java)
        session.copy(policy = session.policy.copy())
    }
}
