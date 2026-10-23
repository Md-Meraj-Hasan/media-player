package com.meraj.mediaplayer

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MerajPlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
