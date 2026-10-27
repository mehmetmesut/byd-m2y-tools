package com.m2y.eq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the equalizer when the head unit boots or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in ACTIONS) EqService.start(context)
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
