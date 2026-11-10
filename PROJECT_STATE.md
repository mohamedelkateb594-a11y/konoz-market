# PROJECT_STATE.md

_آخر تحديث: 2026-11-10 (نقطة توقف بعد المهمة 8b)_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (خاص، `main`)
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## نقطة التوقف الحالية
- المرحلتان 1 و 2 **مكتملتان كتابةً** (مع استثناءات موثّقة). آخر مهمة: **8b** (commit f7246f3) + إصلاح استيراد ناقص في `StockOpsScreen`. لا توجد مهمة نصف مكتملة. **لم تبدأ** المرحلة 3.
- **لم يُبنَ أي شيء قط.** 10 مهام مكتوبة بلا بناء واحد. الخطوة الموصى بها قبل أي كود جديد: المستخدم يبني في Android Studio (Sync + Build + اختبارات) ويرسل الأخطاء (المستخدم طلب المتابعة مرارًا بدون بناء).

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة؛ نصوص AR/EN في `res/values{,-ar}/`: `strings.xml` (منتجات)، `strings_cashier.xml`، `strings_catalog.xml`، `strings_purchases.xml`، `strings_inventory.xml` (مرتجعات/تسويات/حركة/تنبيهات).
- `KonozApp.kt`: `database`, `saleService`, `purchaseService`, `returnService`, `stockAdjustmentService`, `productRepository`, `catalogRepository`, `productCsvService`, `activeUserId()` (**مؤقت = admin**).
- `core/`: `Money`, `Quantities`, `PinHasher`, `ExpiryDates`, `Percent`, `Csv`, `Allocation` (توزيع نسبي مطابق بأكبر البواقي + `portion`).
- `data/`: `KonozDatabase`، `entity/*` (4)، `dao/{CatalogDaos,StockSalesDaos,OperationsDaos}`، `repo/{ProductRepository,CatalogRepository,ProductCsvService}`، `seed/DatabaseInitializer` (+`SettingKeys`).
- `domain/`: `SaleModels`, `SaleCalculator`, `SaleService`, `Cart`, `CashierSession`, `Purchase{Models,Calculator,Service}`, `PurchaseEntry`, `SupplierBalances`, **`ReturnModels`, `ReturnService`, `ReturnPreview`, `StockAdjustmentService` (+`StockException`, `AdjustmentResult`)**.
- `ui/`: `KonozRoot`، `theme/*`، `common/{Format,ProductImages,ProductImage,ProductSearch}`، `products/*`، `scanner/*`، `cashier/*`، `suppliers/*`، `purchases/*`، **`returns/{ReturnsViewModel,ReturnsScreen}`، `stockops/{StockOpsViewModel,StockOpsScreen}`، `movements/{MovementLogViewModel,MovementLogScreen}`، `alerts/{StockAlertsViewModel(+AlertMath),StockAlertsScreen}`، `more/MoreRoute`** (قائمة: موردون، مرتجعات مبيعات، تسويات، سجل حركة، تنبيهات).
- اختبارات JUnit: `core/{Money,Quantities,Csv(+Percent,ExpiryDates),Allocation}Test`, `domain/{SaleCalculator,Cart,PurchaseCalculator,PurchaseEntry(+SupplierBalances),ReturnPreview(+AlertMath)}Test`, `ui/scanner/ScanDebouncerTest`؛ Robolectric+Room: `domain/{SaleService,CashierSession,PurchaseService,ReturnService,StockAdjustmentService}Test`, `data/repo/{ProductRepository,ProductCsvService,CatalogRepository}Test`.

