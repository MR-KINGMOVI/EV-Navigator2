package com.evnavigator.iran.automotive

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.evnavigator.iran.automotive.session.AutomotiveSession

/**
 * Android Auto service implementation for EV Navigator Iran.
 * This service handles the connection lifecycle with Android Auto hosts.
 *
 * Security:
 * Strictly separates debug development hosts from production release hosts.
 * ALLOW_ALL_HOSTS_VALIDATOR is only permitted during local debug builds (e.g. DHU).
 * In release mode, certified Android Auto host validation is enforced.
 */
class CarAppService : CarAppService() {
    companion object {
        private const val TAG = "CarAppService"
    }

    override fun createHostValidator(): HostValidator {
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return if (isDebuggable) {
            Log.w(TAG, "Running in debuggable mode: permitting DHU / developer hosts")
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            Log.i(TAG, "Running in production mode: enforcing certified Android Auto host allowlist")
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(com.evnavigator.iran.R.array.certified_hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        Log.d(TAG, "Creating automotive session")
        return AutomotiveSession()
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        Log.d(TAG, "Creating automotive session with info: $sessionInfo")
        return AutomotiveSession()
    }
}
