# PROJECT_STATE.md

_آخر تحديث: 2026-11-08_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`)
- **الهدف:** تطبيق POS لمحل بقالة/سوبر ماركت يعمل Offline بالكامل: كاشير، مخزون، مشتريات، مرتجعات، مصروفات، جرد، تقارير، ورديات، مستخدمون، نسخ احتياطي.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، فرع `main`)

## التقنية
Kotlin، Compose + Material 3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Coroutines/Flow، DataStore، WorkManager، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1 (اختبارات Room على JVM)، minSdk 24 / targetSdk 35.

## المرحلة الحالية
المرحلة 1 (الهوية + قاعدة البيانات + المنتجات + الباركود + الكاشير + حفظ الفواتير) - **قيد التنفيذ**.

## آخر مهمة نُفّذت
المهمة 3: منطق البيع (`SaleService`, `SaleCalculator`) + بيانات أولية (`DatabaseInitializer`) + `PinHasher` + ربطها في `KonozApp` + اختبارات.

## آخر نقطة توقف
انتهت طبقة منطق البيع ورُفعت (كتابة فقط، لم تُشغَّل). لا توجد مهمة نصف مكتملة. **لم تبدأ** شاشة المنتجات.

## الملفات
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Manifest/Resources: `AndroidManifest.xml`, `res/values/{strings,themes}.xml`, `res/values-ar/strings.xml`
- UI: `MainActivity.kt`, `KonozApp.kt` (ينشئ `database` و`saleService` ويستدعي seed)، `ui/KonozRoot.kt` (تنقل placeholder)، `ui/theme/{Color,Type,Theme}.kt`
- Core: `core/Money.kt`, `core/PinHasher.kt`
- Data: `data/KonozDatabase.kt`, `data/entity/{Enums,CatalogEntities,StockAndSalesEntities,PurchaseExpenseCountEntities}.kt`, `data/dao/{CatalogDaos,StockSalesDaos,OperationsDaos}.kt`, `data/seed/DatabaseInitializer.kt` (+ `SettingKeys`)
- Domain: `domain/{SaleModels,SaleCalculator,SaleService}.kt`
- Tests: `core/MoneyTest`, `domain/SaleCalculatorTest` (JUnit)، `domain/SaleServiceTest` (Robolectric + Room in-memory)
- Docs: `README.md`, `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md`

## وظائف مكتملة (كتابةً فقط - لم تُبنَ ولم تُختبر بعد)
- هيكل المشروع، Design Tokens، نصوص AR/EN، مخطط Room (20 جدول) + DAOs، `Money`.
- حساب الفاتورة (خصم بند، خصم فاتورة، ضريبة على المتبقي).
- حفظ الفاتورة داخل Transaction مع خصم FEFO، دفع مختلط، تعليق/استرجاع، منع تكرار الترحيل، تجميد التكلفة، منع بيع المنتهي بدون تأكيد، خيار المخزون السالب.
- Seed أول تشغيل: مدير (`admin` / PIN مؤقت 1234 + علم `must_change_admin_pin`)، 8 تصنيفات، إعدادات افتراضية.

## وظائف غير مكتملة
- شاشة تسجيل الدخول وإجبار تغيير PIN المدير (الحاليًا لا توجد أي واجهة للدخول).
- Repository/ViewModel للمنتجات وحفظ منتج+باركودات+دفعة افتتاحية (المهمة 4).
- شاشة المنتجات، شاشة الكاشير + CameraX/ML Kit.
- صلاحيات البيع (تعديل سعر، حد خصم الكاشير `cashier_max_discount_bp`) غير مطبقة بعد في `SaleService`.
- منطق المشتريات/المرتجعات/التسويات/الجرد، المصروفات، التقارير، الورديات، المستخدمون، الطباعة والمشاركة، النسخ الاحتياطي.
- خط Cairo (غير مضمّن؛ `AppFontFamily` = Default).
- تبويبات التطبيق placeholder.

## الأخطاء الحالية / مخاطر
- **لم يُبنَ المشروع قط** (بيئة Claude بلا Android SDK). قد توجد أخطاء Sync/Compile. أي خطأ يرسله المستخدم يُصلَح أولًا.
- Robolectric يحتاج إنترنت أول مرة لتنزيل android-all عند تشغيل `SaleServiceTest`.
- `gradle-wrapper.jar` و`gradlew` غير موجودين (يولّدهما Android Studio).
- workflow لبناء APK على GitHub Actions لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني إلى أن تضاف شاشة إجبار التغيير.

## الاختبارات (كلها مكتوبة و**لم تُشغَّل**)
- `MoneyTest` (7)، `SaleCalculatorTest` (5)، `SaleServiceTest` (11: FEFO، تجميد التكلفة، دفع مختلط، منع التكرار، ترقيم متسلسل، تعليق/استرجاع، دفع ناقص، دفع بطاقة زائد، منتهي الصلاحية، مخزون سالب، كمية كسرية لقطعة).

## المهمة التالية مباشرة (المهمة 4: إدارة المنتجات)
1. `data/repo/ProductRepository`: إضافة/تعديل منتج + باركودات (فريدة) + دفعة افتتاحية + حركة OPENING داخل Transaction، تحويل كرتونة←قطعة، تفعيل/إيقاف.
2. `ProductsViewModel` + `ProductsScreen` + `ProductEditScreen` (Compose, RTL) وربطها بتبويب المنتجات.
3. اختبارات Repository (باركود مكرر، دفعة افتتاحية تنشئ حركة ودفعة).

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش (1 EGP = 100). الكميات `Long` بالألف (1 قطعة = 1000). لا Double. الحسابات ذات الكسور عبر BigDecimal + HALF_UP في `Money`.
- الضريبة والنسب بـ basis points (14% = 1400). الضريبة على المبلغ بعد الخصومات.
- المخزون يعتمد على `inventory_movements` (append-only، كمية موقّعة). `products.currentQtyMilli` كاش يتغير فقط داخل نفس الـ Transaction. كل كمية داخلة تنشئ `StockBatch` (حتى بلا تاريخ صلاحية).
- `inventory_movements.idempotencyKey` فريد (IGNORE يرجع -1). مفتاح البيع: `SALE:{uuid}:{lineIndex}:{allocIndex}`.
- `SalesInvoice.uuid` يحدد تكرار المحاولة: إكمال نفس uuid يرجع نتيجة الأولى (`duplicate=true`).
- `number` يُمنح عند الإتمام فقط (المعلقة null، بدون فجوات).
- التكلفة وقت البيع محفوظة في `sales_items.unitCostAtSale` و`sales_invoices.totalCost` (متوسط مرجّح لدفعات FEFO) ولا تُعاد.
- المدفوعات تُخزَّن صافية (النقد مطروحًا منه الباقي) ليساوي SUM(payments) إجمالي الفاتورة؛ `paidTotal`/`changeDue` تُحفظان على الفاتورة. الدفع بغير النقد لا يتجاوز الإجمالي.
- المنتهي الصلاحية يُتجاهل في FEFO إلا بـ `allowExpired=true` (تأكيد الكاشير). المخزون السالب يتبع `allowNegativeStock`.
- لا `fallbackToDestructiveMigration` أبدًا؛ أي تغيير مخطط = رفع `version` + `Migration`.
- حذف المنتج منطقي (`isActive`)؛ المنتجات المبيعة لا تُحذف فعليًا.
- أكثر من باركود للمنتج (`barcodes.unitsPerScan` لباركود الكرتونة).
- الكاشير أول تبويب. الألوان من `Brand`/`MaterialTheme` فقط.
- PIN يُخزَّن مشفّرًا بـ PBKDF2 (`PinHasher`).
