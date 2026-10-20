package io.github.metricambient.luxtunes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.metricambient.luxtunes.data.Prefs
import io.github.metricambient.luxtunes.data.Repository
import io.github.metricambient.luxtunes.data.UsbState
import io.github.metricambient.luxtunes.playback.PlayerConnection

class LuxTunesApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var repository: Repository
        private set
    lateinit var player: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        repository = Repository(this, prefs)
        player = PlayerConnection(this)
        UsbState.watch(this) { repository.transferMode = it }
        // Check for a new sync whenever the app comes to the front.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                repository.refreshAsync()
            }
        })
    }
}