## مكتمل كتابةً (لم يُبنَ/يُختبر)
- هيكل، هوية، AR/EN، Room (20 جدول)، المنتجات الكاملة، الكاشير الكامل، المشتريات والموردون (منطق + شاشات).
- **مرتجعات المبيعات:** `ReturnService.createReturn` (بحث برقم الفاتورة، بند/كله، كمية ≤ المتاح، المبلغ المسترد = حصة البند من إجمالي الفاتورة بعد الخصم والضريبة، تراكميًا فمجموع المرتجعات = الحصة بالضبط، RESELLABLE يعيد دفعة بتكلفة البيع بلا تاريخ صلاحية، DAMAGED حركتان صافيهما صفر، idempotency عبر `sales_returns.uuid` الفريد)، شاشة المرتجع مع معاينة المبلغ.
- **تسويات المخزون:** `StockAdjustmentService` (`adjust` بسبب إلزامي، `writeOff` تالف/منتهي، `convertUnits` كرتونة←قطع بنقل التكلفة والصلاحية، FEFO، لا سالب، idempotent)، شاشة تسويات، سجل حركة منتج، شاشة تنبيهات (ناقص/نفد/قارب الانتهاء حسب `expiry_alert_days`/منتهي) مع كمية شراء مقترحة (لتصل لضعف حد التنبيه).

## استثناءات مؤجّلة عمدًا
- لا Excel أصلي (.xlsx): CSV فقط. لا حذف نهائي للمنتج (إيقاف فقط).
- صور المنتجات في `filesDir/product_images` خارج القاعدة: **يجب تضمينها في النسخ الاحتياطي**.
- مسودات المشتريات (`saveDraft/loadDraft`) بلا واجهة. قائمة المشتريات المقترحة تُعرض فقط (لا تحويلها لفاتورة شراء أو مشاركتها).
- المرتجع المسترد يُسجَّل نقدًا افتراضيًا (الحقل `refundMethod` موجود لكن الواجهة لا تختار طريقة الاسترداد).

## غير مكتمل
- المرحلة 3 (**التالية**): المصروفات، الجرد، التقارير والأرباح، اللوحة الرئيسية.
- المرحلة 4: الورديات (وتأثير سداد الموردين والمرتجعات النقدية على الخزينة)، شاشة دخول + إجبار تغيير PIN + مستخدمون/صلاحيات (حماية تعديل المخزون والمرتجعات: الشاشات الحالية متاحة لكل من يفتح التطبيق)، طباعة/مشاركة فواتير، نسخ احتياطي (مع الصور).
- أخرى: شاشة إعدادات، خط Cairo، APK، اختبارات UI، اللوحة الرئيسية placeholder.

## مخاطر / أخطاء
- **لم يُبنَ أي شيء قط** (لا Android SDK في بيئة Claude). أكثر ما قد يكسر البناء: ملفات UI الكثيرة (Compose التجريبي FlowRow/DatePicker، استيرادات ناقصة مثل ما حدث في StockOpsScreen)، `app::activeUserId` كـ suspend، نصوص بمعاملات، `data object`، `return@tx` داخل `db.tx {}`، `continue` داخل `when`، تكرار أسماء موارد النصوص بين strings*.xml (راجعتها يدويًا، لم تُجمّع).
- لم تُجرَّب الكاميرا/الصور/CSV على جهاز. Robolectric يحتاج إنترنت أول مرة. `gradle-wrapper.jar` غير موجود. workflow لبناء APK لم يُرفع (403 على `.github/workflows`).
- PIN المدير المؤقت 1234 خطر أمني حتى تضاف شاشة الدخول.
- الكتلة المالية للتقارير (مستقبلًا): صافي المبيعات = مبيعات مكتملة − `sales_returns.refundAmount`؛ تكلفة المرتجع القابل للبيع = `return_items` بحالة RESELLABLE × `unitCostAtSale` (تُطرح من COGS)؛ المخزون التالف/المنتهي خسائر من حركات DAMAGED/EXPIRED/ADJUSTMENT السالبة × `unitCost`.

## الاختبارات: 128 مكتوبًا، **لم يُشغَّل أي منها**
(المهمة 8: Allocation 5، ReturnService 10، StockAdjustmentService 9، ReturnPreview+AlertMath 4 = 28 جديدة على 100 سابقة).

