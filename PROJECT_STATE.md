# PROJECT_STATE.md

_آخر تحديث: 2026-11-08_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`)
- **الهدف:** تطبيق POS لمحل بقالة/سوبر ماركت يعمل Offline بالكامل: كاشير، مخزون، مشتريات، مرتجعات، مصروفات، جرد، تقارير، ورديات، مستخدمون، نسخ احتياطي.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، فرع `main`)

## التقنية
Kotlin، Compose + Material 3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Coroutines/Flow، DataStore، WorkManager، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / targetSdk 35.

## المرحلة الحالية
المرحلة 1 (الهوية + قاعدة البيانات + المنتجات + الباركود + الكاشير + حفظ الفواتير) - **قيد التنفيذ** (الباقي: الكاشير + الكاميرا).

## آخر مهمة نُفّذت
المهمة 4: `ProductRepository` + شاشات إدارة المنتجات (قائمة + إضافة/تعديل) + اختبارات.

## آخر نقطة توقف
انتهت شاشة المنتجات ورُفعت (كتابة فقط، لم تُبنَ). لا توجد مهمة نصف مكتملة. **لم تبدأ** شاشة الكاشير.

## الملفات
- Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`
- Manifest/Resources: `AndroidManifest.xml`, `res/values/{strings,themes}.xml`, `res/values-ar/strings.xml` (نصوص المنتجات مضافة)
- App: `MainActivity.kt`, `KonozApp.kt` (`database`, `saleService`, `productRepository`, `activeUserId()` مؤقت = admin)
- UI: `ui/KonozRoot.kt` (تبويب المنتجات حقيقي، باقي التبويبات placeholder)، `ui/theme/{Color,Type,Theme}.kt`، `ui/common/Format.kt`، `ui/products/{ProductsRoute,ProductsScreen,ProductEditScreen,ProductsViewModel,ProductEditViewModel}.kt`
- Core: `core/{Money,Quantities,PinHasher}.kt`
- Data: `data/KonozDatabase.kt`, `data/entity/{Enums,CatalogEntities,StockAndSalesEntities,PurchaseExpenseCountEntities}.kt`, `data/dao/{CatalogDaos,StockSalesDaos,OperationsDaos}.kt`, `data/repo/ProductRepository.kt`, `data/seed/DatabaseInitializer.kt` (+`SettingKeys`)
- Domain: `domain/{SaleModels,SaleCalculator,SaleService}.kt`
- Tests: `core/{MoneyTest,QuantitiesTest}`, `domain/SaleCalculatorTest` (JUnit)؛ `domain/SaleServiceTest`, `data/repo/ProductRepositoryTest` (Robolectric + Room in-memory)
- Docs: `README.md`, `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md`

## وظائف مكتملة (كتابةً فقط - لم تُبنَ ولم تُختبر بعد)
- هيكل + Design Tokens + نصوص AR/EN + مخطط Room (20 جدول) + DAOs + `Money`.
- `SaleService`: حفظ فاتورة داخل Transaction، FEFO، دفع مختلط، تعليق/استرجاع، منع تكرار، تجميد التكلفة، منع بيع المنتهي بدون تأكيد، مخزون سالب اختياري.
- Seed أول تشغيل: `admin` / PIN مؤقت 1234، 8 تصنيفات، إعدادات.
- `ProductRepository`: إضافة/تعديل منتج مع باركودات متعددة (فريدة، الأول أساسي)، دفعة افتتاحية + حركة OPENING، تفعيل/إيقاف، مفضلة، تحديث أسعار بالجملة (نسبة مئوية لتصنيف/الكل) مع Audit، `lookupBarcode` (يرجع unitsPerScan).
- شاشات المنتجات: قائمة + بحث + مفضلة + تفعيل/إيقاف، نموذج إضافة/تعديل (تصنيف، وحدة، تكلفة/بيع/هامش لحظي، حد تنبيه، كرتونة، كمية افتتاحية بالقطع أو الكراتين، صلاحية، باركودات).

## وظائف غير مكتملة
- **شاشة الكاشير + CameraX/ML Kit** (المهمة التالية)، ومكوّن مسح يُستخدم أيضًا في نموذج المنتج (مسح باركود بالكاميرا؛ حاليًا الإدخال يدوي فقط).
- اختيار صورة المنتج (الحقل `imagePath` موجود بلا واجهة)، اختيار مورد في النموذج، إضافة تصنيف جديد من الواجهة (`addCategory` موجود في الـ Repository)، واجهة تحديث الأسعار بالجملة، استيراد/تصدير Excel/CSV، فلتر بالتصنيف في القائمة، تأكيد قبل الحذف/الإيقاف (الحذف النهائي غير متاح بقرار).
- شاشة تسجيل الدخول وإجبار تغيير PIN المدير (`activeUserId()` مؤقت).
- صلاحيات البيع (تعديل سعر، حد خصم الكاشير) في `SaleService`.
- منطق المشتريات/المرتجعات/التسويات/الجرد، المصروفات، التقارير، اللوحة الرئيسية، الورديات، المستخدمون، الطباعة والمشاركة، النسخ الاحتياطي.
- خط Cairo (غير مضمّن).

