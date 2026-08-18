package com.gamedeck.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gamedeck.app.navigation.GameDeckNavHost
import com.gamedeck.app.ui.theme.GameDeckTheme

/**
 * Main activity for GameDeck.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameDeckTheme {
                GameDeckNavHost()
            }
        }
    }
}