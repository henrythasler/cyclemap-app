package com.henrythasler.cyclemap2

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.util.Log

object Util {
    fun isMyServiceRunning(serviceClass: Class<*>, mActivity: Activity): Boolean {
        val manager: ActivityManager =
            mActivity.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                Log.i("App", "Running")
                return true
            }
        }
        Log.i("App", "Not running")
        return false
    }
}