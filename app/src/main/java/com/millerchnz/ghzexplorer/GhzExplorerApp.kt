package com.millerchnz.ghzexplorer

import android.app.Application
import com.millerchnz.ghzexplorer.data.AppGraph
import org.osmdroid.config.Configuration
import java.io.File

class GhzExplorerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = "24GHzExplorer/1.0 (radio survey)"
        val base = File(cacheDir, "osmdroid")
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles")
        AppForegroundTracker.register(this)
        AppGraph.init(this)
    }
}
