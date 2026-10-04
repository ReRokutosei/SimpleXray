package com.simplexray.re.service

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class GeoUpdateScheduler(private val context: Context) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                GeoUpdateWorker.checkAndTriggerCatchUp(context)
                // Check periodically every hour while VPN is running
                delay(1 * 60 * 60 * 1000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
