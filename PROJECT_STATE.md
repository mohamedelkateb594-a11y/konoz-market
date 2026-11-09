# PROJECT_STATE.md

_آخر تحديث: 2026-11-09 (نقطة توقف محفوظة بعد المهمة 6)_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، `main`)
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## نقطة التوقف الحالية
- **المرحلة 1 مكتملة كتابةً** (هوية، قاعدة بيانات، منتجات كاملة، باركود، كاشير، حفظ فواتير) مع استثناءات موثّقة أدناه. آخر مهمة: **المهمة 6** (commit 5419e8e) + تصحيح خطأ في اختبار CSV (انظر TASK_LOG).
- لا توجد مهمة نصف مكتملة. **لم تبدأ** المرحلة 2.
- **الخطوة التالية الإلزامية قبل أي كود جديد:** المستخدم يبني المشروع في Android Studio (Sync + Build) ويشغّل الاختبارات ويرسل نص أي خطأ، ويُصلح أولًا. 6 مهام مكتوبة بلا بناء واحد.

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة؛ نصوص AR/EN في `res/values{,-ar}/`: `strings.xml` (منتجات)، `strings_cashier.xml`، `strings_catalog.xml` (مورد/تصنيف/CSV/صورة/تحديث أسعار).
- `KonozApp.kt`: `database`, `saleService`, `productRepository`, `catalogRepository`, `productCsvService`, `activeUserId()` (**مؤقت = admin**).
- `core/`: `Money`, `Quantities`, `PinHasher`, `ExpiryDates`, `Percent`, `Csv`.
- `data/`: `KonozDatabase`، `entity/*` (4)، `dao/{CatalogDaos,StockSalesDaos,OperationsDaos}`، `repo/{ProductRepository,CatalogRepository,ProductCsvService}`، `seed/DatabaseInitializer` (+`SettingKeys`).
- `domain/`: `SaleModels`, `SaleCalculator`, `SaleService`, `Cart`, `CashierSession` (+`CashierMessage`, `CheckoutOutcome`).
- `ui/`: `KonozRoot` (الكاشير والمنتجات حقيقيان، الرئيسية/المزيد placeholder)، `theme/*`، `common/{Format,ProductImages,ProductImage}`، `products/*` (5 ملفات)، `scanner/{CameraBarcodeScanner,ScanDebouncer}`، `cashier/{CashierViewModel,CashierScreen,CashierRoute}`.
- اختبارات JUnit: `core/{MoneyTest,QuantitiesTest,CsvTest(+PercentTest,ExpiryDatesTest)}`, `domain/{SaleCalculatorTest,CartTest}`, `ui/scanner/ScanDebouncerTest`؛ Robolectric+Room: `domain/{SaleServiceTest,CashierSessionTest}`, `data/repo/{ProductRepositoryTest,ProductCsvServiceTest}`.

## مكتمل كتابةً (لم يُبنَ/يُختبر)
- هيكل، هوية Light/Dark، AR/EN، Room (20 جدول + FK + Indexes)، `Money`.
- `SaleService`: Transaction، FEFO، دفع مختلط، تعليق/استرجاع، idempotency، تجميد التكلفة، منتهي الصلاحية، مخزون سالب اختياري.
- **المنتجات:** قائمة + بحث + فلتر تصنيف + مفضلة + تفعيل/إيقاف (مع تأكيد عند الإيقاف)، نموذج إضافة/تعديل (تصنيف ومورد مع إضافة جديد من النموذج، وحدة، تكلفة/بيع/هامش لحظي، حد تنبيه، كرتونة، كمية افتتاحية بالقطع/الكراتين، صلاحية، صورة من المعرض، **مسح الباركود بالكاميرا داخل النموذج**، باركودات متعددة)، تحديث أسعار بالجملة من الواجهة (نسبة لتصنيف/الكل)، **استيراد/تصدير CSV** (UTF-8 + BOM، يكتشف `,` `;` Tab، كل سطر في Transaction مستقلة مع تقرير أخطاء برقم السطر، المنتج الموجود يُحدّث ومخزونه لا يتغير).
- **الكاشير:** مسح بالكاميرا (debounce + اهتزاز)، بحث، مفضلة، سلة (مسح مكرر يزيد، باركود كرتونة يضيف N، تعديل كمية/وزن، حذف)، خصم بند/فاتورة، ضريبة، تعديل سعر (المدير فقط + Audit)، حد خصم الكاشير، دفع مختلط، تعليق/استرجاع/حذف، حفظ تلقائي واستعادة بعد إغلاق مفاجئ، تنبيه المنتهي مع تأكيد، باركود غير مسجل ← نموذج منتج بالباركود، نافذة نجاح.
- Seed: `admin` / PIN مؤقت 1234، 8 تصنيفات، إعدادات افتراضية.

