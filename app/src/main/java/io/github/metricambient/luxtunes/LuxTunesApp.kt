package io.github.metricambient.luxtunes

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.metricambient.luxtunes.data.Prefs
import io.github.metricambient.luxtunes.data.Repository
import io.github.metricambient.luxtunes.data.UsbState
import io.github.metricambient.luxtunes.playback.PlayerConnection
import io.github.metricambient.luxtunes.ui.ArtFetcher
import io.github.metricambient.luxtunes.ui.ArtKeyer

class LuxTunesApp : Application(), ImageLoaderFactory {
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

    /** Pictures: album covers, falling back to the art inside the song file. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ArtKeyer())
            add(ArtFetcher.Factory())
        }
        .build()
}
