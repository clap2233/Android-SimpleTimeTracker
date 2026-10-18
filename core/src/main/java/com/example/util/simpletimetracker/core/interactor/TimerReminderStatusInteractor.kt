package com.example.util.simpletimetracker.core.interactor

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.record.model.RunningRecord
import com.example.util.simpletimetracker.domain.rhythm.ActivityPolicy
import com.example.util.simpletimetracker.domain.rhythm.TimerPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class TimerReminderStatusInteractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PrefsInteractor,
) {
    suspend fun describe(record: RunningRecord): String {
        val session = record.timerSession ?: return "本次沿用旧计时设置；新规则从下次开始生效。"
        val policy = session.policy
        if (policy.mode != TimerPolicy.Mode.INTERVAL) return ActivityPolicy.label(policy)
        if (session.muted) return "本次周期提醒已关闭，计时仍在继续。"
        val lines = mutableListOf(ActivityPolicy.label(policy))
        policy.nextReminder(session.startedAt, System.currentTimeMillis(), 0)?.let {
            lines += "下个提醒节点 ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))}"
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) lines += "系统通知未开启"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).getNotificationChannel("ACTIVITY")
            if (channel?.importance == NotificationManager.IMPORTANCE_NONE) lines += "计时提醒渠道已关闭"
        }
        if (Build.VERSION.SDK_INT >= 31 && !(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) lines += "精确提醒未授权，系统可能延后送达"
        if (Build.VERSION.SDK_INT >= 28 && (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted) lines += "系统限制后台运行"
        if ((context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode) lines += "系统省电模式已开启"
        val start = prefs.getActivityReminderDoNotDisturbStart()
        val end = prefs.getActivityReminderDoNotDisturbEnd()
        if (start != end) {
            fun clock(value: Long) = "%02d:%02d".format(value / 3_600_000, value / 60_000 % 60)
            lines += "应用免打扰 ${clock(start)}—${clock(end)}，其间提醒顺延"
        }
        lines += "点击查看系统提醒设置"
        return lines.joinToString(" · ")
    }

    fun openSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 31 && !(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
