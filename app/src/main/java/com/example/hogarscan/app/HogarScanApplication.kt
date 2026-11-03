package com.example.hogarscan.app

import android.app.Application

class HogarScanApplication : Application() {

    val container: AppContainer by lazy {
        AppContainer(this)
    }
}