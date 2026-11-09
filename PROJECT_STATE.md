# PROJECT_STATE.md

_آخر تحديث: 2026-11-09_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل (كاشير، مخزون، مشتريات، مرتجعات، مصروفات، جرد، تقارير، ورديات، مستخدمون، نسخ احتياطي).
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، `main`)
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## المرحلة الحالية
المرحلة 1: كل بنودها **مكتوبة** (هوية، قاعدة بيانات، منتجات، باركود، كاشير، حفظ فواتير) لكن **لم تُبنَ ولم تُختبر أي منها**. أول خطوة مطلوبة: المستخدم يبني المشروع في Android Studio ويشغّل الاختبارات ويرسل الأخطاء.

## آخر مهمة نُفّذت / نقطة التوقف
المهمة 5: الكاشير + ماسح الباركود (كتابة فقط). لا توجد مهمة نصف مكتملة. **لم تبدأ** مهام المرحلة 2.

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة؛ نصوص: `res/values{,-ar}/strings.xml` (منتجات) و`strings_cashier.xml` (كاشير).
- `KonozApp.kt`: `database`, `saleService`, `productRepository`, `activeUserId()` (**مؤقت = admin**).
- `core/`: `Money`, `Quantities`, `PinHasher`.
- `data/`: `KonozDatabase`، `entity/*` (4 ملفات)، `dao/{CatalogDaos,StockSalesDaos,OperationsDaos}`، `repo/ProductRepository`، `seed/DatabaseInitializer` (+`SettingKeys`).
- `domain/`: `SaleModels`, `SaleCalculator`, `SaleService`, `Cart` (نقية)، `CashierSession` (+`CashierMessage`, `CheckoutOutcome`).
- `ui/`: `KonozRoot` (الكاشير والمنتجات حقيقيان، الرئيسية/المزيد placeholder)، `theme/*`، `common/Format`، `products/*`، `scanner/{CameraBarcodeScanner,ScanDebouncer}`، `cashier/{CashierViewModel,CashierScreen,CashierRoute}`.
- اختبارات: `core/{Money,Quantities}Test`، `domain/{SaleCalculator,Cart}Test`، `ui/scanner/ScanDebouncerTest` (JUnit)؛ `domain/{SaleService,CashierSession}Test`، `data/repo/ProductRepositoryTest` (Robolectric + Room in-memory).

## مكتمل كتابةً (لم يُبنَ/يُختبر)
- هيكل، هوية Light/Dark، AR/EN، Room (20 جدول)، `Money`.
- `SaleService`: Transaction، FEFO، دفع مختلط، تعليق/استرجاع، idempotency، تجميد التكلفة، منتهي الصلاحية، مخزون سالب اختياري.
- المنتجات: Repository + شاشات (قائمة/إضافة/تعديل)، باركودات متعددة، كرتونة←قطعة، تحديث أسعار بالجملة (منطق فقط).
- **الكاشير:** مسح بالكاميرا (CameraX+ML Kit مع debounce واهتزاز)، بحث يدوي، مفضلة، سلة (مسح مكرر يزيد الكمية، باركود كرتونة يضيف N، تعديل كمية/وزن، حذف)، خصم بند/فاتورة، ضريبة من الإعدادات، تعديل سعر (المدير فقط + Audit)، حد خصم الكاشير، دفع مختلط مع الباقي، تعليق/استرجاع/حذف المعلقة، **حفظ تلقائي للسلة واستعادتها بعد إغلاق مفاجئ**، تنبيه المنتهي الصلاحية مع تأكيد، باركود غير مسجل ← فتح نموذج منتج جديد بالباركود، نافذة نجاح برقم الفاتورة.
- Seed: `admin` / PIN مؤقت 1234، 8 تصنيفات، إعدادات افتراضية.

