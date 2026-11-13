package com.evnavigator.iran.di

import com.evnavigator.iran.core.routing.engine.DefaultDomainRoutingEngine
import com.evnavigator.iran.core.routing.engine.IranRealRoadRoutingEngine
import com.evnavigator.iran.core.routing.importer.OsmDatasetManager
import com.evnavigator.iran.core.routing.provider.CompositeRoutingProvider
import com.evnavigator.iran.core.routing.provider.OfflineRoadGraphRoutingProvider
import com.evnavigator.iran.core.routing.provider.OsrmRoutingProvider
import com.evnavigator.iran.domain.intelligence.charging.RiskAwareChargingEvaluator
import com.evnavigator.iran.domain.intelligence.config.IntelligenceConfigurationManager
import com.evnavigator.iran.domain.intelligence.energy.CanonicalEnergyEngine
import com.evnavigator.iran.domain.intelligence.energy.PredictiveEnergyOrchestrator
import com.evnavigator.iran.domain.intelligence.routing.IntelligentRouteScorer
import com.evnavigator.iran.domain.intelligence.trip.CanonicalTravelTimeEngine
import com.evnavigator.iran.domain.intelligence.trip.PredictiveChargingOrchestrator
import com.evnavigator.iran.domain.intelligence.trip.RouteSourceClassification
import com.evnavigator.iran.domain.intelligence.trip.TripEnergyAndFeasibilityOrchestrator
import com.evnavigator.iran.domain.intelligence.trip.TripIntelligenceOrchestrator
import com.evnavigator.iran.domain.intelligence.uncertainty.ConfidenceAndUncertaintyEngine
import com.evnavigator.iran.domain.journey.multistop.DefaultRoadRouteProvider
import com.evnavigator.iran.domain.journey.multistop.MultiStopRouteCoordinator
import com.evnavigator.iran.domain.journey.multistop.RoadRouteProvider
import com.evnavigator.iran.domain.model.routing.RoutingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Singleton
    fun provideElevationEngine(): com.evnavigator.iran.core.elevation.ElevationEngine {
        return com.evnavigator.iran.core.elevation.ElevationEngineImpl()
    }

    @Provides
    @Singleton
    fun provideRoadProfileEngine(
        elevationEngine: com.evnavigator.iran.core.elevation.ElevationEngine
    ): com.evnavigator.iran.core.roadprofile.RoadProfileEngine {
        return com.evnavigator.iran.core.roadprofile.RoadProfileEngineImpl(elevationEngine)
    }

    @Provides
    @Singleton
    fun provideEnergyEngine(
        elevationEngine: com.evnavigator.iran.core.elevation.ElevationEngine,
        roadProfileEngine: com.evnavigator.iran.core.roadprofile.RoadProfileEngine
    ): com.evnavigator.iran.core.energy.EnergyEngine {
        return com.evnavigator.iran.core.energy.EnergyEngineImpl(elevationEngine, roadProfileEngine)
    }

    @Provides
    @Singleton
    fun provideIranRealRoadRoutingEngine(): IranRealRoadRoutingEngine {
        return IranRealRoadRoutingEngine()
    }

    @Provides
    @Singleton
    fun provideDomainRoutingEngine(
        routingEngine: IranRealRoadRoutingEngine
    ): RoutingEngine {
        val offlineProvider = OfflineRoadGraphRoutingProvider(routingEngine)
        val compositeProvider = CompositeRoutingProvider(
            providers = listOf(OsrmRoutingProvider(), offlineProvider)
        )
        return DefaultDomainRoutingEngine(compositeProvider)
    }

    @Provides
    @Singleton
    fun provideRoadRouteProvider(
        domainRoutingEngine: RoutingEngine
    ): RoadRouteProvider {
        return DefaultRoadRouteProvider(
            routingEngine = domainRoutingEngine,
            sourceClassifier = {
                if (OsmDatasetManager.isUsingImportedOsmDataset()) {
                    RouteSourceClassification.FULL_OFFLINE_OSM
                } else {
                    RouteSourceClassification.LIMITED_CORRIDOR_FALLBACK
                }
            }
        )
    }

    @Provides
    @Singleton
    fun provideMultiStopRouteCoordinator(
        roadRouteProvider: RoadRouteProvider
    ): MultiStopRouteCoordinator {
        return MultiStopRouteCoordinator(
            roadRouteProvider = roadRouteProvider
        )
    }

    @Provides
    @Singleton
    fun providePredictiveEnergyOrchestrator(): PredictiveEnergyOrchestrator {
        return PredictiveEnergyOrchestrator()
    }

    @Provides
    @Singleton
    fun provideConfidenceAndUncertaintyEngine(): ConfidenceAndUncertaintyEngine {
        return ConfidenceAndUncertaintyEngine()
    }

    @Provides
    @Singleton
    fun provideIntelligentRouteScorer(): IntelligentRouteScorer {
        return IntelligentRouteScorer()
    }

    @Provides
    @Singleton
    fun provideRiskAwareChargingEvaluator(): RiskAwareChargingEvaluator {
        return RiskAwareChargingEvaluator()
    }

    @Provides
    @Singleton
    fun provideIntelligenceConfigurationManager(): IntelligenceConfigurationManager {
        return IntelligenceConfigurationManager()
    }

    @Provides
    @Singleton
    fun provideTripEnergyAndFeasibilityOrchestrator(
        predictiveEnergyOrchestrator: PredictiveEnergyOrchestrator,
        intelligentRouteScorer: IntelligentRouteScorer,
        confidenceAndUncertaintyEngine: ConfidenceAndUncertaintyEngine
    ): TripEnergyAndFeasibilityOrchestrator {
        return TripEnergyAndFeasibilityOrchestrator(
            predictiveEnergyOrchestrator,
            intelligentRouteScorer,
            confidenceAndUncertaintyEngine
        )
    }

    @Provides
    @Singleton
    fun providePredictiveChargingOrchestrator(
        vehicleCatalogRepository: com.evnavigator.iran.domain.repository.VehicleCatalogRepository,
        chargingStationRepository: com.evnavigator.iran.domain.repository.ChargingStationCatalogRepository,
        predictiveEnergyOrchestrator: PredictiveEnergyOrchestrator,
        roadRouteProvider: RoadRouteProvider
    ): PredictiveChargingOrchestrator {
        return PredictiveChargingOrchestrator(
            vehicleCatalogRepository = vehicleCatalogRepository,
            chargingStationRepository = chargingStationRepository,
            predictiveEnergyOrchestrator = predictiveEnergyOrchestrator,
            roadRouteProvider = roadRouteProvider
        )
    }

    @Provides
    @Singleton
    fun provideTripIntelligenceOrchestrator(
        energyAndFeasibilityOrchestrator: TripEnergyAndFeasibilityOrchestrator,
        predictiveChargingOrchestrator: PredictiveChargingOrchestrator
    ): TripIntelligenceOrchestrator {
        return TripIntelligenceOrchestrator(
            energyAndFeasibilityOrchestrator,
            predictiveChargingOrchestrator
        )
    }

    @Provides
    @Singleton
    fun provideCanonicalEnergyEngine(): CanonicalEnergyEngine {
        return CanonicalEnergyEngine
    }

    @Provides
    @Singleton
    fun provideCanonicalTravelTimeEngine(): CanonicalTravelTimeEngine {
        return CanonicalTravelTimeEngine
    }
}
