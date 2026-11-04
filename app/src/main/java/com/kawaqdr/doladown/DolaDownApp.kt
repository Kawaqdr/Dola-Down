package com.kawaqdr.doladown

import android.app.Application
import android.content.Context
import android.util.Log
import com.lody.virtual.client.core.VirtualCore
import com.lody.virtual.client.stub.VASettings

class DolaDownApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        VASettings.ENABLE_IO_REDIRECT = true
        VASettings.ENABLE_INNER_SHORTCUT = false
        try {
            VirtualCore.get().startup(base)
        } catch (error: Throwable) {
            startupError = error
            Log.e(TAG, "Virtual runtime startup failed", error)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (startupError == null) {
            try {
                VirtualCore.get().initialize(object : VirtualCore.VirtualInitializer() {})
                runtimeReady = true
            } catch (error: Throwable) {
                startupError = error
                Log.e(TAG, "Virtual runtime initialization failed", error)
            }
        }
    }

    companion object {
        private const val TAG = "DolaDownVirtual"
        @Volatile var runtimeReady: Boolean = false
            private set
        @Volatile var startupError: Throwable? = null
            private set
    }
}
