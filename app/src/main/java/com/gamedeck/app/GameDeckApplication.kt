package com.gamedeck.app

import android.app.Application
import android.content.Context
import com.gamedeck.core.input.InputEngine
import com.gamedeck.core.profile.GameApplicationRepository
import com.gamedeck.platform.input.AndroidBackendProvider
import com.gamedeck.platform.profile.AndroidGameApplicationRepository

/**
 * GameDeck application class.
 *
 * Provides application-level dependency wiring.
 */
class GameDeckApplication : Application() {

    lateinit var gameApplicationRepository: GameApplicationRepository
        private set

    lateinit var inputEngine: InputEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Wire dependencies
        gameApplicationRepository = AndroidGameApplicationRepository(this)
        inputEngine = InputEngine(AndroidBackendProvider(this))
    }

    companion object {
        lateinit var instance: GameDeckApplication
            private set

        fun get(context: Context): GameDeckApplication {
            return context.applicationContext as GameDeckApplication
        }
    }
}