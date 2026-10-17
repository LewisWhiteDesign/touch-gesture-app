package com.lewisw.lockgestures

import android.app.Application
import com.lewisw.lockgestures.model.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
    }
}
