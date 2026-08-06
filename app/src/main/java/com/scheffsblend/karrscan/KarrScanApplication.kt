package com.scheffsblend.karrscan

import android.app.Application
import com.scheffsblend.karrscan.data.AppDatabase

class KarrScanApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        BleDeviceRepository.initialize(database.deviceDao())
    }
}
