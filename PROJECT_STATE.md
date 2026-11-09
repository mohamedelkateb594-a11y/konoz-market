# PROJECT_STATE.md

_آخر تحديث: 2026-11-09 (نقطة توقف بعد المهمة 7a)_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، `main`)
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## نقطة التوقف الحالية
- المرحلة 1 مكتملة كتابةً (مع استثناءات أدناه). **المرحلة 2 بدأت**: المهمة **7a** (منطق المشتريات والمرتجعات للمورد + كشف الحساب + اختبارات) مرفوعة (commit f2a4dba). لا توجد مهمة نصف مكتملة.
- **لم يُبنَ أي شيء قط.** 7 مهام مكتوبة بلا بناء. الخطوة الموصى بها قبل أي كود جديد: المستخدم يبني في Android Studio (Sync + Build + اختبارات) ويرسل الأخطاء (المستخدم طلب المتابعة بدون بناء مرتين).

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة؛ نصوص AR/EN في `res/values{,-ar}/`: `strings.xml` (منتجات)، `strings_cashier.xml`، `strings_catalog.xml`.
- `KonozApp.kt`: `database`, `saleService`, `purchaseService`, `productRepository`, `catalogRepository`, `productCsvService`, `activeUserId()` (**مؤقت = admin**).
- `core/`: `Money`, `Quantities`, `PinHasher`, `ExpiryDates`, `Percent`, `Csv`.
- `data/`: `KonozDatabase`، `entity/*` (4)، `dao/{CatalogDaos,StockSalesDaos,OperationsDaos}`، `repo/{ProductRepository,CatalogRepository,ProductCsvService}`، `seed/DatabaseInitializer` (+`SettingKeys`).
- `domain/`: `SaleModels`, `SaleCalculator`, `SaleService`, `Cart`, `CashierSession`، **`PurchaseModels`, `PurchaseCalculator` (+`SupplierStatement`), `PurchaseService`**.
- `ui/`: `KonozRoot` (الكاشير والمنتجات حقيقيان، الرئيسية/المزيد placeholder)، `theme/*`، `common/{Format,ProductImages,ProductImage}`، `products/*` (5)، `scanner/*`، `cashier/*`.
- اختبارات JUnit: `core/{MoneyTest,QuantitiesTest,CsvTest(+PercentTest,ExpiryDatesTest)}`, `domain/{SaleCalculatorTest,CartTest,PurchaseCalculatorTest}`, `ui/scanner/ScanDebouncerTest`؛ Robolectric+Room: `domain/{SaleServiceTest,CashierSessionTest,PurchaseServiceTest}`, `data/repo/{ProductRepositoryTest,ProductCsvServiceTest}`.

## مكتمل كتابةً (لم يُبنَ/يُختبر)
- هيكل، هوية Light/Dark، AR/EN، Room (20 جدول)، `Money`، `SaleService`، المنتجات (قائمة/نموذج/صورة/مسح باركود/تصنيف ومورد جديد/أسعار بالجملة/CSV)، الكاشير الكامل (مسح، سلة، خصوم، ضريبة، دفع مختلط، تعليق، حفظ تلقائي، صلاحيات، منتهي الصلاحية، باركود غير مسجل).
- Seed: `admin` / PIN مؤقت 1234، 8 تصنيفات، إعدادات.
- **المهمة 7a — `PurchaseService` (منطق فقط، بلا شاشات):** `saveDraft`/`loadDraft`/`cancelDraft`، `approve` (شراء: دفعة لكل بند + حركة PURCHASE + كمية + `costPrice`= آخر سعر شراء، نفس uuid يعيد النتيجة بلا ترحيل جديد؛ مرتجع `isReturn=true`: خصم FEFO من الدفعات بما فيها المنتهي، يرفض المخزون السالب)، `settle` (سداد لاحق أو تحصيل استرداد)، `supplierBalance`، `statement`. خصم الفاتورة يُوزَّع على البنود بطريقة أكبر البواقي (مجموعها مطابق تمامًا) فتدخل في تكلفة الدفعات.

## استثناءات من المرحلة 1 (مؤجّلة عمدًا)
- لا Excel أصلي (.xlsx): CSV فقط (قرار للمستخدم). لا حذف نهائي للمنتج (إيقاف فقط).
- صور المنتجات في `filesDir/product_images` خارج القاعدة: **يجب تضمينها في النسخ الاحتياطي**، لا EXIF، القديمة لا تُحذف.
- استيراد CSV: باركود مكرر لمنتجين = تحديث للأول.

