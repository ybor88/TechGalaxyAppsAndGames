package com.volcanoescape.app

import android.app.Application
import org.osmdroid.config.Configuration

class VolcanoEscapeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
    }
}
