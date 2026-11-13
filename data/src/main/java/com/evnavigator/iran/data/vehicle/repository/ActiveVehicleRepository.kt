package com.evnavigator.iran.data.vehicle.repository

import com.evnavigator.iran.data.vehicle.dao.VehicleDao
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

data class PendingNavigationHandoff(
    val originName: String? = null,
    val originLat: Double? = null,
    val originLon: Double? = null,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val autoStart: Boolean = false
)

/**
 * Explicit provenance of the vehicle's State of Charge (SOC).
 */
enum class VehicleSocSource {
    USER_MANUAL_INPUT,
    LIVE_VEHICLE_TELEMETRY,
    ENERGY_MODEL_ESTIMATE,
    UNCONFIRMED_DEFAULT,
    OBD_II_LIVE,
    USER_MANUAL,
    VEHICLE_PROFILE,
    DEFAULT_FALLBACK
}

/**
 * Explicit state of active vehicle selection, preventing silent `.firstOrNull()` fallbacks.
 */
sealed class ActiveVehicleSelectionState {
    data class Selected(val vehicle: VehicleEntity) : ActiveVehicleSelectionState()
    data object NoActiveVehicle : ActiveVehicleSelectionState()
    data class InvalidSelection(val requestedId: String) : ActiveVehicleSelectionState()

    val isSelected: Boolean
        get() = this is Selected
}

