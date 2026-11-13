package com.evnavigator.iran

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.evnavigator.iran.designsystem.locale.LocaleManager
import com.evnavigator.iran.designsystem.theme.ThemeManager
import com.evnavigator.iran.navigation.AppFeatureScreenProvider
import com.evnavigator.iran.navigation.EVNavigatorNavGraph
import com.evnavigator.iran.navigation.rememberEVNavigatorNavController
import com.evnavigator.iran.ui.theme.EVNavigatorIranTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* GPS provider re-checks permission state when it starts */ }

    private fun requestLocationPermissionIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestLocationPermissionIfNeeded()
        
        // Initialize persistent locale and automotive theme
        LocaleManager.initialize(this)
        ThemeManager.initialize(this)
        
        setContent {
            EVNavigatorIranTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val appNavController = rememberEVNavigatorNavController()
                    EVNavigatorNavGraph(
                        navController = appNavController.navController,
                        screenProvider = AppFeatureScreenProvider
                    )
                }
            }
        }
    }
}
