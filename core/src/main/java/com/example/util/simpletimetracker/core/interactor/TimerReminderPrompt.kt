package com.example.util.simpletimetracker.core.interactor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerReminderPrompt @Inject constructor(@ApplicationContext context: Context) : Application.ActivityLifecycleCallbacks {
    private var activity = WeakReference<Activity>(null)
    init { (context as Application).registerActivityLifecycleCallbacks(this) }
    fun show(message: String) {
        Handler(Looper.getMainLooper()).post {
            val host = activity.get()?.takeUnless { it.isFinishing || it.isDestroyed } ?: return@post
            Snackbar.make(host.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .setAction("知道了") { }.show()
        }
    }
    override fun onActivityResumed(value: Activity) { activity = WeakReference(value) }
    override fun onActivityPaused(value: Activity) { if (activity.get() === value) activity.clear() }
    override fun onActivityCreated(value: Activity, state: Bundle?) = Unit
    override fun onActivityStarted(value: Activity) = Unit
    override fun onActivityStopped(value: Activity) = Unit
    override fun onActivitySaveInstanceState(value: Activity, state: Bundle) = Unit
    override fun onActivityDestroyed(value: Activity) = Unit
}
