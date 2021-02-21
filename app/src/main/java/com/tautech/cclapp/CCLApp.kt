package com.tautech.cclapp

import android.app.Application
import androidx.work.Configuration
import java.util.concurrent.Executors

class CCLApp: Application(), Configuration.Provider {
    override fun getWorkManagerConfiguration() = Configuration.Builder()
        .setExecutor(Executors.newFixedThreadPool(2))
        .build()
}