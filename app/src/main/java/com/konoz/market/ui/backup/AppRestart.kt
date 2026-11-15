package com.konoz.market.ui.backup

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Process

object AppRestart {
    /** Starts the launcher screen in a fresh task, then ends this process so the restored database is opened cleanly. */
    fun restart(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        context.startActivity(intent)
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 400)
    }
}
