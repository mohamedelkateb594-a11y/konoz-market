# PROJECT_STATE.md

_آخر تحديث: 2026-11-10 (نقطة توقف بعد المهمة 7b)_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، `main`)
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## نقطة التوقف الحالية
- المرحلة 1 مكتملة كتابةً. المرحلة 2 (المخزون والمشتريات): **المشتريات والموردون مكتملة كتابةً** (7a منطق commit f2a4dba، 7b شاشات commit c49deea). لا توجد مهمة نصف مكتملة.
- **لم يُبنَ أي شيء قط.** 8 مهام مكتوبة بلا بناء واحد. الخطوة الموصى بها قبل أي كود جديد: المستخدم يبني في Android Studio (Sync + Build + اختبارات) ويرسل الأخطاء.

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة؛ نصوص AR/EN في `res/values{,-ar}/`: `strings.xml` (منتجات)، `strings_cashier.xml`، `strings_catalog.xml`، `strings_purchases.xml` (موردون/مشتريات/كشف حساب/المزيد).
- `KonozApp.kt`: `database`, `saleService`, `purchaseService`, `productRepository`, `catalogRepository`, `productCsvService`, `activeUserId()` (**مؤقت = admin**).
- `core/`: `Money`, `Quantities`, `PinHasher`, `ExpiryDates`, `Percent`, `Csv`.
- `data/`: `KonozDatabase`، `entity/*` (4)، `dao/{CatalogDaos,StockSalesDaos,OperationsDaos}`، `repo/{ProductRepository,CatalogRepository (موردون + قراءة الفواتير),ProductCsvService}`، `seed/DatabaseInitializer` (+`SettingKeys`).
- `domain/`: `SaleModels`, `SaleCalculator`, `SaleService`, `Cart`, `CashierSession`, `PurchaseModels`, `PurchaseCalculator` (+`SupplierStatement`), `PurchaseService`, `PurchaseEntry` (حالة شاشة الشراء النقية)، `SupplierBalances`.
- `ui/`: `KonozRoot` (الكاشير، المنتجات، المزيد حقيقية؛ الرئيسية placeholder)، `theme/*`، `common/{Format,ProductImages,ProductImage}`، `products/*` (5)، `scanner/*`، `cashier/*`، **`more/MoreRoute` (مسارات: menu/suppliers/statement/{id}/purchase/{id}/{0|1})، `suppliers/{SuppliersViewModel,SuppliersScreen,StatementViewModel,StatementScreen}`، `purchases/{PurchaseViewModel,PurchaseScreen}`**.
- اختبارات JUnit: `core/{MoneyTest,QuantitiesTest,CsvTest(+PercentTest,ExpiryDatesTest)}`, `domain/{SaleCalculatorTest,CartTest,PurchaseCalculatorTest,PurchaseEntryTest(+SupplierBalancesTest)}`, `ui/scanner/ScanDebouncerTest`؛ Robolectric+Room: `domain/{SaleServiceTest,CashierSessionTest,PurchaseServiceTest}`, `data/repo/{ProductRepositoryTest,ProductCsvServiceTest,CatalogRepositoryTest}`.

## مكتمل كتابةً (لم يُبنَ/يُختبر)
- هيكل، هوية Light/Dark، AR/EN، Room (20 جدول)، `Money`، `SaleService`، المنتجات الكاملة (نموذج، صورة، مسح، تصنيف/مورد جديد، أسعار بالجملة، CSV)، الكاشير الكامل.
- **المشتريات والموردون (7a+7b):** `PurchaseService` (مسودة/اعتماد/مرتجع للمورد/سداد/رصيد/كشف)، شاشة الموردين (بحث، إضافة/تعديل/حذف محمي، رصيد لكل مورد)، كشف حساب (رصيد، فواتير، سداد/تحصيل)، شاشة فاتورة شراء/مرتجع (بحث + مسح باركود يضيف N لباركود الكرتونة، كمية/سعر/خصم/صلاحية، خصم فاتورة، مدفوع، تأكيد قبل الاعتماد، uuid لكل شاشة لمنع التكرار).

## استثناءات مؤجّلة عمدًا
- لا Excel أصلي (.xlsx): CSV فقط (قرار للمستخدم). لا حذف نهائي للمنتج (إيقاف فقط).
- صور المنتجات في `filesDir/product_images` خارج القاعدة: **يجب تضمينها في النسخ الاحتياطي**، لا EXIF، القديمة لا تُحذف.
- استيراد CSV: باركود مكرر لمنتجين = تحديث للأول.
- المسودات: `PurchaseService.saveDraft/loadDraft/cancelDraft` موجودة ومختبرة كتابةً، لكن **لا واجهة لها** (شاشة الشراء تعتمد مباشرة فقط).

## غير مكتمل
- **المرحلة 2 المتبقي (المهمة 8):** مرتجعات المبيعات (منطق + شاشة، جداول `sales_returns`/`return_items` موجودة بلا كود)، تسويات المخزون (تالف/منتهي/تعديل يدوي/تحويل وحدات) مع حركاتها، سجل حركة منتج، شاشة تنبيهات المخزون والصلاحية، قائمة مشتريات مقترحة من النواقص، إضافة الموردين إلى القائمة الرئيسية للمزيد مع باقي الشاشات.
- المرحلة 3: المصروفات، الجرد، التقارير، اللوحة الرئيسية.
- المرحلة 4: الورديات (وتأثير سداد الموردين النقدي على الخزينة)، شاشة دخول + إجبار تغيير PIN + إدارة المستخدمين/الصلاحيات، الطباعة والمشاركة، النسخ الاحتياطي (مع الصور).
- أخرى: شاشة إعدادات، خط Cairo، APK، اختبارات UI، اللوحة الرئيسية placeholder.

