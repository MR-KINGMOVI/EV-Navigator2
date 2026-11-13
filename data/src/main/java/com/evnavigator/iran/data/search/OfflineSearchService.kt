package com.evnavigator.iran.data.search

import com.evnavigator.iran.data.charging.entity.ChargingStationEntity
import com.evnavigator.iran.data.charging.repository.ChargingStationRepository
import com.evnavigator.iran.data.vehicle.entity.VehicleEntity
import com.evnavigator.iran.data.vehicle.repository.VehicleRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * Categorized search result from offline indexed databases.
 */
sealed class OfflineSearchResult(val rankScore: Double) : Comparable<OfflineSearchResult> {
    data class ChargingStationItem(
        val id: String,
        val name: String,
        val address: String?,
        val operator: String?,
        val score: Double
    ) : OfflineSearchResult(score)

    data class VehicleItem(
        val id: String,
        val name: String,
        val manufacturer: String,
        val model: String,
        val modelYear: Int,
        val score: Double
    ) : OfflineSearchResult(score)

    override fun compareTo(other: OfflineSearchResult): Int {
        // Higher score first; if equal, break ties deterministically
        val cmp = other.rankScore.compareTo(this.rankScore)
        if (cmp != 0) return cmp
        val thisName = when (this) {
            is ChargingStationItem -> this.name
            is VehicleItem -> this.name
        }
        val otherName = when (other) {
            is ChargingStationItem -> other.name
            is VehicleItem -> other.name
        }
        return thisName.compareTo(otherName)
    }
}

/**
 * Text normalizer tailored for Persian (Farsi) and Arabic script compatibility.
 * Replaces common variant codepoints (Yeh, Kaf, digits, diacritics, ZWNJ).
 */
object PersianTextNormalizer {
    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return input
            .trim()
            .lowercase()
            // Normalize Arabic Yeh (ي \u064A) to Persian Yeh (ی \u06CC)
            .replace('\u064A', '\u06CC')
            // Normalize Arabic Kaf (ك \u0643) to Persian Kehe (ک \u06A9)
            .replace('\u0643', '\u06A9')
            // Normalize Eastern Arabic-Indic digits to ASCII
            .map { ch ->
                when (ch) {
                    in '\u0660'..'\u0669' -> '0' + (ch - '\u0660')
                    in '\u06F0'..'\u06F9' -> '0' + (ch - '\u06F0')
                    '\u200C' -> ' ' // Replace ZWNJ with space for token matching
                    else -> ch
                }
            }
            .joinToString("")
            // Remove Arabic diacritics (Fatha, Damma, Kasra, Shadda, etc.)
            .replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
            .replace(Regex("\\s+"), " ")
    }
}

/**
 * Production offline indexed search service.
 * Supports cross-domain queries over charging stations and vehicles with RTL/Persian normalization.
 */
class OfflineSearchService(
    private val chargingStationRepository: ChargingStationRepository,
    private val vehicleRepository: VehicleRepository
) {

    suspend fun search(
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<OfflineSearchResult> {
        val normalizedQuery = PersianTextNormalizer.normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        val results = mutableListOf<OfflineSearchResult>()

        // 1. Search charging stations
        currentCoroutineContext().ensureActive()
        val stations = chargingStationRepository.getAllStations().first()
        for (station in stations) {
            val normName = PersianTextNormalizer.normalize(station.canonicalName)
            val normAddress = PersianTextNormalizer.normalize(station.address)
            val normOperator = PersianTextNormalizer.normalize(station.operatorName ?: "")

            val score = calculateMatchScore(normalizedQuery, normName, normAddress, normOperator)
            if (score > 0.0) {
                results.add(
                    OfflineSearchResult.ChargingStationItem(
                        id = station.id,
                        name = station.canonicalName,
                        address = station.address,
                        operator = station.operatorName,
                        score = score
                    )
                )
            }
        }

        // 2. Search vehicles
        currentCoroutineContext().ensureActive()
        val vehicles = vehicleRepository.getAllVehicles()
        for (veh in vehicles) {
            val normName = PersianTextNormalizer.normalize(veh.name)
            val normMake = PersianTextNormalizer.normalize(veh.id.manufacturer)
            val normModel = PersianTextNormalizer.normalize(veh.id.model)

            val score = calculateMatchScore(normalizedQuery, normName, normMake, normModel)
            if (score > 0.0) {
                results.add(
                    OfflineSearchResult.VehicleItem(
                        id = veh.id.id,
                        name = veh.name,
                        manufacturer = veh.id.manufacturer,
                        model = veh.id.model,
                        modelYear = veh.id.modelYear,
                        score = score
                    )
                )
            }
        }

        // Sort deterministically by rank score, then take requested slice
        return results
            .sorted()
            .drop(offset)
            .take(limit)
    }

    private fun calculateMatchScore(query: String, primary: String, vararg secondaries: String?): Double {
        if (primary == query) return 1.0
        if (primary.startsWith(query)) return 0.8
        if (primary.contains(query)) return 0.6

        for (sec in secondaries) {
            if (sec != null && sec.isNotEmpty()) {
                if (sec == query) return 0.7
                if (sec.startsWith(query)) return 0.5
                if (sec.contains(query)) return 0.4
            }
        }

        return 0.0
    }
}
