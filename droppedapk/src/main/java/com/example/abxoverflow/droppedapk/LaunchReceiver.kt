package com.example.abxoverflow.droppedapk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LaunchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Mods.runAll()
    }
}