## مخاطر / أخطاء
- **لم يُبنَ أي شيء قط** (لا Android SDK في بيئة Claude). أكثر ما قد يكسر البناء: Compose التجريبي (FlowRow/DatePicker)، `LocalLifecycleOwner` من `androidx.lifecycle.compose`، `app::activeUserId` كـ suspend، نصوص بمعاملات موضعية، `CreateDocument("text/csv")`، `data object`، suspend داخل lambdas متداخلة، `return@tx` داخل `db.tx {}`، تكرار أسماء موارد النصوص بين ملفات strings*.xml (راجعت يدويًا ولم أجد تكرارًا، لكن لم يُجمّع).
- لم تُجرَّب الكاميرا/الصور/CSV على جهاز.
- Robolectric يحتاج إنترنت أول مرة. `gradle-wrapper.jar` غير موجود. workflow لبناء APK لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني حتى تضاف شاشة الدخول.
- سداد الموردين (`settle` والمدفوع عند الاعتماد) لا يؤثر على خزينة الوردية حتى تُبنى الورديات.
- كشف حساب المورد: الأرصدة الجارية من `remaining` الحالي (بعد التسويات) وليس من تاريخ كل دفعة (لا جدول مدفوعات موردين مستقل).

## الاختبارات: 100 مكتوبًا، **لم يُشغَّل أي منها**
Money 7، Quantities 4، SaleCalculator 5، Cart 11، ScanDebouncer 3، SaleService 11، ProductRepository 8، CashierSession 11، Csv 5، Percent 2، ExpiryDates 2، ProductCsvService 6، PurchaseCalculator 7، PurchaseService 8، **PurchaseEntry 5، SupplierBalances 2، CatalogRepository 3**. لا اختبارات UI.

## المهمة التالية مباشرة (المهمة 8 = مرتجعات المبيعات وتسويات المخزون)
1. `domain/ReturnService`: مرتجع مبيعات لفاتورة مكتملة (بحث بالرقم، بند/كله، الكمية لا تتجاوز المباع مخصومًا منه المُرتجع سابقًا عبر `returnedQty`، المبلغ المسترد بسعر البيع الفعلي بعد حصة الخصم)، الحالة RESELLABLE تُعيد المخزون (دفعة جديدة بتكلفة البيع المحفوظة + حركة RETURN_IN) وDAMAGED لا تُعيده (حركة DAMAGED بكمية 0 أو تسجيل فقط، تُقرَّر بالمهمة)، `cashRefundsForShift` يعتمد على `shiftId`، مفتاح idempotency `RETURN:{uuid}:{item}`، Audit، اختبارات Robolectric.
2. `domain/StockAdjustmentService`: تالف/منتهي/تعديل يدوي (موجب/سالب) مع سبب، خصم FEFO للتالف/المنتهي، تحويل وحدات (UNIT_CONVERSION)، كلها بحركات ومفاتيح idempotency + اختبارات.
3. شاشات: مرتجع مبيعات، تسوية مخزون، سجل حركة منتج، تنبيهات المخزون/الصلاحية، وربطها في تبويب "المزيد" (`MoreRoute`).
ثم المرحلة 3 (مصروفات، جرد، تقارير).

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`.
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction (تعديل المنتج واستيراد CSV لا يلمسانه)؛ كل كمية داخلة تنشئ `StockBatch`.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`، `PURCHASE:{uuid}:{line}`، `PRETURN:{uuid}:{line}:{alloc}`. `uuid` في فواتير البيع والشراء يمنع تكرار المحاولة؛ `number` عند الإتمام/الاعتماد فقط؛ الشراء ومرتجع المشتريات يتشاركان سلسلة أرقام واحدة.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- **المشتريات:** `costPrice` للمنتج = آخر سعر شراء فعلي (بعد خصومات البند والفاتورة، لا يُضبط إلى صفر)؛ خصم الفاتورة يُوزَّع بأكبر البواقي (مجموعها مطابق)؛ المرتجع للمورد يُخرج المخزون بـ FEFO (بما فيه المنتهي) بتكلفة الدفعة ولا يسمح بالسالب؛ رصيد المورد = مجموع `remaining` للمشتريات المعتمدة − مجموع `remaining` للمرتجعات (السالب = المورد مدين لنا)؛ حذف مورد ممنوع إن كان له فواتير.
- المنتهي الصلاحية يُتجاهل في FEFO البيع إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية يُخزَّن نهاية اليوم عبر `ExpiryDates`.
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- الكاشير: السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid`؛ قائمة "المعلقة" تستثني الحالية؛ عمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- CSV: أعمدة `name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton, barcodes, quantity, expiry, active`؛ `quantity` افتتاحي للجديد فقط.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings*.xml؛ PIN مشفّر بـ PBKDF2.
- تنقل تبويب "المزيد" بمسارات نصية في `MoreRoute` (لإضافة شاشة: أضف مسارًا وبطاقة في `MoreMenu` ودالة `parentOf`).
