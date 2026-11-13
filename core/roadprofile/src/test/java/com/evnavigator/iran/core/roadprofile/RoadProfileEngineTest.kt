package com.evnavigator.iran.core.roadprofile

import com.evnavigator.iran.core.domain.GeoCoordinate
import com.evnavigator.iran.core.domain.ElevationPoint
import com.evnavigator.iran.core.elevation.ElevationEngineImpl
import com.evnavigator.iran.core.elevation.ElevationProvider
import com.evnavigator.iran.core.elevation.ElevationProviderResult
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

class RoadProfileEngineTest {
    
    private val testProvider = object : ElevationProvider {
        override fun canHandle(coordinate: GeoCoordinate): Boolean = true
        override fun getName(): String = "RoadProfileTestProvider"
        override suspend fun getElevationAt(coordinate: GeoCoordinate): ElevationProviderResult {
            val elev = if (coordinate.latitude in 35.0..36.0 && coordinate.longitude in 51.0..52.0) 1200.0 else 100.0
            val pt = ElevationPoint.known(coordinate, elev, 5.0)
            return ElevationProviderResult.Success(listOf(pt))
        }
        override suspend fun getElevationBatch(coordinates: List<GeoCoordinate>): ElevationProviderResult {
            val pts = coordinates.map { coord ->
                val elev = if (coord.latitude in 35.0..36.0 && coord.longitude in 51.0..52.0) 1200.0 else 100.0
                ElevationPoint.known(coord, elev, 5.0)
            }
            return ElevationProviderResult.Success(pts)
        }
        override suspend fun getElevationProfile(coordinates: List<GeoCoordinate>): ElevationProviderResult = getElevationBatch(coordinates)
    }

    private val roadProfileEngine = RoadProfileEngineImpl(ElevationEngineImpl(testProvider))
    
    @Test
    fun testGetRoadInfoAt() = runBlocking {
        // Test with Tehran coordinates (1200m elevation -> highway)
        val tehranPoint = GeoCoordinate(35.6892, 51.3890) 
        val roadInfo = roadProfileEngine.getRoadInfoAt(tehranPoint)
        
        assertNotNull(roadInfo)
        assertEquals(RoadType.HIGHWAY, roadInfo?.roadType)
        assertEquals(SurfaceType.ASPHALT, roadInfo?.surfaceType)
        assertEquals(120, roadInfo?.speedLimitKmh)
        assertTrue(roadInfo?.isTollRoad == true)
    }
    
    @Test
    fun testCalculateAverageRoadGrade() = runBlocking {
        val points = listOf(
            GeoCoordinate(35.6892, 51.3890), // Tehran
            GeoCoordinate(35.7000, 51.4000), // Nearby
            GeoCoordinate(35.7100, 51.4100)  // Further
        )
        
        val grade = roadProfileEngine.calculateAverageRoadGrade(points)
        assertNotNull(grade)
        assertTrue(grade >= -10.0 && grade <= 10.0) // Reasonable range
    }
    
    @Test
    fun testCalculateSegmentRoadCharacteristics() = runBlocking {
        val start = GeoCoordinate(35.6892, 51.3890)
        val end = GeoCoordinate(35.7000, 51.4000)
        
        val characteristics = roadProfileEngine.calculateSegmentRoadCharacteristics(start, end)
        
        assertNotNull(characteristics)
        assertEquals(start, characteristics.startCoordinate)
        assertEquals(end, characteristics.endCoordinate)
        assertTrue(characteristics.lengthMeters > 0)
        assertNotNull(characteristics.averageGradientPercent)
    }
}
