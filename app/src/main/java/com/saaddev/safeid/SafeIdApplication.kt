package com.saaddev.safeid

import android.app.Application
import com.google.android.gms.ads.MobileAds

class SafeIdApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize AdMob SDK
        MobileAds.initialize(this) {}

        // Clear export cache on startup per PRD privacy specification
        try {
            val exportDir = cacheDir.resolve("safeid_exports")
            if (exportDir.exists()) {
                exportDir.deleteRecursively()
            }
        } catch (_: Exception) {
        }
    }
}

