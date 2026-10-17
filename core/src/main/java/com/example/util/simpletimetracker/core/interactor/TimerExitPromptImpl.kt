package com.example.util.simpletimetracker.core.interactor

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.util.simpletimetracker.core.R
import com.example.util.simpletimetracker.domain.rhythm.TimerExitPrompt
import com.example.util.simpletimetracker.domain.rhythm.TimerSession
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class TimerExitPromptImpl @Inject constructor(@ApplicationContext private val context: Context) : TimerExitPrompt, Application.ActivityLifecycleCallbacks {
    private var activity = WeakReference<Activity>(null)
    private var pending: CancellableContinuation<Boolean>? = null
    private var session: TimerSession? = null
    private var switching = false
    private var dialog: AlertDialog? = null

    init { (context as Application).registerActivityLifecycleCallbacks(this) }

    override suspend fun confirm(session: TimerSession, switching: Boolean): Boolean = withContext(Dispatchers.Main) {
        if (pending != null) return@withContext false
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            this@TimerExitPromptImpl.session = session
            this@TimerExitPromptImpl.switching = switching
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { if (pending === continuation) finish(false) } }
            if (activity.get() != null) show() else notifyPending()
        }
    }

    private fun show() {
        val host = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
        val current = session ?: return
        if (pending == null || dialog != null) return
        val minutes = ((System.currentTimeMillis() - current.startedAt).coerceAtLeast(0) / 60_000L)
        val remaining = 15 - minutes % 15
        dialog = AlertDialog.Builder(host).setTitle("可以现在结束，也可以再专注一会")
            .setMessage("已专注 $minutes 分钟，可以再继续约 $remaining 分钟，也可以现在${if (switching) "切换" else "结束"}。")
            .setNegativeButton(if (switching) "现在切换" else "现在结束") { _, _ -> finish(true) }
            .setPositiveButton("继续专注") { _, _ -> finish(false) }
            .setOnCancelListener { finish(false) }.create().also { it.show() }
    }

    private fun finish(approved: Boolean) {
        val continuation = pending
        pending = null; session = null
        dialog?.dismiss(); dialog = null
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        if (continuation?.isActive == true) continuation.resume(approved)
    }

    private fun notifyPending() {
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "计时退出确认", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, flags)
        try {
            manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.icon_category_image_notification)
                .setContentTitle("计时仍在继续").setContentText("打开应用，确认结束或切换这次专注。")
                .setContentIntent(open).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* Confirmation remains available on the next foreground resume. */ }
    }

    override fun onActivityResumed(value: Activity) { activity = WeakReference(value); show() }
    override fun onActivityPaused(value: Activity) {
        if (activity.get() === value) { activity.clear(); dialog?.dismiss(); dialog = null }
    }
    override fun onActivityCreated(value: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(value: Activity) = Unit
    override fun onActivityStopped(value: Activity) = Unit
    override fun onActivitySaveInstanceState(value: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(value: Activity) = Unit
    companion object { private const val CHANNEL = "FOCUS_EXIT"; private const val NOTIFICATION_ID = 5831 }
}
