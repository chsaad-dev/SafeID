package com.saaddev.safeid.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {

    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val COOLDOWN_MS = 180_000L // Strict 3-minute cooldown (180,000 ms)

    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastAdShownTimestamp: Long = 0L

    /**
     * Preloads Google's test Interstitial Ad silently in the background when online.
     */
    fun load(context: Context) {
        if (interstitialAd != null || isLoading) return
        if (!isNetworkAvailable(context)) return

        isLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    /**
     * Shows the interstitial ad if ready, online, and cooldown period has expired.
     * Fails silently if offline, not loaded, or on cooldown without blocking user actions.
     */
    fun showIfReady(activity: Activity, onDismissed: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastAdShownTimestamp

        val ad = interstitialAd
        if (ad == null || timeSinceLast < COOLDOWN_MS || !isNetworkAvailable(activity)) {
            // Fail silently without blocking user
            onDismissed()
            load(activity)
            return
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastAdShownTimestamp = System.currentTimeMillis()
                onDismissed()
                load(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                interstitialAd = null
                onDismissed()
                load(activity)
            }
        }

        ad.show(activity)
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

