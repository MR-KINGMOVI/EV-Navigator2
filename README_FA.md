# مسیریاب هوشمند خودروهای برقی ایران — نسخه V13.2.0 (`versionCode = 132`)

**وضعیت دروازه انتشار (Release Gate):** **`BLOCKED / NOT VERIFIED`**
*(ممیزی استاتیک و صحت منشأ داده‌ها: `PASS`؛ اجرای کامپایل و تست Gradle در محیط ایزوله بدون شبکه و بدون Android SDK: `BLOCKED / NOT VERIFIED`)*

## خلاصه اصلاحات کلیدی نسخه V13.2.0
- **حذف کامل اطلاعات ساختگی OSRM (`OsrmOnlineRouteClient.kt`):** حذف مسافت و زمان ساختگی (`polyline.size * 50.0` و `polyline.size * 2.0`)، حذف سرعت پیش‌فرض `100 km/h` و حذف تولید مانور جعلی در نبود `steps` (خروجی `INSUFFICIENT_ROUTE_DETAILS`).
- **حذف کامل fallbackهای پنهان سرعت `90.0` و دمای `22.0`:** ثبت صریح `MODEL_ASSUMPTION`، کاهش ضریب اطمینان و محاسبه بازه عدم‌قطعیت انرژی (`energyUncertaintyRangeWh`).
- **اصلاح سیستم اعتبارسنجی ایستگاه‌ها و تعرفه‌ها (`IranStationsCatalog.kt`):** حذف ثابت سراسری `LAST_OPERATOR_RELEASE_VERIFIED_AT_EPOCH_MS = 1762819200000L`، ثبت منابع و زمان‌های مستقل، انتقال خودکار داده‌های قدیمی به وضعیت `STALE` (`0` ایستگاه `VERIFIED` تازه از مجموع `195` ایستگاه)، ثبت `LINK_UNCHECKED` برای لینک‌ها و استانداردسازی واحد پول (`IRR_RIAL` و `IRT_TOMAN`) با `CentralCurrencyConverter`.
- **اسکریپت بررسی استاتیک داخل پروژه:** قابل اجرا با دستور `node scripts/v13_2_static_sanity_lint.mjs`.