## استثناءات من المرحلة 1 (مؤجّلة عمدًا)
- **لا دعم Excel الأصلي (.xlsx)**: CSV فقط (يفتحه Excel). إضافة .xlsx تحتاج مكتبة خارجية — قرار للمستخدم.
- **لا حذف نهائي للمنتج** (إيقاف فقط)، لأن بنود الفواتير تشير إليه.
- صور المنتجات تُحفظ كملفات في `filesDir/product_images` (خارج قاعدة البيانات): **يجب تضمينها في النسخ الاحتياطي (المرحلة 4)**، ولا تُطبّق معلومات EXIF للدوران، والصور القديمة لا تُحذف عند الاستبدال.
- استيراد CSV: باركود مكرر بالملف لمنتجين مختلفين يُعتبر تحديثًا للمنتج الأول (مطابقة بالباركود الأول)، لا خطأ.

## غير مكتمل (مراحل لاحقة)
- المرحلة 2: المشتريات/الموردون (شاشة إدارة الموردين وكشف الحساب)، المرتجعات، تسويات المخزون، تنبيهات المخزون والصلاحية (شاشات + منطق المشتريات/المرتجعات).
- المرحلة 3: المصروفات، الجرد، التقارير، اللوحة الرئيسية.
- المرحلة 4: الورديات، شاشة دخول + إجبار تغيير PIN + إدارة المستخدمين/الصلاحيات (حاليًا admin دائمًا)، الطباعة والمشاركة (لا طباعة ولا مشاركة فاتورة حتى الآن)، النسخ الاحتياطي (مع الصور).
- أخرى: شاشة إعدادات (الضريبة وحد الخصم من `app_settings` فقط حاليًا)، خط Cairo (ملفات TTF غير مضمّنة)، APK، اختبارات UI، اللوحة الرئيسية والمزيد مازالتا placeholder.

## مخاطر / أخطاء
- **لم يُبنَ أي شيء قط** (لا Android SDK في بيئة Claude). أكثر ما قد يكسر البناء: Compose التجريبي (FlowRow/DatePicker)، `LocalLifecycleOwner` من `androidx.lifecycle.compose`، `app::activeUserId` كـ suspend، نصوص بمعاملات موضعية، `CreateDocument("text/csv")`، `data object`، استدعاءات suspend داخل lambdas متداخلة.
- لم تُجرَّب الكاميرا ولا اختيار الصور ولا فتح/حفظ ملفات CSV على جهاز حقيقي.
- Robolectric يحتاج إنترنت أول مرة. `gradle-wrapper.jar` غير موجود. workflow لبناء APK لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني حتى تضاف شاشة الدخول.
- الحفظ التلقائي للسلة يكتب في القاعدة مع كل تغيير (راجعه إن ظهر بطء على أجهزة ضعيفة).

## الاختبارات: 75 اختبارًا مكتوبًا، **لم يُشغَّل أي منها**
Money 7، Quantities 4، SaleCalculator 5، Cart 11، ScanDebouncer 3، SaleService 11، ProductRepository 8، CashierSession 11، Csv 5، Percent 2، ExpiryDates 2، ProductCsvService 6. لا اختبارات UI.

## المهمة التالية مباشرة (المهمة 7 = المرحلة 2: المشتريات والموردون) — بعد بناء المستخدم الناجح
1. `domain/PurchaseService`: فاتورة شراء (DRAFT→APPROVED) داخل Transaction: `StockBatch` + حركة `PURCHASE` + `adjustQty` + تحديث `costPrice`، مفتاح idempotency `PURCHASE:{uuid}:{line}`، المدفوع/المتبقي، رقم متسلسل عند الاعتماد.
2. مرتجع مشتريات (`isReturn`) يخصم من FEFO وكشف حساب المورد.
3. اختبارات Robolectric (اعتماد يضيف دفعة وحركة، إعادة الاعتماد لا تكرر، مرتجع يخصم).
4. شاشات: الموردون، فاتورة شراء، كشف حساب، تمكين إدارة الموردين من تبويب "المزيد".

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`.
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction (تعديل المنتج واستيراد CSV لا يلمسانه)؛ كل كمية داخلة تنشئ `StockBatch`.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`. `SalesInvoice.uuid` يمنع تكرار المحاولة (`duplicate=true`)، `number` عند الإتمام فقط.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- المنتهي الصلاحية يُتجاهل في FEFO إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية يُخزَّن نهاية اليوم عبر `ExpiryDates` (UTC midnight + 86_399_999).
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- الكاشير: السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid` يشير للسلة المفتوحة؛ قائمة "المعلقة" تستثني السلة الحالية؛ العمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- CSV: أعمدة `name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton, barcodes ("|" و "code*units"), quantity, expiry (yyyy-MM-dd), active`؛ `quantity` افتتاحي للجديد فقط.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings*.xml؛ PIN مشفّر بـ PBKDF2.
