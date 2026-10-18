package com.example.util.simpletimetracker

import android.app.Application
import android.os.StrictMode
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree

@HiltAndroidApp
class TimeTrackerApp : Application() {

    @javax.inject.Inject
    lateinit var fullBackupRepo: com.example.util.simpletimetracker.domain.backup.repo.FullBackupRepo

    @javax.inject.Inject
    lateinit var timerExitPrompt: com.example.util.simpletimetracker.core.interactor.TimerExitPromptImpl

    @javax.inject.Inject
    lateinit var activityDefaults: com.example.util.simpletimetracker.domain.rhythm.ActivityDefaultsInstaller

    @javax.inject.Inject
    lateinit var timerReminderPrompt: com.example.util.simpletimetracker.core.interactor.TimerReminderPrompt

    override fun onCreate() {
        super.onCreate()
        initLog()
        kotlinx.coroutines.runBlocking {
            fullBackupRepo.recoverInterruptedRestore()
            activityDefaults.ensure()
        }
        initLibraries()
        initStrictMode()
    }

    private fun initLog() {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }

    private fun initLibraries() {
        val config = BundledEmojiCompatConfig(applicationContext)
            .setReplaceAll(true)
        EmojiCompat.init(config)
    }

    private fun initStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
        }
    }
}
