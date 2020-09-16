/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.kotlin.stepcounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BroadcastReceiver to automatically start the [StepService] when the device booted.
 *
 *
 * Created by tiefensuche on 10.02.18.
 */
internal class OnBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action?.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, StepService::class.java))
            } else {
                context.startService(Intent(context, StepService::class.java))
            }
        }
    }
}
