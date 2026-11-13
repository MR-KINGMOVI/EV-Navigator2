package com.evnavigator.iran.data.mapper

import com.evnavigator.iran.core.charging.AccessType
import com.evnavigator.iran.core.charging.ConnectorType
import com.evnavigator.iran.core.charging.SourceType
import com.evnavigator.iran.data.cache.EnvironmentCacheEntity
import com.evnavigator.iran.data.charging.entity.ChargingPortEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.entity.ChargingStationWithPortsEntity
import com.evnavigator.iran.data.intelligence.entity.TripHistorySummaryEntity
import com.evnavigator.iran.data.journey.ActiveJourneyEntity
import com.evnavigator.iran.data.seed.IranStationsCatalog
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import com.evnavigator.iran.data.vehicle.repository.ActiveVehicleSelectionState
import com.evnavigator.iran.data.vehicle.repository.PendingNavigationHandoff
import com.evnavigator.iran.data.vehicle.repository.VehicleSocSource
import com.evnavigator.iran.domain.intelligence.context.FieldVerificationState
import com.evnavigator.iran.domain.intelligence.context.UrlVerificationStatus
import com.evnavigator.iran.domain.model.catalog.*
import com.evnavigator.iran.domain.repository.DomainEnvironmentCacheRecord

fun VehicleEntity.toDomainVehicle(): DomainVehicle = DomainVehicle(
    id = id,
    name = name,
    manufacturer = manufacturer,
    model = model,
    generation = generation,
    modelYear = modelYear,
    trim = trim,
    market = market,
    powertrainType = powertrainType,
    curbMassKg = curbMassKg,
    grossVehicleMassKg = grossVehicleMassKg,
    lengthMm = lengthMm,
    widthMm = widthMm,
    heightMm = heightMm,
    wheelbaseMm = wheelbaseMm,
    groundClearanceMm = groundClearanceMm,
    dragCoefficient = dragCoefficient,
    frontalAreaM2 = frontalAreaM2,
    rollingResistanceCoefficient = rollingResistanceCoefficient,
    nominalBatteryCapacityWh = nominalBatteryCapacityWh,
    usableBatteryCapacityWh = usableBatteryCapacityWh,
    batteryChemistry = batteryChemistry,
    nominalVoltageV = nominalVoltageV,
    batteryConfiguration = batteryConfiguration,
    motorCount = motorCount,
    frontMotorPowerKw = frontMotorPowerKw,
    rearMotorPowerKw = rearMotorPowerKw,
    peakMotorPowerKw = peakMotorPowerKw,
    continuousMotorPowerKw = continuousMotorPowerKw,
    torqueNm = torqueNm,
    drivetrainType = drivetrainType,
    baselineConsumptionWhPer100Km = baselineConsumptionWhPer100Km,
    acChargingMaxPowerKw = acChargingMaxPowerKw,
    dcChargingMaxPowerKw = dcChargingMaxPowerKw,
    chargingVoltageRangeV = chargingVoltageRangeV,
    chargingCurrentRangeA = chargingCurrentRangeA,
    chargePortCount = chargePortCount,
    connectorTypes = connectorTypes,
    wheelDiameterMm = wheelDiameterMm,
    tireWidthMm = tireWidthMm,
    dataQuality = dataQuality,
    source = source,
    sourceVersion = sourceVersion,
    retrievedDate = retrievedDate,
    confidence = confidence,
    validationStatus = validationStatus,
    notes = notes,
    schemaVersion = schemaVersion,
    dataVersion = dataVersion,
    vehicleRecordVersion = vehicleRecordVersion,
    consumptionStandard = consumptionStandard,
    consumptionReferenceSpeedKmh = consumptionReferenceSpeedKmh,
    sourceUrl = sourceUrl,
    verifiedAt = verifiedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun DomainVehicle.toVehicleEntity(): VehicleEntity = VehicleEntity(
    id = id,
    name = name,
    manufacturer = manufacturer,
    model = model,
    generation = generation,
    modelYear = modelYear,
    trim = trim,
    market = market,
    powertrainType = powertrainType,
    curbMassKg = curbMassKg,
    grossVehicleMassKg = grossVehicleMassKg,
    lengthMm = lengthMm,
    widthMm = widthMm,
    heightMm = heightMm,
    wheelbaseMm = wheelbaseMm,
    groundClearanceMm = groundClearanceMm,
    dragCoefficient = dragCoefficient,
    frontalAreaM2 = frontalAreaM2,
    rollingResistanceCoefficient = rollingResistanceCoefficient,
    nominalBatteryCapacityWh = nominalBatteryCapacityWh,
    usableBatteryCapacityWh = usableBatteryCapacityWh,
    batteryChemistry = batteryChemistry,
    nominalVoltageV = nominalVoltageV,
    batteryConfiguration = batteryConfiguration,
    motorCount = motorCount,
    frontMotorPowerKw = frontMotorPowerKw,
    rearMotorPowerKw = rearMotorPowerKw,
    peakMotorPowerKw = peakMotorPowerKw,
    continuousMotorPowerKw = continuousMotorPowerKw,
    torqueNm = torqueNm,
    drivetrainType = drivetrainType,
    baselineConsumptionWhPer100Km = baselineConsumptionWhPer100Km,
    acChargingMaxPowerKw = acChargingMaxPowerKw,
    dcChargingMaxPowerKw = dcChargingMaxPowerKw,
    chargingVoltageRangeV = chargingVoltageRangeV,
    chargingCurrentRangeA = chargingCurrentRangeA,
    chargePortCount = chargePortCount,
    connectorTypes = connectorTypes,
    wheelDiameterMm = wheelDiameterMm,
    tireWidthMm = tireWidthMm,
    dataQuality = dataQuality,
    source = source,
    sourceVersion = sourceVersion,
    retrievedDate = retrievedDate,
    confidence = confidence,
    validationStatus = validationStatus,
    notes = notes,
    schemaVersion = schemaVersion,
    dataVersion = dataVersion,
    vehicleRecordVersion = vehicleRecordVersion,
    consumptionStandard = consumptionStandard,
    consumptionReferenceSpeedKmh = consumptionReferenceSpeedKmh,
    sourceUrl = sourceUrl,
    verifiedAt = verifiedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ConnectorType.toDomainConnectorType(): DomainConnectorType = when (this) {
    ConnectorType.TYPE_2 -> DomainConnectorType.TYPE_2
    ConnectorType.CCS_COMBO_1 -> DomainConnectorType.CCS_COMBO_1
    ConnectorType.CCS_COMBO_2 -> DomainConnectorType.CCS_COMBO_2
    ConnectorType.CHAdeMO -> DomainConnectorType.CHAdeMO
    ConnectorType.GB_T_AC -> DomainConnectorType.GB_T_AC
    ConnectorType.GB_T_DC -> DomainConnectorType.GB_T_DC
    ConnectorType.TESLA_NACS -> DomainConnectorType.TESLA_NACS
    ConnectorType.TESLA_SUPERCHARGER -> DomainConnectorType.TESLA_SUPERCHARGER
    ConnectorType.OTHER -> DomainConnectorType.OTHER
    ConnectorType.UNKNOWN -> DomainConnectorType.UNKNOWN
}

fun DomainConnectorType.toCoreConnectorType(): ConnectorType = when (this) {
    DomainConnectorType.TYPE_2 -> ConnectorType.TYPE_2
    DomainConnectorType.CCS_COMBO_1 -> ConnectorType.CCS_COMBO_1
    DomainConnectorType.CCS_COMBO_2 -> ConnectorType.CCS_COMBO_2
    DomainConnectorType.CHAdeMO -> ConnectorType.CHAdeMO
    DomainConnectorType.GB_T_AC -> ConnectorType.GB_T_AC
    DomainConnectorType.GB_T_DC -> ConnectorType.GB_T_DC
    DomainConnectorType.TESLA_NACS -> ConnectorType.TESLA_NACS
    DomainConnectorType.TESLA_SUPERCHARGER -> ConnectorType.TESLA_SUPERCHARGER
    DomainConnectorType.OTHER -> ConnectorType.OTHER
    DomainConnectorType.UNKNOWN -> ConnectorType.UNKNOWN
}

fun AccessType.toDomainAccessType(): DomainAccessType = when (this) {
    AccessType.PUBLIC -> DomainAccessType.PUBLIC
    AccessType.PRIVATE -> DomainAccessType.PRIVATE
    AccessType.RESTRICTED -> DomainAccessType.RESTRICTED
    AccessType.SEMI_PUBLIC -> DomainAccessType.SEMI_PUBLIC
    else -> DomainAccessType.UNKNOWN
}

fun DomainAccessType.toCoreAccessType(): AccessType = when (this) {
    DomainAccessType.PUBLIC -> AccessType.PUBLIC
    DomainAccessType.PRIVATE -> AccessType.PRIVATE
    DomainAccessType.RESTRICTED -> AccessType.RESTRICTED
    DomainAccessType.SEMI_PUBLIC -> AccessType.SEMI_PUBLIC
    DomainAccessType.UNKNOWN -> AccessType.PUBLIC
}

fun SourceType.toDomainSourceType(): DomainSourceType = when (this) {
    SourceType.NETWORK_PROVIDER -> DomainSourceType.NETWORK_PROVIDER
    SourceType.GOVERNMENT -> DomainSourceType.GOVERNMENT
    SourceType.MANUFACTURER -> DomainSourceType.MANUFACTURER
    SourceType.CROWDSOURCED -> DomainSourceType.CROWDSOURCED
    SourceType.USER_CONTRIBUTION -> DomainSourceType.USER_CONTRIBUTION
    SourceType.FIELD_SURVEY -> DomainSourceType.FIELD_SURVEY
    else -> DomainSourceType.UNKNOWN
}

fun DomainSourceType.toCoreSourceType(): SourceType = when (this) {
    DomainSourceType.NETWORK_PROVIDER -> SourceType.NETWORK_PROVIDER
    DomainSourceType.GOVERNMENT -> SourceType.GOVERNMENT
    DomainSourceType.MANUFACTURER -> SourceType.MANUFACTURER
    DomainSourceType.CROWDSOURCED -> SourceType.CROWDSOURCED
    DomainSourceType.USER_CONTRIBUTION -> SourceType.USER_CONTRIBUTION
    DomainSourceType.FIELD_SURVEY -> SourceType.FIELD_SURVEY
    DomainSourceType.UNKNOWN -> SourceType.UNKNOWN
}

fun ChargingStationEntity.toDomainStation(): DomainChargingStation {
    val cleanExternalUrl = sourceUrl?.takeIf {
        !it.startsWith("urn:iran-ev-registry:") &&
            (it.startsWith("https://") || it.startsWith("http://"))
    }
    val internalUrn = sourceReference?.takeIf { it.startsWith("urn:iran-ev-registry:") }
        ?: sourceUrl?.takeIf { it.startsWith("urn:iran-ev-registry:") }
    val lastVerifiedMs = (verifiedAt ?: validatedDate)?.takeIf { it > 0L && it != retrievedDate }
    val domainSourceType = sourceType.toDomainSourceType()
    val urlStatus = when {
        notes?.contains("URL_STATUS=HTTP_CONTENT_VERIFIED") == true -> UrlVerificationStatus.HTTP_CONTENT_VERIFIED
        cleanExternalUrl != null -> UrlVerificationStatus.LINK_UNCHECKED
        sourceUrl?.startsWith("urn:") == true -> UrlVerificationStatus.INTERNAL_URN_REJECTED
        else -> UrlVerificationStatus.MISSING
    }
    val method = when {
        notes?.contains("METHOD=FIELD_INSPECTION") == true -> VerificationMethod.FIELD_INSPECTION
        notes?.contains("METHOD=OPERATOR_FEED_OCPP") == true -> VerificationMethod.OPERATOR_FEED_OCPP
        validationStatus.equals("VERIFIED", ignoreCase = true) -> VerificationMethod.DOCUMENTED_PUBLIC_RELEASE
        validationStatus.equals("PARTIALLY_VERIFIED", ignoreCase = true) -> VerificationMethod.MUNICIPAL_SURVEY
        else -> VerificationMethod.UNVERIFIED_CORRIDOR_SEED
    }
    val evidence = SourceEvidence(
        sourceName = source,
        sourceType = domainSourceType,
        externalRecordId = externalId,
        sourceUrl = cleanExternalUrl,
        internalRegistryUrn = internalUrn,
        retrievedAtEpochMs = retrievedDate,
        lastVerifiedAtEpochMs = lastVerifiedMs,
        catalogGeneratedAtEpochMs = retrievedDate,
        verificationMethod = method,
        verificationStatus = validationStatus,
        portPowerProvenance = when (validationStatus.uppercase()) {
            "VERIFIED" -> SpecProvenance.OPERATOR_PUBLISHED_SPEC
            "PARTIALLY_VERIFIED" -> SpecProvenance.ESTIMATED_CORRIDOR_TEMPLATE
            else -> SpecProvenance.UNKNOWN
        },
        connectorProvenance = when (validationStatus.uppercase()) {
            "VERIFIED", "PARTIALLY_VERIFIED" -> SpecProvenance.OPERATOR_PUBLISHED_SPEC
            else -> SpecProvenance.UNKNOWN
        },
        evidenceNotes = notes,
        sourceChecksumOrVersion = datasetVersion,
        urlVerificationStatus = urlStatus
    )
    val verificationRecord = StationIndependentVerificationRecord(
        stationId = id,
        verifiedAt = lastVerifiedMs,
        retrievedAt = retrievedDate,
        datasetPublishedAt = retrievedDate,
        evidenceSource = source.takeIf { it.isNotBlank() },
        evidenceReference = externalId?.takeIf { it.isNotBlank() } ?: internalUrn,
        externalUrl = cleanExternalUrl,
        urlVerificationStatus = urlStatus,
        verificationMethod = method,
        rawVerificationStatus = validationStatus,
        operationalStatus = StationOperationalStatus.UNKNOWN
    )
    return DomainChargingStation(
        id = id,
        externalId = externalId,
        canonicalName = canonicalName,
        operatorId = operatorId,
        operatorName = operatorName,
        networkId = networkId,
        networkName = networkName,
        accessType = accessType.toDomainAccessType(),
        accessRestrictions = accessRestrictions,
        is247 = is247,
        openingHoursJson = openingHoursJson,
        paymentMethods = paymentMethods,
        amenities = amenities,
        contactPhone = contactPhone,
        contactEmail = contactEmail,
        website = website,
        notes = notes,
        latitude = latitude,
        longitude = longitude,
        province = province,
        city = city,
        locality = locality,
        roadContext = roadContext,
        address = address,
        source = source,
        sourceType = domainSourceType,
        sourceReference = internalUrn ?: sourceReference,
        version = version,
        retrievedDate = retrievedDate,
        createdDate = createdDate,
        validatedDate = lastVerifiedMs,
        confidence = confidence,
        validationStatus = validationStatus,
        isPrimarySource = isPrimarySource,
        datasetVersion = datasetVersion,
        coverage = coverage,
        coverageType = coverageType,
        sourceUrl = cleanExternalUrl,
        verifiedAt = lastVerifiedMs,
        coverageLevel = coverageLevel,
        lastAccessedAt = lastAccessedAt,
        sourceEvidence = evidence,
        operationalStatus = StationOperationalStatus.UNKNOWN,
        verificationRecord = verificationRecord
    )
}

fun DomainChargingStation.toStationEntity(): ChargingStationEntity = ChargingStationEntity(
    id = id,
    externalId = externalId,
    canonicalName = canonicalName,
    operatorId = operatorId,
    operatorName = operatorName,
    networkId = networkId,
    networkName = networkName,
    accessType = accessType.toCoreAccessType(),
    accessRestrictions = accessRestrictions,
    is247 = is247,
    openingHoursJson = openingHoursJson,
    paymentMethods = paymentMethods,
    amenities = amenities,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    website = website,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    province = province,
    city = city,
    locality = locality,
    roadContext = roadContext,
    address = address,
    source = source,
    sourceType = sourceType.toCoreSourceType(),
    sourceReference = sourceEvidence?.internalRegistryUrn ?: sourceReference,
    version = version,
    retrievedDate = retrievedDate,
    createdDate = createdDate,
    validatedDate = validatedDate,
    confidence = confidence,
    validationStatus = validationStatus,
    isPrimarySource = isPrimarySource,
    datasetVersion = datasetVersion,
    coverage = coverage,
    coverageType = coverageType,
    sourceUrl = sourceUrl?.takeIf { !it.startsWith("urn:iran-ev-registry:") },
    verifiedAt = verifiedAt,
    coverageLevel = coverageLevel,
    lastAccessedAt = lastAccessedAt
)

fun ChargingPortEntity.toDomainPort(): DomainChargingPort {
    val cleanExternalUrl = sourceReference?.takeIf {
        !it.startsWith("urn:iran-ev-registry:") &&
            (it.startsWith("https://") || it.startsWith("http://"))
    }
    val internalUrn = sourceReference?.takeIf { it.startsWith("urn:iran-ev-registry:") }
    val stationAudit = IranStationsCatalog.INDEPENDENT_STATION_VERIFICATION_REGISTRY[stationId]
    // V13.2 Section 3 & 4 Invariant: NEVER conflate retrievedDate with lastVerifiedAtEpochMs!
    val independentPortVerifiedMs = stationAudit?.verifiedAtEpochMs?.takeIf { it > 0L && it != retrievedDate }
    val portEvidence = SourceEvidence(
        sourceName = source ?: "UNKNOWN_SOURCE",
        sourceType = DomainSourceType.NETWORK_PROVIDER,
        externalRecordId = id,
        sourceUrl = cleanExternalUrl,
        internalRegistryUrn = internalUrn,
        retrievedAtEpochMs = retrievedDate ?: 0L,
        lastVerifiedAtEpochMs = independentPortVerifiedMs,
        catalogGeneratedAtEpochMs = retrievedDate ?: 0L,
        verificationMethod = when (validationStatus.uppercase()) {
            "VERIFIED" -> VerificationMethod.DOCUMENTED_PUBLIC_RELEASE
            "PARTIALLY_VERIFIED" -> VerificationMethod.MUNICIPAL_SURVEY
            else -> VerificationMethod.UNVERIFIED_CORRIDOR_SEED
        },
        verificationStatus = validationStatus,
        portPowerProvenance = when {
            powerKw == null || powerKw <= 0.0 -> SpecProvenance.UNKNOWN
            validationStatus.equals("VERIFIED", ignoreCase = true) -> SpecProvenance.OPERATOR_PUBLISHED_SPEC
            else -> SpecProvenance.ESTIMATED_CORRIDOR_TEMPLATE
        },
        connectorProvenance = when {
            connectorType == ConnectorType.UNKNOWN -> SpecProvenance.UNKNOWN
            validationStatus.equals("VERIFIED", ignoreCase = true) -> SpecProvenance.OPERATOR_PUBLISHED_SPEC
            else -> SpecProvenance.ESTIMATED_CORRIDOR_TEMPLATE
        },
        sourceChecksumOrVersion = datasetVersion
    )
    val tariff = IranStationsCatalog.getPortTariffSchedule(
        portId = id,
        stationId = stationId,
        isDc = isDc,
        nominalPowerKw = powerKw
    )
    val specRecord = PortIndependentSpecRecord(
        portId = id,
        stationId = stationId,
        isAc = isAc,
        isDc = isDc,
        rawConnectorType = connectorType.name,
        nominalPowerKw = powerKw,
        powerEvidenceSource = source,
        powerEvidenceReference = internalUrn ?: sourceReference,
        powerVerifiedAtEpochMs = independentPortVerifiedMs,
        powerVerificationState = when (validationStatus.uppercase()) {
            "VERIFIED" -> FieldVerificationState.VERIFIED
            "PARTIALLY_VERIFIED" -> FieldVerificationState.PARTIALLY_VERIFIED
            "ASSUMED" -> FieldVerificationState.ESTIMATED
            "UNVERIFIED" -> FieldVerificationState.UNVERIFIED
            else -> FieldVerificationState.UNKNOWN
        },
        connectorEvidenceSource = source,
        connectorVerifiedAtEpochMs = independentPortVerifiedMs,
        occupancyStatus = when (status.trim().uppercase()) {
            "AVAILABLE" -> PortOccupancyStatus.AVAILABLE
            "OCCUPIED", "BUSY", "IN_USE" -> PortOccupancyStatus.OCCUPIED
            "OUT_OF_SERVICE", "OUT_OF_ORDER", "FAULTED", "UNAVAILABLE", "OFFLINE" -> PortOccupancyStatus.OUT_OF_ORDER
            else -> PortOccupancyStatus.UNKNOWN
        },
        occupancyObservedAtEpochMs = if (status.equals("AVAILABLE", ignoreCase = true)) retrievedDate else null,
        tariffSchedule = tariff
    )
    return DomainChargingPort(
        id = id,
        stationId = stationId,
        connectorType = connectorType.toDomainConnectorType(),
        connectorCount = connectorCount,
        voltage = voltage,
        current = current,
        powerKw = powerKw,
        isAc = isAc,
        isDc = isDc,
        phaseCount = phaseCount,
        protocol = protocol,
        status = status,
        pricePerKwh = pricePerKwh,
        source = source,
        sourceReference = sourceReference,
        retrievedDate = retrievedDate,
        liveStatusUpdatedAtEpochMs = if (status.equals("AVAILABLE", ignoreCase = true)) retrievedDate else null,
        confidence = confidence,
        validationStatus = validationStatus,
        datasetVersion = datasetVersion,
        schemaVersion = schemaVersion,
        applicationVersion = applicationVersion,
        sourceEvidence = portEvidence,
        independentSpecRecord = specRecord,
        tariffSchedule = tariff
    )
}

fun DomainChargingPort.toPortEntity(): ChargingPortEntity = ChargingPortEntity(
    id = id,
    stationId = stationId,
    connectorType = connectorType.toCoreConnectorType(),
    connectorCount = connectorCount,
    voltage = voltage,
    current = current,
    powerKw = powerKw,
    isAc = isAc,
    isDc = isDc,
    phaseCount = phaseCount,
    protocol = protocol,
    status = status,
    pricePerKwh = pricePerKwh,
    source = source,
    sourceReference = sourceReference,
    retrievedDate = retrievedDate,
    confidence = confidence,
    validationStatus = validationStatus,
    datasetVersion = datasetVersion,
    schemaVersion = schemaVersion,
    applicationVersion = applicationVersion
)

fun ChargingStationWithPortsEntity.toDomainStationWithPorts(): DomainChargingStationWithPorts =
    DomainChargingStationWithPorts(
        station = station.toDomainStation(),
        ports = ports.map { it.toDomainPort() }
    )

fun DomainChargingStationWithPorts.toStationWithPortsEntity(): ChargingStationWithPortsEntity =
    ChargingStationWithPortsEntity(
        station = station.toStationEntity(),
        ports = ports.map { it.toPortEntity() }
    )

fun ActiveJourneyEntity.toDomainActiveJourney(): DomainActiveJourneyState = DomainActiveJourneyState(
    journeyId = journeyId,
    lifecycleState = lifecycleState,
    vehicleId = vehicleId,
    originName = originName,
    destinationName = destinationName,
    waypointsJson = waypointsJson,
    currentSocPercent = currentSocPercent,
    batteryCapacityKwh = batteryCapacityKwh,
    totalDistanceMeters = totalDistanceMeters,
    remainingDistanceMeters = remainingDistanceMeters,
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    lastUpdatedEpochMs = lastUpdatedEpochMs
)

fun DomainActiveJourneyState.toActiveJourneyEntity(): ActiveJourneyEntity = ActiveJourneyEntity(
    journeyId = journeyId,
    lifecycleState = lifecycleState,
    vehicleId = vehicleId,
    originName = originName,
    destinationName = destinationName,
    waypointsJson = waypointsJson,
    currentSocPercent = currentSocPercent,
    batteryCapacityKwh = batteryCapacityKwh,
    totalDistanceMeters = totalDistanceMeters,
    remainingDistanceMeters = remainingDistanceMeters,
    lastLatitude = lastLatitude,
    lastLongitude = lastLongitude,
    lastUpdatedEpochMs = lastUpdatedEpochMs
)

fun TripHistorySummaryEntity.toDomainSummary(): DomainTripHistorySummary = DomainTripHistorySummary(
    tripId = tripId,
    vehicleId = vehicleId,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs,
    distanceMeters = distanceMeters,
    predictedEnergyWh = predictedEnergyWh,
    measuredEnergyWh = measuredEnergyWh,
    averageSpeedKmh = averageSpeedKmh,
    ambientTempCelsius = ambientTempCelsius,
    isVerifiedTelemetry = isVerifiedTelemetry,
    modelCalibrationVersion = modelCalibrationVersion
)

fun DomainTripHistorySummary.toSummaryEntity(): TripHistorySummaryEntity = TripHistorySummaryEntity(
    tripId = tripId,
    vehicleId = vehicleId,
    startEpochMs = startEpochMs,
    endEpochMs = endEpochMs,
    distanceMeters = distanceMeters,
    predictedEnergyWh = predictedEnergyWh,
    measuredEnergyWh = measuredEnergyWh,
    averageSpeedKmh = averageSpeedKmh,
    ambientTempCelsius = ambientTempCelsius,
    isVerifiedTelemetry = isVerifiedTelemetry,
    modelCalibrationVersion = modelCalibrationVersion
)

fun VehicleSocSource.toDomainSocSource(): DomainVehicleSocSource = when (this) {
    VehicleSocSource.USER_MANUAL_INPUT -> DomainVehicleSocSource.USER_MANUAL_INPUT
    VehicleSocSource.USER_MANUAL -> DomainVehicleSocSource.USER_MANUAL
    VehicleSocSource.LIVE_VEHICLE_TELEMETRY -> DomainVehicleSocSource.LIVE_VEHICLE_TELEMETRY
    VehicleSocSource.OBD_II_LIVE -> DomainVehicleSocSource.OBD_II_LIVE
    VehicleSocSource.VEHICLE_PROFILE -> DomainVehicleSocSource.VEHICLE_PROFILE
    VehicleSocSource.ENERGY_MODEL_ESTIMATE -> DomainVehicleSocSource.ENERGY_MODEL_ESTIMATE
    VehicleSocSource.DEFAULT_FALLBACK -> DomainVehicleSocSource.DEFAULT_FALLBACK
    VehicleSocSource.UNCONFIRMED_DEFAULT -> DomainVehicleSocSource.UNCONFIRMED_DEFAULT
}

fun DomainVehicleSocSource.toDataSocSource(): VehicleSocSource = when (this) {
    DomainVehicleSocSource.USER_MANUAL_INPUT -> VehicleSocSource.USER_MANUAL_INPUT
    DomainVehicleSocSource.USER_MANUAL -> VehicleSocSource.USER_MANUAL
    DomainVehicleSocSource.LIVE_VEHICLE_TELEMETRY -> VehicleSocSource.LIVE_VEHICLE_TELEMETRY
    DomainVehicleSocSource.OBD_II_LIVE -> VehicleSocSource.OBD_II_LIVE
    DomainVehicleSocSource.VEHICLE_PROFILE -> VehicleSocSource.VEHICLE_PROFILE
    DomainVehicleSocSource.ENERGY_MODEL_ESTIMATE,
    DomainVehicleSocSource.ESTIMATED_DURING_NAVIGATION -> VehicleSocSource.ENERGY_MODEL_ESTIMATE
    DomainVehicleSocSource.DEFAULT_FALLBACK -> VehicleSocSource.DEFAULT_FALLBACK
    DomainVehicleSocSource.UNCONFIRMED_DEFAULT -> VehicleSocSource.UNCONFIRMED_DEFAULT
}

fun ActiveVehicleSelectionState.toDomainSelectionState(): DomainActiveVehicleSelectionState = when (this) {
    is ActiveVehicleSelectionState.Selected -> DomainActiveVehicleSelectionState.Selected(vehicle.toDomainVehicle())
    is ActiveVehicleSelectionState.InvalidSelection -> DomainActiveVehicleSelectionState.InvalidSelection(requestedId)
    ActiveVehicleSelectionState.NoActiveVehicle -> DomainActiveVehicleSelectionState.NoActiveVehicle
}

fun PendingNavigationHandoff.toDomainHandoff(): DomainPendingNavigationHandoff = DomainPendingNavigationHandoff(
    originName = originName,
    originLat = originLat,
    originLon = originLon,
    destinationName = destinationName,
    destinationLat = destinationLat,
    destinationLon = destinationLon,
    autoStart = autoStart
)

fun DomainPendingNavigationHandoff.toDataHandoff(): PendingNavigationHandoff = PendingNavigationHandoff(
    originName = originName,
    originLat = originLat,
    originLon = originLon,
    destinationName = destinationName,
    destinationLat = destinationLat,
    destinationLon = destinationLon,
    autoStart = autoStart
)

fun EnvironmentCacheEntity.toDomainRecord(): DomainEnvironmentCacheRecord = DomainEnvironmentCacheRecord(
    key = key,
    cacheType = cacheType,
    latitude = latitude,
    longitude = longitude,
    payloadJson = payloadJson,
    cachedAt = cachedAt,
    expiresAt = expiresAt
)

fun DomainEnvironmentCacheRecord.toCacheEntity(): EnvironmentCacheEntity = EnvironmentCacheEntity(
    key = key,
    cacheType = cacheType,
    latitude = latitude,
    longitude = longitude,
    payloadJson = payloadJson,
    cachedAt = cachedAt,
    expiresAt = expiresAt
)
