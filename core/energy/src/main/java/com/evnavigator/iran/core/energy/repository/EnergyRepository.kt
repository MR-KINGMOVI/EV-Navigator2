package com.evnavigator.iran.core.energy.repository

import com.evnavigator.iran.core.energy.model.*
import kotlinx.coroutines.flow.Flow

interface EnergyRepository {
    suspend fun saveEnergyConsumption(consumption: EnergyConsumption): Result<Unit>
    suspend fun getEnergyConsumption(vehicleId: String, fromTimestamp: Long, toTimestamp: Long): Result<List<EnergyConsumption>>
    suspend fun getEnergyConsumptionById(id: String): Result<EnergyConsumption?>
    suspend fun deleteEnergyConsumption(id: String): Result<Unit>
    
    suspend fun saveEnergyResult(result: EnergyResult): Result<Unit>
    suspend fun getEnergyResult(vehicleId: String, fromTimestamp: Long, toTimestamp: Long): Result<List<EnergyResult>>
    suspend fun getEnergyResultById(id: String): Result<EnergyResult?>
    suspend fun deleteEnergyResult(id: String): Result<Unit>
    
    suspend fun saveChargingStop(stop: ChargingStop): Result<Unit>
    suspend fun getChargingStops(vehicleId: String, fromTimestamp: Long, toTimestamp: Long): Result<List<ChargingStop>>
    suspend fun getChargingStopById(id: String): Result<ChargingStop?>
    suspend fun deleteChargingStop(id: String): Result<Unit>
    
    suspend fun saveEnergyEfficiencyMetrics(metrics: EnergyEfficiencyMetrics): Result<Unit>
    suspend fun getEnergyEfficiencyMetrics(vehicleId: String, fromTimestamp: Long, toTimestamp: Long): Result<List<EnergyEfficiencyMetrics>>
    suspend fun getEnergyEfficiencyMetricsById(id: String): Result<EnergyEfficiencyMetrics?>
    
    suspend fun saveEnergyPrediction(prediction: EnergyPrediction): Result<Unit>
    suspend fun getEnergyPrediction(vehicleId: String, routeSegmentId: String): Result<EnergyPrediction?>
    
    suspend fun saveSOCPrediction(prediction: SOCPrediction): Result<Unit>
    suspend fun getSOCPrediction(vehicleId: String, routeSegmentId: String): Result<SOCPrediction?>
    
    suspend fun getEnergyConsumptionHistory(vehicleId: String): Result<EnergyConsumptionHistory>
    suspend fun saveEnergyConsumptionHistory(history: EnergyConsumptionHistory): Result<Unit>
}