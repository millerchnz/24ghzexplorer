package com.millerchnz.ghzexplorer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.millerchnz.ghzexplorer.data.AppGraph
import com.millerchnz.ghzexplorer.ui.nav.AppNavHost
import com.millerchnz.ghzexplorer.ui.theme.GhzExplorerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GhzExplorerTheme {
                AppNavHost()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.onForeground()
    }

    override fun onStop() {
        AppGraph.onBackground()
        super.onStop()
    }
}
