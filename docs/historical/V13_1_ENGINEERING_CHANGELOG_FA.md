# گزارش جامع تغییرات مهندسی — نسخه V13.1.0 (`versionCode = 131`)

## خلاصه اجرایی
این سند تمامی اصلاحات معماری، ریاضی، اصالت داده (Data Provenance) و اعتبارسنجی هندسی اعمال‌شده در ارتقای پروژه **EV Navigator Iran** از نسخه `13.0.0` به **`13.1.0` (`versionCode = 131`)** را بر اساس مشخصات `EV_Navigator_Iran_V13_1_MASTER_FIX_SPEC_FA` مستند می‌کند.

---

## ۱. همگرایی کامل موتورهای محاسباتی (Canonical Engine Convergence — بخش ۱ و ۹)
- **`CanonicalEnergyEngine.kt`:**
  - افزودن متدهای کانونیکال `ConnectorCompatibilityEngine.evaluatePortCompatibility(...)` و `evaluateDomainPort(...)` به عنوان مرجع واحد تطابق کانکتور و توان شارژ AC/DC.
  - افزودن `ChargeCurveEngine.simulateChargingSessionOrNull(...)` که در صورت نامشخص بودن توان مؤثر (`effectiveMaxPowerKw == null`) به جای عددسازی، `null` برمی‌گرداند.
  - افزودن `CanonicalEnergyEngine.predictRouteEnergy(...)` برای پوشش مستقیم پروفایل‌های سگمنت.
- **`CanonicalTravelTimeEngine.kt`:**
  - افزودن اعتبارسنجی سخت‌گیرانه در برابر مقادیر منفی، `NaN` یا `Infinite` (`isValid = false`, `validationErrorCode = "INVALID_..."`).
  - اتصال مستقیم `CanonicalTravelTimeEngine` به `TripEnergyAndFeasibilityOrchestrator.kt`، `PredictiveChargingOrchestrator.kt`، `MultiStopRouteCoordinator.kt` و ثبت در گراف تزریق وابستگی `IntelligenceModule.kt`.

## ۲. تطابق کانکتور و حذف کامل Fallbackهای جعلی توان شارژ (بخش ۲)
- **`CanonicalEnergyEngine.kt` و `StationPortEligibilityEvaluator.kt`:**
  - حذف کامل `?: standard.defaultMaxPowerKw` برای شارژ DC و `?: min(11.0, standard.defaultMaxPowerKw)` برای شارژ AC هنگامی که توان پذیرش خودرو (`vehicleDcMaxKw` یا `vehicleAcMaxKw`) نامشخص (`null`) است.
  - بازگشت قطعی `ConnectorCompatibilityStatus.UNKNOWN` (و هرگز `COMPATIBLE`) زمانی که لیست کانکتورهای خودرو خالی است یا رشته کانکتور خودرو/پورت ناشناخته است.
  - حفظ رشته خام کانکتور پورت (`rawPortConnector`) و کانکتورهای ناشناخته خودرو (`unrecognizedVehicleConnectors`) برای گزارش‌دهی شفاف.
- **`TripIntelligenceViewModel.kt`، `ActiveJourneyScreen.kt` و `StationIntelligenceSheet.kt`:**
  - حذف کامل `?: 60.0` برای توان بیشینه ایستگاه (`maxKw`).

## ۳. جداسازی ظرفیت ناخالص و قابل استفاده باتری و حذف کامل `60.0 kWh` (بخش ۳ و ۴)
- **`EnergyPredictionService.kt`:**
  - جداسازی صریح `usableBatteryKwh` و `nominalGrossBatteryKwh` در `VehiclePhysicsParams` و `DomainVehicle.toPhysicsParamsOrNull`.
  - حذف پیش‌فرض `usableBatteryKwh: Double = 60.0` و `?: 60.0` از `VehiclePhysicsParams.assumedReference` و `DefaultEnergyPredictionService.predict`.
  - بازگشت وضعیت صریح `dataQuality = "INSUFFICIENT_VEHICLE_DATA"`، `isFeasibleWithoutCharging = false` و `predictedArrivalSocPercent = Double.NaN` در صورت نامشخص یا نامعتبر بودن ظرفیت باتری (`<= 0`, `NaN`, `Infinite`).
  - در صورت وجود تنها `nominalGrossBatteryKwh`، تخمین ظرفیت قابل استفاده با ضریب `0.92` صرفاً با برچسب `ASSUMED_USABLE_FROM_GROSS_BATTERY` و `dataQuality = "ESTIMATED"` (بدون اجازه اعلام `isFeasibleWithoutCharging = true`).
- **`ConfidenceAndUncertaintyEngine.kt`:**
  - حذف کامل `val batteryCapKwh = context.batteryCapacityKwh.value ?: 60.0` و ثبت ریسک `MISSING_BATTERY_CAPACITY` همراه با سطح `QualitativeUncertainty.VERY_HIGH_UNCERTAINTY`.

## ۴. جداسازی سرعت مجاز قانونی از سرعت عملیاتی و اعتبارسنجی هندسه مسیر (بخش ۵ و ۶)
- **`domain/.../intelligence/route/RouteIntegrityValidator.kt` و `core/routing/.../validation/RouteIntegrityValidator.kt`:**
  - حذف کامل `speedLimitKmh = 90` و `100` از `RouteIntegrityValidator.kt`، `ValhallaRoutingProvider.kt`، `GraphHopperRoutingProvider.kt` و `OsrmRoutingProvider.kt`.
  - حفظ `speedLimitKmh = 0` (`null` / `InputOrigin.UNKNOWN`) در صورت فقدان سرعت مجاز قانونی در پاسخ مسیریاب، و تفکیک آن از سرعت عملیاتی (`DERIVED_FROM_ROUTE_TIME` یا `ESTIMATED_MODEL`).
  - بررسی انطباق نقطه آغاز (`first()`) با `origin` و نقطه پایان (`last()`) با `destination`، تشخیص مسیر معکوس (`ROUTE_GEOMETRY_REVERSED`)، بررسی نسبت طول هندسی به مسافت گزارش‌شده (`GEOMETRY_DISTANCE_MISMATCH`) و بررسی پیوستگی سگمنت‌ها (`DISCONTINUOUS_SEGMENTS`).

## ۵. اصالت داده کاتالوگ ایستگاه‌ها و ادغام ایستگاه‌های تکراری بدون حذف شواهد (بخش ۸)
- **`IranStationsCatalog.kt` و `CatalogDomainModels.kt`:**
  - ارتقای نسخه کاتالوگ به `CATALOG_DATASET_VERSION = "2026.4-V13.1"` (`CATALOG_SCHEMA_VERSION = "2.3"`).
  - حذف سنتز خودکار URL خارجی از روی URN داخلی در `addStation` و ثبت URL خارجی صریح برای ایستگاه‌های `VERIFIED`.
  - پیاده‌سازی `StationCatalogDeduplicator.deduplicateAndMergeDomainStations` و `IranStationsCatalog.deduplicateAndMergeStations` برای تشخیص ایستگاه‌های تکراری بر اساس `externalId`، نام نرمال‌شده و فاصله جغرافیایی با حفظ کامل `SourceEvidence` و پورت‌ها.