## غير مكتمل
- **المرحلة 2 المتبقي:** شاشات المشتريات (إدارة الموردين، فاتورة شراء/مسودة، مرتجع مشتريات، كشف حساب، سداد)، **مرتجعات المبيعات** (منطق + شاشة)، **تسويات/تالف/منتهي المخزون** (منطق + شاشة)، سجل حركة منتج، شاشة تنبيهات المخزون والصلاحية، تمكين تبويب "المزيد".
- المرحلة 3: المصروفات، الجرد، التقارير، اللوحة الرئيسية.
- المرحلة 4: الورديات (وتأثير سداد الموردين النقدي على الخزينة)، شاشة دخول + إجبار تغيير PIN + إدارة المستخدمين/الصلاحيات، الطباعة والمشاركة، النسخ الاحتياطي (مع الصور).
- أخرى: شاشة إعدادات، خط Cairo، APK، اختبارات UI، اللوحة الرئيسية والمزيد placeholder.

## مخاطر / أخطاء
- **لم يُبنَ أي شيء قط** (لا Android SDK في بيئة Claude). أكثر ما قد يكسر البناء: Compose التجريبي (FlowRow/DatePicker)، `LocalLifecycleOwner` من `androidx.lifecycle.compose`، `app::activeUserId` كـ suspend، نصوص بمعاملات موضعية، `CreateDocument("text/csv")`، `data object`، استدعاءات suspend داخل lambdas متداخلة، `return@tx` داخل `db.tx {}`.
- لم تُجرَّب الكاميرا/الصور/CSV على جهاز.
- Robolectric يحتاج إنترنت أول مرة. `gradle-wrapper.jar` غير موجود. workflow لبناء APK لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني حتى تضاف شاشة الدخول.
- سداد الموردين (`settle` والمدفوع عند الاعتماد) لا يؤثر على خزينة الوردية حتى تُبنى الورديات (المرحلة 4).
- كشف حساب المورد: الأرصدة الجارية تُحسب من `remaining` الحالي (بعد التسويات) وليس من التاريخ الأصلي لكل دفعة (لا جدول مدفوعات موردين مستقل في المخطط الحالي).

## الاختبارات: 90 مكتوبًا، **لم يُشغَّل أي منها**
Money 7، Quantities 4، SaleCalculator 5، Cart 11، ScanDebouncer 3، SaleService 11، ProductRepository 8، CashierSession 11، Csv 5، Percent 2، ExpiryDates 2، ProductCsvService 6، **PurchaseCalculator 7 (منها كشف الحساب)، PurchaseService 8**. لا اختبارات UI.

## المهمة التالية مباشرة (المهمة 7b = شاشات المشتريات والموردين)
1. تمكين تبويب "المزيد" (قائمة شاشات فرعية) ومسار تنقل داخلي.
2. شاشة الموردين (قائمة/إضافة/تعديل مع الرصيد من `observeSupplierBalance` — يحتاج دوال `update/delete` في `CatalogRepository` إن لزم).
3. شاشة فاتورة شراء/مرتجع (اختيار مورد، إضافة منتجات بالمسح/البحث، كمية/سعر/خصم/صلاحية/دفعة، مدفوع، حفظ مسودة، اعتماد بتأكيد) + ViewModel.
4. شاشة كشف حساب المورد + زر سداد (`settle`).
5. اختبارات لأي منطق جديد (ViewModel/حالة نقية).
ثم المهمة 8: مرتجعات المبيعات + تسويات المخزون + التنبيهات.

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`.
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction (تعديل المنتج واستيراد CSV لا يلمسانه)؛ كل كمية داخلة تنشئ `StockBatch`.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`، **`PURCHASE:{uuid}:{line}`، `PRETURN:{uuid}:{line}:{alloc}`**. `uuid` في فواتير البيع والشراء يمنع تكرار المحاولة؛ `number` عند الإتمام/الاعتماد فقط؛ الشراء ومرتجع المشتريات يتشاركان سلسلة أرقام واحدة.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- **المشتريات:** `costPrice` للمنتج = آخر سعر شراء فعلي (بعد خصومات البند والفاتورة، لا يُضبط إلى صفر)؛ الدفعة تأخذ تكلفتها الفعلية؛ المرتجع للمورد يُخرج المخزون بـ FEFO بتكلفة الدفعة ولا يسمح بالسالب؛ رصيد المورد = مجموع `remaining` للمشتريات المعتمدة − مجموع `remaining` للمرتجعات (السالب = المورد مدين لنا).
- المنتهي الصلاحية يُتجاهل في FEFO البيع إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية يُخزَّن نهاية اليوم عبر `ExpiryDates`.
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- الكاشير: السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid`؛ قائمة "المعلقة" تستثني الحالية؛ عمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- CSV: أعمدة `name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton, barcodes, quantity, expiry, active`؛ `quantity` افتتاحي للجديد فقط.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings*.xml؛ PIN مشفّر بـ PBKDF2.
