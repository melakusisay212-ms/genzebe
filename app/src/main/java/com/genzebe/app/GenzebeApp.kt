package com.genzebe.app

import android.app.Application
import com.genzebe.app.data.AppDatabase
import com.genzebe.app.data.Prefs
import com.genzebe.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the few objects shared by the screens, the SMS receiver and the share screen. */
class GenzebeApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var repo: Repository
        private set
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.build(this))
        prefs = Prefs(this)
    }
}
