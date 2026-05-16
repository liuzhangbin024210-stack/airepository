package com.majiang.counter

import android.app.Application
import com.majiang.counter.di.runAuthBootstrap
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MajiangApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runAuthBootstrap(this)
    }
}