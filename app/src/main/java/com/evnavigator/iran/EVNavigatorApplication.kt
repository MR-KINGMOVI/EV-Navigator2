package com.evnavigator.iran

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration
import java.io.File

@HiltAndroidApp
class EVNavigatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Configure internal private storage for Osmdroid to prevent permission failures on Android 10-14
        val osmDir = File(noBackupFilesDir, "osmdroid")
        val tileCacheDir = File(osmDir, "tiles")
        osmDir.mkdirs()
        tileCacheDir.mkdirs()

        val osmConfig = Configuration.getInstance()
        osmConfig.osmdroidBasePath = osmDir
        osmConfig.osmdroidTileCache = tileCacheDir
        osmConfig.userAgentValue = "Mozilla/5.0 (Linux; Android) EVNavigatorIran/2.0"
        osmConfig.isMapViewHardwareAccelerated = true
        osmConfig.tileFileSystemCacheMaxBytes = 300L * 1024 * 1024
        osmConfig.tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        osmConfig.load(this, getSharedPreferences("osmdroid_config", Context.MODE_PRIVATE))
    }
}