## المهمة التالية مباشرة (المهمة 9 = المصروفات والجرد)
1. `domain/ExpenseService` + شاشة المصروفات: إضافة (قيمة، نوع من `ExpenseCategory`، تاريخ، وصف، ملاحظات، صورة إيصال اختيارية، `paidInCash`)، تعديل/حذف بتأكيد + Audit، إجمالي يومي/شهري/سنوي (استعلامات `observeTotalBetween` موجودة)، ربط `shiftId` بالوردية المفتوحة، اختبارات.
2. `domain/StockCountService` (الجرد): بدء جرد (شامل/حسب تصنيف) يلتقط `systemQtyMilli` وتكلفة اللحظة، إدخال الكميات الفعلية يدويًا أو بالباركود، مسودة، حساب العجز/الزيادة وقيمتها بالتكلفة، **الاعتماد فقط يعدّل المخزون** بحركات `COUNT_ADJUSTMENT` (زيادة = دفعة جديدة، عجز = FEFO) مع تسجيل المستخدم والتاريخ، منع اعتماد مكرر، حساب الفرق بالنسبة للمخزون الحالي وليس الملتقط (مبيعات أثناء الجرد)، سجل تاريخي، اختبارات + شاشات (قائمة جرد، جرد جديد، إدخال، مراجعة الفروق، اعتماد بتأكيد).
3. تمكين المصروفات والجرد في `MoreRoute`.
ثم التقارير/الأرباح/اللوحة الرئيسية (المهمة 10).

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`؛ أي تقسيم مبلغ على أجزاء يجب أن يساوي المبلغ بالضبط (`Allocation.proportional`).
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction (تعديل المنتج واستيراد CSV لا يلمسانه)؛ كل كمية داخلة تنشئ `StockBatch`؛ خروج المخزون دائمًا FEFO.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`، `PURCHASE:{uuid}:{line}`، `PRETURN:{uuid}:{line}:{alloc}`، `SRETURN:{uuid}:{i}:in|dmg`، `ADJ:{uuid}:{n}`، `WO:{uuid}:{n}`، `CONV:{uuid}:out:{n}|in`. المستندات ذات `uuid` (بيع، شراء، مرتجع مبيعات) تمنع تكرار المحاولة؛ `number` عند الإتمام/الاعتماد فقط؛ الشراء ومرتجع المشتريات يتشاركان سلسلة أرقام واحدة. كل شاشة تولّد uuid واحدًا وتعيد استخدامه عند المحاولة.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- المشتريات: `costPrice` = آخر سعر شراء فعلي (بعد الخصومات، لا يُضبط لصفر)؛ المرتجع للمورد بـ FEFO بتكلفة الدفعة بلا سالب؛ رصيد المورد = مجموع `remaining` للمشتريات − مجموع `remaining` للمرتجعات؛ حذف مورد ممنوع إن كان له فواتير.
- مرتجع المبيعات: الفاتورة الأصلية لا تُعدَّل؛ التقارير تطرح المرتجعات؛ `ReturnPreview` يجب أن يبقى مطابقًا لمنطق `ReturnService`.
- المنتهي الصلاحية يُتجاهل في FEFO البيع إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية يُخزَّن نهاية اليوم عبر `ExpiryDates`.
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- الكاشير: السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid`؛ قائمة "المعلقة" تستثني الحالية؛ عمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- CSV: أعمدة `name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton, barcodes, quantity, expiry, active`؛ `quantity` افتتاحي للجديد فقط.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration (المخطط لم يصدر بعد، لذا كان تعديل `SalesReturn` بإضافة `uuid` ممكنًا بلا Migration — ابتداءً من أول نسخة تُنشر أي تغيير يحتاج Migration)؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings*.xml؛ PIN مشفّر بـ PBKDF2.
- تنقل تبويب "المزيد" بمسارات نصية في `MoreRoute` (لإضافة شاشة: مسار + عنصر في `MoreMenu` + `parentOf` إن لزم).
