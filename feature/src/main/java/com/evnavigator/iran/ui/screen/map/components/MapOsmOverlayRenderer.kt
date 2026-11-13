package com.evnavigator.iran.ui.screen.map.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evnavigator.iran.designsystem.tokens.ColorTokens
import com.evnavigator.iran.domain.model.GeoCoordinate
import com.evnavigator.iran.domain.model.catalog.DomainChargingStationWithPorts
import com.evnavigator.iran.domain.routing.importer.ImportedRoutePreset
import com.evnavigator.iran.domain.routing.importer.RouteFileImporter
import com.evnavigator.iran.ui.screen.map.MapNavigationUiState
import com.evnavigator.iran.ui.screen.map.location.GpsLocationState
import com.evnavigator.iran.ui.screen.map.model.RouteCalculationState
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

/**
 * Extracted Osmdroid overlay renderer and GPX/KML corridor dialog for [com.evnavigator.iran.ui.screen.MapScreen].
 *
 * V12.5 Data-Trust Guarantees:
 * - Never substitutes an unverified `50.0 kW` power rating when station port power is null/unknown.
 * - Uses authentic port power (`it.powerKw`) or explicit `نامشخص` label when power is unverified.
 */
object MapOsmOverlayRenderer {

    fun updateMapOverlays(
        context: Context,
        mapView: MapView,
        uiState: MapNavigationUiState,
        gpsState: GpsLocationState,
        currentMapTheme: String,
        isDark: Boolean,
        showRangeIsochrone: Boolean,
        filterUltraFastOnly: Boolean,
        onDismissMenusOrSelection: () -> Boolean,
        onSelectCoordinate: (GeoCoordinate) -> Unit,
        onSelectStation: (DomainChargingStationWithPorts) -> Unit
    ) {
        val targetSource = when (currentMapTheme) {
            "DARK_OBSIDIAN" -> CockpitMapTileSource.DarkMatter
            "GOOGLE_ROAD" -> CockpitMapTileSource.GoogleRoadmap
            "SATELLITE" -> CockpitMapTileSource.GoogleSatellite
            "TERRAIN" -> CockpitMapTileSource.GoogleTerrain
            else -> CockpitMapTileSource.DarkMatter
        }
        if (mapView.tileProvider.tileSource != targetSource) {
            mapView.setTileSource(targetSource)
        }

        mapView.overlayManager.tilesOverlay.setColorFilter(
            TrueDarkTileFilter.createForSource(targetSource, isDark)
        )

        mapView.overlays.clear()
        mapView.overlays.add(IranOfflineVectorOverlay())
        mapView.overlays.add(CopyrightOverlay(mapView.context))

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (onDismissMenusOrSelection()) return true
                p?.let { onSelectCoordinate(GeoCoordinate(it.latitude, it.longitude)) }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { onSelectCoordinate(GeoCoordinate(it.latitude, it.longitude)) }
                return true
            }
        }
        mapView.overlays.add(MapEventsOverlay(mapEventsReceiver))

        renderUserGpsAndIsochrone(
            context = context,
            mapView = mapView,
            uiState = uiState,
            gpsState = gpsState,
            showRangeIsochrone = showRangeIsochrone
        )

        if (uiState.isChargingLayerVisible) {
            renderChargingStationsLayer(
                context = context,
                mapView = mapView,
                uiState = uiState,
                filterUltraFastOnly = filterUltraFastOnly,
                onSelectStation = onSelectStation
            )
        }

        val currentRoute = uiState.routeState
        if (currentRoute is RouteCalculationState.Success) {
            renderActiveRouteOverlay(context, mapView, currentRoute)
        }

        mapView.invalidate()
    }

    private fun renderUserGpsAndIsochrone(
        context: Context,
        mapView: MapView,
        uiState: MapNavigationUiState,
        gpsState: GpsLocationState,
        showRangeIsochrone: Boolean
    ) {
        val userPoint: GeoPoint?
        val accuracyMeters: Float
        val bearing: Float?
        val speedKmh: Float
        val isDeadReckoning: Boolean

        when (gpsState) {
            is GpsLocationState.FixAcquired -> {
                userPoint = GeoPoint(gpsState.location.latitude, gpsState.location.longitude)
                accuracyMeters = gpsState.accuracyMeters
                bearing = gpsState.bearingDegrees
                speedKmh = gpsState.speedKmh ?: 0f
                isDeadReckoning = false
            }
            is GpsLocationState.MapMatchedFix -> {
                userPoint = GeoPoint(gpsState.snappedLocation.latitude, gpsState.snappedLocation.longitude)
                accuracyMeters = 8f
                bearing = gpsState.bearingDegrees
                speedKmh = gpsState.speedKmh
                isDeadReckoning = false
            }
            is GpsLocationState.DeadReckoning -> {
                userPoint = GeoPoint(gpsState.estimatedLocation.latitude, gpsState.estimatedLocation.longitude)
                accuracyMeters = 25f
                bearing = gpsState.estimatedBearing
                speedKmh = gpsState.estimatedSpeedKmh
                isDeadReckoning = true
            }
            else -> {
                userPoint = null
                accuracyMeters = 0f
                bearing = 0f
                speedKmh = 0f
                isDeadReckoning = false
            }
        }

        if (userPoint != null) {
            if (accuracyMeters in 5f..250f) {
                val circlePoints = Polygon.pointsAsCircle(userPoint, accuracyMeters.toDouble())
                val accuracyPolygon = Polygon(mapView).apply {
                    points = circlePoints
                    fillPaint.color = if (isDeadReckoning) 0x22FFAB00.toInt() else 0x1A00E5FF.toInt()
                    outlinePaint.color = if (isDeadReckoning) 0x77FFAB00.toInt() else 0x5500E5FF.toInt()
                    outlinePaint.strokeWidth = 2f
                }
                mapView.overlays.add(accuracyPolygon)
            }

            val userMarker = Marker(mapView).apply {
                position = userPoint
                title = "موقعیت خودروی برقی"
                snippet = uiState.vehicleModelName
                icon = AutomotiveMapPuck.createVehiclePuck(context, isDeadReckoning, speedKmh.toDouble())
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(userMarker)

            if (showRangeIsochrone && uiState.vehicleBatteryKwh > 0.0 && uiState.vehicleConsumptionWhPerKm > 0.0) {
                val maxRangeMeters = uiState.estimatedRangeKm.toDouble() * 1000.0
                val safeRoundTripMeters = maxRangeMeters * 0.48
                if (maxRangeMeters > 5000.0) {
                    val outerRing = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(userPoint, maxRangeMeters)
                        fillPaint.color = 0x0A00E5FF.toInt()
                        outlinePaint.color = 0x6600E5FF.toInt()
                        outlinePaint.strokeWidth = 3f
                    }
                    val innerSafeRing = Polygon(mapView).apply {
                        points = Polygon.pointsAsCircle(userPoint, safeRoundTripMeters)
                        fillPaint.color = 0x1200E676.toInt()
                        outlinePaint.color = 0x8800E676.toInt()
                        outlinePaint.strokeWidth = 2.5f
                    }
                    mapView.overlays.add(outerRing)
                    mapView.overlays.add(innerSafeRing)
                }
            }

            if (uiState.isHeadingUp && bearing != null) {
                mapView.mapOrientation = -bearing
            } else {
                mapView.mapOrientation = 0f
            }
        }
    }

    private fun renderChargingStationsLayer(
        context: Context,
        mapView: MapView,
        uiState: MapNavigationUiState,
        filterUltraFastOnly: Boolean,
        onSelectStation: (DomainChargingStationWithPorts) -> Unit
    ) {
        val isCompactZoom = mapView.zoomLevelDouble < 9.5
        uiState.chargingStations.forEach { stationWithPorts ->
            val maxKw = stationWithPorts.ports.mapNotNull { it.powerKw }.filter { it > 0.0 }.maxOrNull() ?: 0.0
            if (!filterUltraFastOnly || maxKw >= 100.0) {
                val isSelected = uiState.selectedStation?.station?.id == stationWithPorts.station.id
                val powerLabel = if (maxKw > 0.0) "${maxKw.toInt()} kW" else "توان نامشخص"
                val stationMarker = Marker(mapView).apply {
                    position = GeoPoint(stationWithPorts.station.latitude, stationWithPorts.station.longitude)
                    title = stationWithPorts.station.canonicalName
                    snippet = "${stationWithPorts.station.city} • $powerLabel"
                    icon = ChargerPinFactory.createStationPin(context, maxKw, isSelected)
                    setAnchor(
                        Marker.ANCHOR_CENTER,
                        if (isCompactZoom && !isSelected) Marker.ANCHOR_CENTER else 0.90f
                    )
                    setOnMarkerClickListener { _, _ ->
                        onSelectStation(stationWithPorts)
                        mapView.controller.animateTo(position)
                        true
                    }
                }
                mapView.overlays.add(stationMarker)
            }
        }
    }

    private fun renderActiveRouteOverlay(
        context: Context,
        mapView: MapView,
        currentRoute: RouteCalculationState.Success
    ) {
        val geoPoints = currentRoute.polylinePoints.map { GeoPoint(it.latitude, it.longitude) }

        val outerAura = Polyline(mapView).apply {
            setPoints(geoPoints)
            outlinePaint.color = 0x2800E5FF
            outlinePaint.strokeWidth = 34f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        mapView.overlays.add(outerAura)

        val midGlow = Polyline(mapView).apply {
            setPoints(geoPoints)
            outlinePaint.color = 0x6600E5FF.toInt()
            outlinePaint.strokeWidth = 18f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        mapView.overlays.add(midGlow)

        val coreLine = Polyline(mapView).apply {
            setPoints(geoPoints)
            outlinePaint.color = if (currentRoute.isFeasible) 0xFF00E5FF.toInt() else 0xFFFFAB00.toInt()
            outlinePaint.strokeWidth = 8.5f
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        mapView.overlays.add(coreLine)

        currentRoute.plan.waypoints.forEach { wp ->
            if (wp.isChargingStop) {
                val stopMarker = Marker(mapView).apply {
                    position = GeoPoint(wp.coordinate.latitude, wp.coordinate.longitude)
                    title = "⚡ توقف شارژ: ${wp.name}"
                    snippet = "شارژ هدف تا ${(wp.targetDepartureSocPercent ?: 85.0).toInt()}%"
                    icon = ChargerPinFactory.createStationPin(
                        context = context,
                        maxPowerKw = wp.stationDetails?.maxPowerKw ?: 0.0,
                        isSelected = true
                    )
                    setAnchor(Marker.ANCHOR_CENTER, 0.90f)
                }
                mapView.overlays.add(stopMarker)
            }
        }

        val destCoord = currentRoute.polylinePoints.lastOrNull()
        if (destCoord != null) {
            val destMarker = Marker(mapView).apply {
                position = GeoPoint(destCoord.latitude, destCoord.longitude)
                title = currentRoute.destinationName
                snippet = "شارژ تخمینی ورود: ${currentRoute.arrivalSocPercent.roundToInt()}%"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(destMarker)
        }
    }
}

@Composable
fun MapImportCorridorDialog(
    onDismiss: () -> Unit,
    onSelectPreset: (ImportedRoutePreset) -> Unit
) {
    val presets = remember { RouteFileImporter.getPresetRoutes() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B1222),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = {
            Text(
                text = "وارد کردن کریدورهای ترانزیتی (GPX / KML)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = ColorTokens.NeonCyan
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "یک کریدور استاندارد بین‌شهری را برای بارگذاری روی نقشه شیشه‌ای انتخاب کنید:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
                for (preset in presets) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPreset(preset) },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF131E36),
                        border = BorderStroke(1.dp, ColorTokens.NeonCyan.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = preset.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${preset.distanceKm.toInt()} کیلومتر • ${preset.recommendedChargingStops.size} ایستگاه شارژ سریع",
                                style = MaterialTheme.typography.labelSmall,
                                color = ColorTokens.ElectricGreen
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن", color = ColorTokens.NeonCyan)
            }
        }
    )
}
