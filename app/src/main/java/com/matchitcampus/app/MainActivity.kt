package com.matchitcampus.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.matchitcampus.app.ui.navigation.MatchItRoot
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

/**
 * The app's only Activity. All screens are composables inside it, switched by Navigation
 * Compose, so this class just sets up the window and hands over to [MatchItRoot].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status and navigation bars; Scaffolds add the needed padding.
        enableEdgeToEdge()
        setContent {
            MatchItCampusTheme {
                MatchItRoot()
            }
        }
    }
}
