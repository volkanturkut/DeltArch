package com.swordfish.lemuroid.app.shared.game

import android.content.Intent

interface GameLaunchDelegate {
    fun launchGameIntent(intent: Intent)
}
