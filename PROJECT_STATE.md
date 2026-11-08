# PROJECT_STATE.md

_آخر تحديث: 2026-11-08_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`)
- **الهدف:** تطبيق POS لمحل بقالة/سوبر ماركت يعمل Offline بالكامل: كاشير، مخزون، مشتريات، مرتجعات، مصروفات، جرد، تقارير، ورديات، مستخدمون، نسخ احتياطي.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، فرع `main`)

## التقنية
Kotlin، Jetpack Compose + Material 3، Room 2.6.1، CameraX 1.4.0 + ML Kit Barcode، MVVM، Coroutines/Flow، DataStore، WorkManager، AGP 8.7.3، Kotlin 2.0.21، KSP، minSdk 24 / targetSdk 35.

## المرحلة الحالية
المرحلة 1 (الهوية + قاعدة البيانات + المنتجات + الباركود + الكاشير + حفظ الفواتير) - **قيد التنفيذ**.

## آخر مهمة نُفّذت
المهمة 2: مخطط Room كامل + DAOs + `Money` + `MoneyTest`.

## آخر نقطة توقف
انتهت كتابة طبقة البيانات (الجداول وDAOs) ورُفعت. **لم تبدأ** طبقة المنطق (`SaleService`). لا توجد مهمة نصف مكتملة.

## الملفات
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Manifest/Resources: `AndroidManifest.xml`, `res/values/{strings,themes}.xml`, `res/values-ar/strings.xml`
- UI: `MainActivity.kt`, `KonozApp.kt`, `ui/KonozRoot.kt` (شريط تنقل: كاشير/رئيسية/منتجات/المزيد، المحتوى placeholder)، `ui/theme/{Color,Type,Theme}.kt`
- Core: `core/Money.kt`
- Data: `data/KonozDatabase.kt`، `data/entity/{Enums,CatalogEntities,StockAndSalesEntities,PurchaseExpenseCountEntities}.kt`، `data/dao/{CatalogDaos,StockSalesDaos,OperationsDaos}.kt`
- Tests: `app/src/test/.../core/MoneyTest.kt`
- Docs: `README.md`, `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md`

## وظائف مكتملة (كتابةً فقط - لم تُبنَ ولم تُختبر بعد)
- هيكل المشروع، Design Tokens (Light/Dark)، نصوص AR/EN.
- 20 جدول Room بمفاتيح أجنبية وفهارس + DAOs لكل الجداول.
- حسابات مالية أساسية (`Money`).

## وظائف غير مكتملة
- `SaleService` (حفظ فاتورة داخل Transaction، خصم FEFO، دفع مختلط، تعليق/استرجاع) - **المهمة التالية**.
- بيانات أولية (مستخدم مدير، تصنيفات، إعدادات) وربط `KonozDatabase` في `KonozApp`.
- شاشة المنتجات، الكاشير + الكاميرا.
- المخزون/المشتريات/المرتجعات (منطق)، المصروفات، الجرد، التقارير، الورديات، المستخدمون والصلاحيات، الطباعة والمشاركة، النسخ الاحتياطي.
- خط Cairo (الملفات غير مضمّنة؛ `AppFontFamily` حاليًا `FontFamily.Default`).
- تبويبات التطبيق الحالية placeholder.

## الأخطاء الحالية / مخاطر
- **لم يُبنَ المشروع قط** (بيئة Claude بلا Android SDK). قد توجد أخطاء Sync/Compile. أي خطأ يرسله المستخدم يُصلَح أولًا.
- `gradle-wrapper.jar` و`gradlew` غير موجودين (يولّدهما Android Studio).
- workflow لبناء APK على GitHub Actions **لم يُرفع**: رفض 403 على `.github/workflows` (ربط GitHub بلا صلاحية Workflows). يمكن للمستخدم إضافته يدويًا.

## الاختبارات
- `MoneyTest`: مكتوب، **لم يُشغَّل**.
- لا توجد اختبارات أخرى بعد.

## المهمة التالية مباشرة (المهمة 3)
1. `data/seed/` أو `DatabaseInitializer`: إنشاء مستخدم مدير افتراضي (PIN مُهشَّر)، تصنيفات أساسية، إعدادات (عملة EGP، ضريبة معطلة)، وربطها في `KonozApp`.
2. `domain/SaleService`: `holdInvoice`, `resumeInvoice`, `completeSale` داخل `db.tx { }`.
3. اختبارات (instrumented أو Robolectric مع Room in-memory): FEFO، الدفع المختلط، عدم تكرار الترحيل، حفظ التكلفة وقت البيع.

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش (1 EGP = 100). الكميات `Long` بالألف (1 قطعة = 1000). لا Double. الحسابات ذات الكسور عبر BigDecimal + HALF_UP في `Money`.
- الضريبة والنسب بـ basis points (14% = 1400).
- المخزون يعتمد على `inventory_movements` (دفتر append-only، الكمية موقّعة). `products.currentQtyMilli` كاش يتغير فقط داخل نفس الـ Transaction.
- `inventory_movements.idempotencyKey` فريد (insert بـ IGNORE يرجع -1 عند التكرار) لمنع تكرار الترحيل.
- رقم فاتورة البيع/الشراء `number` يُمنح عند الإتمام فقط (الفواتير المعلقة null، بدون فجوات). `uuid` فريد لمنع التكرار عند الاستعادة.
- سعر التكلفة وقت البيع محفوظ في `sales_items.unitCostAtSale` و`sales_invoices.totalCost` ولا يُعاد حسابه.
- البيع بنظام FEFO من `stock_batches`.
- لا `fallbackToDestructiveMigration` أبدًا؛ أي تغيير مخطط = رفع `version` + `Migration`.
- حذف المنتج منطقي (`isActive`) لأن بنود الفواتير تشير إليه.
- المنتجات: أكثر من باركود (`barcodes.unitsPerScan` لباركود الكرتونة).
- الكاشير أول تبويب في التنقل. الألوان من `Brand`/`MaterialTheme` فقط.