@Singleton
class ActiveVehicleRepository(
    private val vehicleDao: VehicleDao,
    persistenceFile: File? = null
) {
    private var customPersistenceFile: File? = persistenceFile

    @Inject
    constructor(
        vehicleDao: VehicleDao,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ) : this(
        vehicleDao = vehicleDao,
        persistenceFile = context.filesDir?.let { File(it, PERSISTENCE_FILE_NAME) }
    )

    init {
        synchronized(persistenceLock) {
            if (persistenceFile != null) {
                sharedConfiguredStorageFile = persistenceFile
                val props = loadProps(persistenceFile)
                val loadedId = props.getProperty("activeVehicleId")?.takeIf { it.isNotBlank() } ?: ""
                sharedActiveVehicleId.value = loadedId
                explicitSelectionMade = loadedId.isNotBlank()
                if (loadedId.isBlank()) {
                    sharedActiveVehicle.value = null
                    sharedSelectionState.value = ActiveVehicleSelectionState.NoActiveVehicle
                }

                val rawSocStr = props.getProperty("batterySocPercent")
                val rawSourceStr = props.getProperty("socSource")
                val rawTimestampStr = props.getProperty("lastSocUpdatedEpochMs")

                if (rawSocStr != null || rawSourceStr != null || rawTimestampStr != null) {
                    val parsedSoc = rawSocStr?.toDoubleOrNull()
                    val parsedSource = rawSourceStr?.let { raw ->
                        runCatching { VehicleSocSource.valueOf(raw) }.getOrNull()
                    }
                    val parsedTimestamp = rawTimestampStr?.toLongOrNull()

                    if (parsedSoc == null || !isValidSocPercent(parsedSoc) || parsedSource == null) {
                        sharedLastPersistenceError.value =
                            "Corrupted persisted SOC state (soc=$rawSocStr, source=$rawSourceStr, ts=$rawTimestampStr); reset to UNCONFIRMED_DEFAULT."
                        sharedIsPersistenceConfirmed.value = false
                        sharedBatterySocPercent.value = UNCONFIRMED_FALLBACK_SOC_PERCENT
                        sharedConfirmedBatterySocPercent.value = null
                        sharedEstimatedBatterySocPercent.value = null
                        sharedSocSource.value = VehicleSocSource.UNCONFIRMED_DEFAULT
                        sharedLastSocUpdatedEpochMs.value = null
                    } else {
                        val isFresh = isSocTimestampFresh(parsedTimestamp)
                        sharedBatterySocPercent.value = parsedSoc
                        sharedSocSource.value = parsedSource
                        sharedLastSocUpdatedEpochMs.value = parsedTimestamp
                        when {
                            isAuthoritativeSocSource(parsedSource) && isFresh -> {
                                sharedConfirmedBatterySocPercent.value = parsedSoc
                                sharedEstimatedBatterySocPercent.value = null
                            }
                            parsedSource == VehicleSocSource.ENERGY_MODEL_ESTIMATE -> {
                                sharedConfirmedBatterySocPercent.value = null
                                sharedEstimatedBatterySocPercent.value = if (isFresh) parsedSoc else null
                            }
                            else -> {
                                sharedConfirmedBatterySocPercent.value = null
                                sharedEstimatedBatterySocPercent.value = null
                            }
                        }
                        sharedIsPersistenceConfirmed.value =
                            persistenceFile.exists() && sharedLastPersistenceError.value == null
                    }
                } else {
                    sharedIsPersistenceConfirmed.value =
                        persistenceFile.exists() && sharedLastPersistenceError.value == null
                }
            }
        }
    }

    companion object {
        const val CANONICAL_DEFAULT_VEHICLE_ID = "vw_id4_crozz_2024"
        const val UNCONFIRMED_FALLBACK_SOC_PERCENT = 85.0
        const val PERSISTENCE_FILE_NAME = "ev_navigator_iran_active_vehicle_v10.properties"

        /** Maximum age (30 minutes) before a persisted or live SOC reading is treated as stale. */
        const val MAX_SOC_FRESHNESS_AGE_MS = 30L * 60L * 1000L

        /** Maximum allowed future clock skew (60 seconds) for SOC timestamps. */
        const val MAX_FUTURE_CLOCK_SKEW_MS = 60_000L

        private val persistenceLock = Any()

        @Volatile
        private var sharedConfiguredStorageFile: File? = null

        // Cold boot starts with "" (NoActiveVehicle) unless an explicit selection was previously persisted in durable storage
        private val sharedActiveVehicleId = MutableStateFlow("")
        private val sharedActiveVehicle = MutableStateFlow<VehicleEntity?>(null)
        private val sharedSelectionState = MutableStateFlow<ActiveVehicleSelectionState>(ActiveVehicleSelectionState.NoActiveVehicle)
        private val sharedBatterySocPercent = MutableStateFlow(UNCONFIRMED_FALLBACK_SOC_PERCENT)
        private val sharedConfirmedBatterySocPercent = MutableStateFlow<Double?>(null)
        private val sharedEstimatedBatterySocPercent = MutableStateFlow<Double?>(null)
        private val sharedSocSource = MutableStateFlow(VehicleSocSource.UNCONFIRMED_DEFAULT)
        private val sharedLastSocUpdatedEpochMs = MutableStateFlow<Long?>(null)
        private val sharedPendingHandoff = MutableStateFlow<PendingNavigationHandoff?>(null)
        private val sharedLastPersistenceError = MutableStateFlow<String?>(null)
        private val sharedIsPersistenceConfirmed = MutableStateFlow(false)
        private var explicitSelectionMade = false

        /**
         * Returns true ONLY for authoritative physical or explicit user-entered SOC sources.
         * Model estimates, vehicle profile defaults, and unconfirmed defaults are NEVER authoritative.
         */
        fun isAuthoritativeSocSource(source: VehicleSocSource): Boolean {
            return when (source) {
                VehicleSocSource.USER_MANUAL_INPUT,
                VehicleSocSource.USER_MANUAL,
                VehicleSocSource.LIVE_VEHICLE_TELEMETRY,
                VehicleSocSource.OBD_II_LIVE -> true
                VehicleSocSource.ENERGY_MODEL_ESTIMATE,
                VehicleSocSource.VEHICLE_PROFILE,
                VehicleSocSource.DEFAULT_FALLBACK,
                VehicleSocSource.UNCONFIRMED_DEFAULT -> false
            }
        }

        /**
         * Validates that an SOC percentage is finite (`!isNaN() && !isInfinite()`) and within `[0.0, 100.0]`.
         */
        fun isValidSocPercent(socPercent: Double): Boolean {
            return socPercent.isFinite() && socPercent in 0.0..100.0
        }

        /**
         * Validates that a timestamp is positive, not skewed into the future beyond tolerance,
         * and within the freshness window.
         */
        fun isSocTimestampFresh(
            timestampEpochMs: Long?,
            nowEpochMs: Long = System.currentTimeMillis()
        ): Boolean {
            if (timestampEpochMs == null || timestampEpochMs <= 0L) return false
            val delta = nowEpochMs - timestampEpochMs
            if (delta < -MAX_FUTURE_CLOCK_SKEW_MS) return false
            return delta <= MAX_SOC_FRESHNESS_AGE_MS
        }

        private fun loadProps(targetFile: File?): Properties {
            synchronized(persistenceLock) {
                val props = Properties()
                if (targetFile == null) {
                    return props
                }
                try {
                    if (targetFile.exists()) {
                        targetFile.inputStream().use { props.load(it) }
                        sharedLastPersistenceError.value = null
                    }
                } catch (e: Exception) {
                    sharedIsPersistenceConfirmed.value = false
                    sharedLastPersistenceError.value = "Failed to read vehicle state file (${targetFile.name}): ${e.message}"
                    System.err.println("ActiveVehicleRepository: ${sharedLastPersistenceError.value}")
                }
                return props
            }
        }

        private fun saveProps(
            targetFile: File?,
            vehicleId: String = sharedActiveVehicleId.value,
            soc: Double = sharedBatterySocPercent.value,
            source: VehicleSocSource = sharedSocSource.value,
            updatedAt: Long? = sharedLastSocUpdatedEpochMs.value
        ) {
            synchronized(persistenceLock) {
                if (targetFile == null) {
                    sharedIsPersistenceConfirmed.value = false
                    sharedLastPersistenceError.value =
                        "Durable storage path is not configured (missing Android context.filesDir or persistenceFile); state is held in memory only."
                    return
                }
                if (!isValidSocPercent(soc)) {
                    sharedIsPersistenceConfirmed.value = false
                    sharedLastPersistenceError.value =
                        "Refused to persist non-finite or out-of-range SOC value: $soc"
                    return
                }
                val tempFile = File(targetFile.parentFile ?: File("."), "${targetFile.name}.tmp")
                try {
                    targetFile.parentFile?.mkdirs()
                    val props = Properties()
                    props.setProperty("activeVehicleId", vehicleId)
                    props.setProperty("batterySocPercent", soc.toString())
                    props.setProperty("socSource", source.name)
                    if (updatedAt != null && updatedAt > 0L) {
                        props.setProperty("lastSocUpdatedEpochMs", updatedAt.toString())
                    }
                    FileOutputStream(tempFile).use { fos ->
                        props.store(fos, "EV Navigator Iran V10 Active Vehicle State")
                        fos.flush()
                        try {
                            fos.fd.sync()
                        } catch (_: Throwable) {
                        }
                    }
                    try {
                        Files.move(
                            tempFile.toPath(),
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE
                        )
                    } catch (_: Exception) {
                        Files.move(
                            tempFile.toPath(),
                            targetFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    sharedLastPersistenceError.value = null
                    sharedIsPersistenceConfirmed.value = true
                } catch (e: Exception) {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                    sharedIsPersistenceConfirmed.value = false
                    sharedLastPersistenceError.value = "Failed to persist vehicle state (${targetFile.name}): ${e.message}"
                    System.err.println("ActiveVehicleRepository: ${sharedLastPersistenceError.value}")
                }
            }
        }

        /**
         * Resets shared state for deterministic unit test isolation.
         * Defaults to "" (NoActiveVehicle) so cold-boot tests never inherit an unrequested vehicle.
         */
        fun resetForTesting(defaultVehicleId: String = "") {
            synchronized(persistenceLock) {
                try {
                    sharedConfiguredStorageFile?.let { if (it.exists()) it.delete() }
                } catch (e: Exception) {
                    sharedLastPersistenceError.value = e.message
                }
                sharedConfiguredStorageFile = null
                explicitSelectionMade = defaultVehicleId.isNotBlank()
                sharedActiveVehicleId.value = defaultVehicleId
                sharedActiveVehicle.value = null
                sharedSelectionState.value = ActiveVehicleSelectionState.NoActiveVehicle
                sharedBatterySocPercent.value = UNCONFIRMED_FALLBACK_SOC_PERCENT
                sharedConfirmedBatterySocPercent.value = null
                sharedEstimatedBatterySocPercent.value = null
                sharedSocSource.value = VehicleSocSource.UNCONFIRMED_DEFAULT
                sharedLastSocUpdatedEpochMs.value = null
                sharedPendingHandoff.value = null
                sharedLastPersistenceError.value = null
                sharedIsPersistenceConfirmed.value = false
            }
        }

        fun resetSharedStateForTesting(defaultVehicleId: String = "") = resetForTesting(defaultVehicleId)
    }

    private fun activeStorageFile(): File? = customPersistenceFile ?: sharedConfiguredStorageFile

    val activeVehicleId: StateFlow<String> = sharedActiveVehicleId.asStateFlow()
    val activeVehicle: StateFlow<VehicleEntity?> = sharedActiveVehicle.asStateFlow()
    val selectionState: StateFlow<ActiveVehicleSelectionState> = sharedSelectionState.asStateFlow()
    val batterySocPercent: StateFlow<Double> = sharedBatterySocPercent.asStateFlow()
    val confirmedBatterySocPercent: StateFlow<Double?> = sharedConfirmedBatterySocPercent.asStateFlow()
    val estimatedBatterySocPercent: StateFlow<Double?> = sharedEstimatedBatterySocPercent.asStateFlow()
    val socDataSource: StateFlow<VehicleSocSource> = sharedSocSource.asStateFlow()
    val socSource: StateFlow<VehicleSocSource> get() = socDataSource
    val lastSocUpdatedEpochMs: StateFlow<Long?> = sharedLastSocUpdatedEpochMs.asStateFlow()
    val pendingNavigationHandoff: StateFlow<PendingNavigationHandoff?> = sharedPendingHandoff.asStateFlow()
    val lastPersistenceError: StateFlow<String?> = sharedLastPersistenceError.asStateFlow()
    val isPersistenceConfirmed: StateFlow<Boolean> = sharedIsPersistenceConfirmed.asStateFlow()

    fun getActiveVehicleId(): String = synchronized(persistenceLock) { sharedActiveVehicleId.value }

    /**
     * Returns true ONLY when the current SOC reading comes from an authoritative source
     * (`USER_MANUAL_INPUT`, `USER_MANUAL`, `LIVE_VEHICLE_TELEMETRY`, `OBD_II_LIVE`),
     * has a finite value within `[0.0, 100.0]`, and has a fresh timestamp.
     *
     * Never returns true for `ENERGY_MODEL_ESTIMATE`, `VEHICLE_PROFILE`, `DEFAULT_FALLBACK`, or `UNCONFIRMED_DEFAULT`.
     */
    fun isSocConfirmed(nowEpochMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(persistenceLock) {
            val source = sharedSocSource.value
            val confirmedVal = sharedConfirmedBatterySocPercent.value ?: return false
            val ts = sharedLastSocUpdatedEpochMs.value ?: return false
            if (!isAuthoritativeSocSource(source)) return false
            if (!isValidSocPercent(confirmedVal)) return false
            return isSocTimestampFresh(ts, nowEpochMs)
        }
    }

    /**
     * Updates the current battery SOC with strict validation and explicit provenance.
     *
     * Rejects `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY`, values `< 0.0` or `> 100.0`,
     * and non-positive or future-skewed timestamps without mutating state or corrupting disk persistence.
     *
     * @return true if the update was valid and applied, false if rejected.
     */
    fun updateBatterySoc(
        newSoc: Double,
        source: VehicleSocSource = VehicleSocSource.USER_MANUAL_INPUT,
        timestampEpochMs: Long = System.currentTimeMillis(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        synchronized(persistenceLock) {
            if (!isValidSocPercent(newSoc)) {
                sharedIsPersistenceConfirmed.value = false
                sharedLastPersistenceError.value =
                    "Rejected invalid SOC value: $newSoc (must be finite within 0.0..100.0)"
                return false
            }

            if (source != VehicleSocSource.UNCONFIRMED_DEFAULT &&
                source != VehicleSocSource.DEFAULT_FALLBACK &&
                source != VehicleSocSource.VEHICLE_PROFILE
            ) {
                if (timestampEpochMs <= 0L || (nowEpochMs - timestampEpochMs) < -MAX_FUTURE_CLOCK_SKEW_MS) {
                    sharedIsPersistenceConfirmed.value = false
                    sharedLastPersistenceError.value =
                        "Rejected invalid SOC timestamp: $timestampEpochMs (now=$nowEpochMs)"
                    return false
                }
            }

            sharedBatterySocPercent.value = newSoc
            sharedSocSource.value = source

            when {
                isAuthoritativeSocSource(source) -> {
                    val isFresh = isSocTimestampFresh(timestampEpochMs, nowEpochMs)
                    sharedConfirmedBatterySocPercent.value = if (isFresh) newSoc else null
                    sharedEstimatedBatterySocPercent.value = null
                    sharedLastSocUpdatedEpochMs.value = timestampEpochMs
                    saveProps(targetFile = activeStorageFile(), soc = newSoc, source = source, updatedAt = timestampEpochMs)
                }
                source == VehicleSocSource.ENERGY_MODEL_ESTIMATE -> {
                    sharedConfirmedBatterySocPercent.value = null
                    sharedEstimatedBatterySocPercent.value = newSoc
                    sharedLastSocUpdatedEpochMs.value = timestampEpochMs
                    saveProps(targetFile = activeStorageFile(), soc = newSoc, source = source, updatedAt = timestampEpochMs)
                }
                else -> {
                    // UNCONFIRMED_DEFAULT, VEHICLE_PROFILE, DEFAULT_FALLBACK
                    sharedConfirmedBatterySocPercent.value = null
                    sharedEstimatedBatterySocPercent.value = null
                    sharedLastSocUpdatedEpochMs.value = null
                    saveProps(targetFile = activeStorageFile(), soc = newSoc, source = source, updatedAt = null)
                }
            }
            return true
        }
    }

    fun setPendingNavigationHandoff(handoff: PendingNavigationHandoff?) {
        synchronized(persistenceLock) {
            sharedPendingHandoff.value = handoff
        }
    }

    fun consumePendingNavigationHandoff(): PendingNavigationHandoff? {
        synchronized(persistenceLock) {
            val current = sharedPendingHandoff.value
            sharedPendingHandoff.value = null
            return current
        }
    }

    /**
     * Validates and selects a vehicle by ID.
     * Never silently falls back to `.firstOrNull()` if [id] does not exist in [vehicleDao].
     */
    suspend fun setActiveVehicleId(id: String): ActiveVehicleSelectionState {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) {
            synchronized(persistenceLock) {
                explicitSelectionMade = true
                sharedActiveVehicleId.value = ""
                sharedActiveVehicle.value = null
                sharedSelectionState.value = ActiveVehicleSelectionState.NoActiveVehicle
                saveProps(targetFile = activeStorageFile(), vehicleId = "")
                return ActiveVehicleSelectionState.NoActiveVehicle
            }
        }
        val fetched = vehicleDao.getVehicleById(trimmed)
        synchronized(persistenceLock) {
            explicitSelectionMade = true
            return if (fetched != null) {
                sharedActiveVehicleId.value = fetched.id
                sharedActiveVehicle.value = fetched
                val state = ActiveVehicleSelectionState.Selected(fetched)
                sharedSelectionState.value = state
                saveProps(targetFile = activeStorageFile(), vehicleId = fetched.id)
                state
            } else {
                // Strict: Never silently pick an unrelated vehicle when a specific ID was requested and not found
                sharedActiveVehicleId.value = ""
                sharedActiveVehicle.value = null
                val state = ActiveVehicleSelectionState.InvalidSelection(trimmed)
                sharedSelectionState.value = state
                state
            }
        }
    }

    /**
     * Returns the explicitly selected active vehicle from [vehicleDao], or `null` if no vehicle
     * has been selected (`NoActiveVehicle`) or if the selected ID does not exist (`InvalidSelection`).
     * Never auto-selects a default vehicle on cold boot.
     */
    suspend fun getActiveVehicle(): VehicleEntity? {
        val requestedId = synchronized(persistenceLock) { sharedActiveVehicleId.value }
        if (requestedId.isBlank()) {
            synchronized(persistenceLock) {
                sharedActiveVehicle.value = null
                sharedSelectionState.value = ActiveVehicleSelectionState.NoActiveVehicle
            }
            return null
        }
        val current = synchronized(persistenceLock) { sharedActiveVehicle.value }
        if (current != null && current.id == requestedId) {
            synchronized(persistenceLock) {
                sharedSelectionState.value = ActiveVehicleSelectionState.Selected(current)
            }
            return current
        }
        val fetched = vehicleDao.getVehicleById(requestedId)
        synchronized(persistenceLock) {
            return if (fetched != null) {
                sharedActiveVehicle.value = fetched
                sharedSelectionState.value = ActiveVehicleSelectionState.Selected(fetched)
                fetched
            } else {
                sharedActiveVehicle.value = null
                sharedSelectionState.value = ActiveVehicleSelectionState.InvalidSelection(requestedId)
                null
            }
        }
    }

    suspend fun refreshActiveVehicle() {
        getActiveVehicle()
    }
}
