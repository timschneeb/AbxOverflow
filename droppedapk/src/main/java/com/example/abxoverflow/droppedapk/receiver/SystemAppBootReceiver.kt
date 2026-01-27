package com.example.abxoverflow.droppedapk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.abxoverflow.droppedapk.Mods

class SystemAppBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Mods.startShizuku(context)
    }
}