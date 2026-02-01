package com.example.abxoverflow.droppedapk

import android.app.Application
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui

class InjectedApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (!Sui.init(BuildConfig.APPLICATION_ID)) {
            ShizukuProvider.enableMultiProcessSupport(false)
        }
    }
}