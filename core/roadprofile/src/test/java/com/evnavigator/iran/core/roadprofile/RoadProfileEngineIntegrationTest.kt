package com.evnavigator.iran.core.roadprofile

import com.evnavigator.iran.core.domain.GeoCoordinate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class RoadProfileEngineIntegrationTest {
    
    private val roadProfileEngine = RoadProfileEngineImpl()
    
    @Test
    fun testAnalyzeRoadProfile() = runBlocking {
        // Test with a simple route
        val routePoints = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(35.7000, 51.4000), // Nearby
            GeoCoordinate(35.7100, 51.4100)  // Further
        )
        
        val profileData = roadProfileEngine.analyzeRoadProfile(routePoints).first()
        
        assertNotNull(profileData)
        assertNotNull(profileData.coordinate)
        assertNotNull(profileData.roadType)
        assertNotNull(profileData.surfaceType)
        assertTrue(profileData.speedLimitKmh > 0)
        assertNotNull(profileData.gradientPercent)
    }
    
    @Test
    fun testGetRoadTypesAlongRoute() = runBlocking {
        val routePoints = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(37.0662, 49.4931)  // Isfahan
        )
        
        val roadTypes = roadProfileEngine.getRoadTypesAlongRoute(routePoints).first()
        
        assertNotNull(roadTypes)
        assertNotNull(roadTypes.coordinate)
        assertNotNull(roadTypes.roadType)
        assertNotNull(roadTypes.roadName)
    }
    
    @Test
    fun testGetRoadSurfaceDataInArea() = runBlocking {
        val boundingBox = BoundingBox(
            north = 36.0,
            south = 35.0,
            east = 52.0,
            west = 51.0
        )
        
        val surfaceData = roadProfileEngine.getRoadSurfaceDataInArea(boundingBox).first()
        
        assertNotNull(surfaceData)
        assertNotNull(surfaceData.coordinate)
        assertNotNull(surfaceData.surfaceType)
        assertTrue(surfaceData.qualityIndex >= 0.0 && surfaceData.qualityIndex <= 1.0)
    }
}