## غير مكتمل
- **المرحلة 1 الناقص جزئيًا:** مسح باركود داخل نموذج المنتج (الإدخال يدوي أو من الكاشير)، صورة المنتج، اختيار مورد، إضافة تصنيف من الواجهة، واجهة تحديث الأسعار بالجملة، استيراد/تصدير Excel/CSV، فلتر بالتصنيف، تأكيد قبل الإيقاف.
- الكاشير: لا طباعة ولا مشاركة فاتورة (المرحلة 4)، لا ربط بالوردية (يقرأ الوردية المفتوحة إن وُجدت فقط)، لا فتح للمنتجات المباعة بالوزن من ميزان خارجي، لا تعديل ضريبة من الواجهة (من الإعدادات فقط، ولا شاشة إعدادات بعد).
- شاشة تسجيل الدخول + إجبار تغيير PIN (حاليًا المستخدم = admin دائمًا).
- المرحلة 2: منطق المشتريات/الموردين/المرتجعات/التسويات، شاشاتها، تنبيهات المخزون والصلاحية.
- المرحلة 3: المصروفات، الجرد، التقارير، اللوحة الرئيسية.
- المرحلة 4: الورديات، المستخدمون/الصلاحيات، الطباعة/المشاركة، النسخ الاحتياطي.
- خط Cairo (غير مضمّن)، APK.

## مخاطر / أخطاء
- **لم يُبنَ أي شيء قط** (لا Android SDK في بيئة Claude). أكثر ما قد يكسر البناء: Compose التجريبي (FlowRow/DatePicker)، `LocalLifecycleOwner` من `androidx.lifecycle.compose`، مراجع `app::activeUserId` كـ suspend، نصوص بمعاملات موضعية (`%2$s`). أي خطأ يرسله المستخدم يُصلح أولًا.
- لم تُجرَّب الكاميرا على جهاز حقيقي (المسح، الأذونات، الأداء).
- Robolectric يحتاج إنترنت أول مرة. `gradle-wrapper.jar` غير موجود. workflow لبناء APK لم يُرفع (403).
- PIN المدير المؤقت 1234 خطر أمني.
- الحفظ التلقائي يكتب في قاعدة البيانات على كل تغيير بالسلة (مقبول لـ SQLite، يُراجَع إن ظهر بطء على أجهزة ضعيفة).

## الاختبارات (كلها مكتوبة، **لم تُشغَّل**)
`MoneyTest` 7، `QuantitiesTest` 4، `SaleCalculatorTest` 5، `CartTest` 11، `ScanDebouncerTest` 3، `SaleServiceTest` 11، `ProductRepositoryTest` 8، `CashierSessionTest` 11. لا اختبارات UI.

## المهمة التالية مباشرة (المهمة 6 = المرحلة 2: المشتريات والموردون)
1. `data/repo/SupplierRepository` + `PurchaseService`: فاتورة شراء (DRAFT→APPROVED) داخل Transaction: دفعة `StockBatch` + حركة `PURCHASE` + `adjustQty` + تحديث `costPrice` للمنتج، مفتاح idempotency `PURCHASE:{uuid}:{line}`، المدفوع/المتبقي، رقم متسلسل عند الاعتماد.
2. مرتجع مشتريات (`isReturn`) وكشف حساب المورد (من `purchase_invoices`).
3. اختبارات Robolectric (اعتماد يضيف دفعة وحركة، إعادة الاعتماد لا تكرر، مرتجع يخصم من FEFO).
4. شاشات: الموردون، فاتورة شراء، كشف حساب.
(بديل أولوية ممكن: إكمال نواقص المرحلة 1 المذكورة أعلاه قبل الانتقال.)

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`.
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction (تعديل المنتج لا يلمسه)؛ كل كمية داخلة تنشئ `StockBatch`.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`. `SalesInvoice.uuid` يمنع تكرار المحاولة (`duplicate=true`)، `number` عند الإتمام فقط.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- المنتهي الصلاحية يُتجاهل في FEFO إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية من الواجهة = نهاية اليوم (UTC midnight + 86_399_999).
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- **الكاشير:** السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid` يشير للسلة المفتوحة؛ قائمة "المعلقة" تستثني السلة الحالية؛ العمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings.xml؛ PIN مشفّر بـ PBKDF2.