## الأخطاء الحالية / مخاطر
- **لم يُبنَ المشروع قط** (بيئة Claude بلا Android SDK). قد توجد أخطاء Sync/Compile (خاصة استخدامات Compose التجريبية مثل FlowRow/DatePicker). أي خطأ يرسله المستخدم يُصلَح أولًا.
- Robolectric يحتاج إنترنت أول مرة.
- `gradle-wrapper.jar`/`gradlew` غير موجودين (يولّدهما Android Studio).
- workflow لبناء APK لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني حتى تضاف شاشة إجبار التغيير.

## الاختبارات (كلها مكتوبة و**لم تُشغَّل**)
- `MoneyTest` (7)، `QuantitiesTest` (4)، `SaleCalculatorTest` (5)، `SaleServiceTest` (11)، `ProductRepositoryTest` (8: إنشاء مع دفعة+حركة، باركود كرتونة، باركود مكرر مع rollback، كود داخلي مكرر، قيم غير صحيحة، تعديل يُبقي المخزون، تحديث أسعار بالجملة، إيقاف المنتج).
- لا اختبارات UI.

## المهمة التالية مباشرة (المهمة 5: الكاشير + الباركود)
1. `ui/scanner/BarcodeScanner.kt`: مكوّن CameraX + ML Kit مع طلب صلاحية الكاميرا، تهدئة (debounce) لمنع قراءة مكررة.
2. `CashierViewModel`: سلة (مسح→`lookupBarcode`→إضافة/زيادة كمية، كمية يدوية، حذف، بحث، وزن، خصم، ضريبة من الإعدادات)، تعليق/استرجاع، دفع مختلط عبر `SaleService`، حفظ تلقائي للسلة (مسودة HELD) لمنع فقدانها عند إغلاق مفاجئ.
3. `CashierScreen` + رسالة "باركود غير مسجل" تفتح نموذج إضافة منتج بالباركود، تنبيه منتهي الصلاحية مع تأكيد.
4. اختبارات `CashierViewModel` (مسح مكرر يزيد الكمية، باركود كرتونة يضيف N قطعة).

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش (1 EGP = 100). الكميات `Long` بالألف (1 قطعة = 1000). لا Double. الحسابات ذات الكسور عبر BigDecimal + HALF_UP في `Money`. تحويل الكرتونة إلى قطع عبر `Quantities.cartonsToPieceMilli`.
- النسب بـ basis points (14% = 1400)، الضريبة على المبلغ بعد الخصومات.
- المخزون يعتمد على `inventory_movements` (append-only، كمية موقّعة). `products.currentQtyMilli` كاش يتغير فقط داخل نفس الـ Transaction (تعديل المنتج لا يلمسه). كل كمية داخلة تنشئ `StockBatch`.
- `idempotencyKey` فريد: البيع `SALE:{uuid}:{line}:{alloc}`، الافتتاحي `OPEN:{productId}`.
- `SalesInvoice.uuid` يحدد تكرار المحاولة (`duplicate=true`)، `number` يُمنح عند الإتمام فقط.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`) ولا تُعاد.
- المدفوعات تُخزَّن صافية (النقد مطروحًا منه الباقي)، وغير النقد لا يتجاوز الإجمالي.
- المنتهي الصلاحية يُتجاهل في FEFO إلا بـ `allowExpired=true`. المخزون السالب يتبع `allowNegativeStock`.
- تاريخ الصلاحية من الواجهة يُحفظ كنهاية اليوم (UTC midnight + 86_399_999) فيبقى المنتج صالحًا طوال يوم انتهائه.
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan = N` (وليس منتجًا منفصلًا).
- بحث الإدارة (`searchManage`) يشمل الموقوف، بحث الكاشير (`search`) للنشط فقط.
- لا `fallbackToDestructiveMigration` أبدًا؛ تغيير المخطط = رفع `version` + `Migration`.
- حذف المنتج منطقي (`isActive`) فقط.
- الكاشير أول تبويب. الألوان من `Brand`/`MaterialTheme` فقط. النصوص في `strings.xml` (AR/EN) لا مكتوبة داخل الشاشات.
- PIN يُخزَّن مشفّرًا بـ PBKDF2 (`PinHasher`).
