# PROJECT_STATE.md

_آخر تحديث: 2026-11-15 (Developer C، تحديث توثيقي فقط مبني على ملفات تسليم A وB وC والكود على `claude/developer-b` @ 6ca67b5)_

## المشروع
- **الاسم:** KONOZ MARKET - كنوز ماركت (`com.konoz.market`) - تطبيق POS لمحل بقالة/سوبر ماركت، Offline بالكامل.
- **الريبو:** github.com/mohamedelkateb594-a11y/konoz-market (`main` حتى المهمة 9، وكل ما بعده على `claude/developer-b` ومفتوح في PR #2).
- **التقنية:** Kotlin، Compose + M3، Room 2.6.1، CameraX 1.4.0 + ML Kit، WorkManager 2.9.1، MVVM، Flow، AGP 8.7.3، Kotlin 2.0.21، KSP، Robolectric 4.14.1، minSdk 24 / target 35.

## التوزيع بين المطوّرين (الفرع المشترك `claude/developer-b`)
- اقرأ `DEVELOPER_A_HANDOFF.md` و`DEVELOPER_B_HANDOFF.md` و`DEVELOPER_C_HANDOFF.md` و`list_commits` قبل أي كتابة، وسجّل ملكية أي مهمة (ملف LOCK_*) قبل أن تبدأ.
- حادثتا تكرار سابقتان: 4.2 (معتمدة تنفيذ B) و 4.4 (معتمدة تنفيذ B). فرعا C `claude/developer-c` و`claude/developer-c-4-4` مهملان ولا يُدمجان.
- 4.5 الطباعة الحرارية: مسجلة لـ A؛ نسخة C في PR #4 (`claude/developer-c-4-5`) مسودة بانتظار قرار المالك.
- فرع C `claude/developer-c-4-2b` يحوي أعمالًا **غير مدمجة ومطلوبة بقرارات المالك**: البيع يحتاج وردية مفتوحة، سداد الموردين يؤثر على الخزينة (جدول `supplier_payments` وقاعدة بيانات v2 + Migration)، صلاحيات الكاشير، تصدير تقرير CSV. لم تُدمج بعد فهي غير موجودة في الفرع المشترك.

## نقطة التوقف الحالية
- المراحل 1 و 2 و 3 (المهام 9 و 10 و 11) ومعظم المرحلة 4 (4.2 المستخدمون، 4.3 فاتورة وواتساب، 4.4 نسخ احتياطي) **مكتوبة**.
- البناء: نجح `assembleDebug` على GitHub Actions لفرعي C المبنيّين فوق فرع B (2026-11-15). خطوة الاختبارات `continue-on-error` فلا يُعتمد على لونها؛ **لم تُراجَع نتائج الاختبارات** ولا تجربة على جهاز.
- المتبقي: طباعة ESC/POS بلوتوث (قرار المالك بين A وC)، تصدير التقارير PDF/CSV/Excel، المرحلة 5 (اختبارات/أداء/دون إنترنت/APK)، قرارات المالك المذكورة أعلاه ودمج فرع `claude/developer-c-4-2b` المناسب.

## خريطة الملفات (لا تقرأ غير ما تحتاجه)
- Gradle/Manifest/Resources: المعتادة (+ `FileProvider` و`res/xml/file_paths.xml`)؛ نصوص AR/EN في `res/values{,-ar}/strings*.xml` لكل وحدة (منتجات، كاشير، كتالوج، مشتريات، مخزون، مصروفات، جرد، تقارير، ورديات، دخول، فواتير، تنبيه السكان، نسخ احتياطي).
- `KonozApp.kt`: كل الخدمات (`database`, `saleService`, `purchaseService`, `returnService`, `stockAdjustmentService`, `stockCountService`, `expenseService`, `reportService`, `shiftService`, `userService`, خدمة النسخ الاحتياطي من B، المستودعات)، `session` (المستخدم المسجّل)، `activeUserId()`.
- `core/`: `Money`, `Quantities`, `PinHasher`, `ExpiryDates`, `Percent`, `Csv`, `Allocation`.
- `data/`: `KonozDatabase` (الإصدار 1 على الفرع المشترك)، `entity/*`، `dao/*`، `repo/*`، `seed/DatabaseInitializer`.
- `domain/`: البيع والمشتريات والمرتجعات والتسويات و`ExpenseService`/`ExpensePeriods` و`StockCountService` و`ReportService` و`ShiftService` و`Permissions` و`AuthSession` و`UserService` و`PhoneNumbers` و`InvoiceData`.
- `backup/*` (B): `BackupService`, `BackupScheduler`, `BackupWorker`.
- `ui/`: `KonozRoot`، `theme`، `common`، `products`، `scanner`، `cashier`، `suppliers`، `purchases`، `returns`، `stockops`، `movements`، `alerts`، `expenses`، `stockcount`، `reports`، `dashboard`، `shifts`، `auth`، `users`، `receipt`، `backup`، `more/MoreRoute` (قائمة مفلترة بالصلاحيات).
- الاختبارات لكل وحدة من الأعلى في `app/src/test`: JUnit للمنطق النقي، Robolectric+Room للخدمات. **لم يُتحقق من نتائجها بعد.**

## مكتمل كتابةً (مختصر)
- هيكل، هوية، AR/EN، Room (20 جدول)، المنتجات، الكاشير، المشتريات والموردون، مرتجعات المبيعات، تسويات المخزون، التنبيهات (المهام 1–8).
- 9: المصروفات (إضافة idempotent، إيصال، إجماليات) والجرد (الاعتماد وحده يعدّل المخزون).
- 10: التقارير والأرباح واللوحة (الضريبة تُستبعد من الإيراد، خسائر المخزون في صافي الربح).
- 11: الورديات والخزينة والملخص الحي.
- 4.2: أدوار ADMIN/CASHIER/STOCK_KEEPER، دخول بـ PIN، قفل بعد 5 محاولات، إجبار تغيير PIN الافتراضي 1234، إدارة المستخدمين.
- 4.3: إيصال 80 مم كـ PDF/PNG (عربي مشكّل)، إرسال واتساب برقم يكتبه الكاشير، قائمة مشاركة، شاشة الفواتير، زر «إرسال الفاتورة» في الكاشير، تنبيه (نغمة + اهتزاز + شريط) عند كل سكان يضيف منتجًا.
- 4.4: نسخة واحدة zip (القاعدة + صور المنتجات + الإيصالات + manifest مع SHA-256)، الاستعادة تستبدل كل شيء بعد تحقق ونسخة أمان، نسخ يومي داخل التطبيق (آخر 7)، للمدير فقط.

## استثناءات مؤجّلة عمدًا / حدود معلنة
- لا Excel أصلي (.xlsx): CSV فقط. لا حذف نهائي للمنتج (إيقاف فقط).
- مسودات المشتريات بلا واجهة؛ قائمة المشتريات المقترحة تُعرض فقط.
- المرتجع المسترد يُسجَّل نقدًا افتراضيًا (لا اختيار طريقة استرداد بالواجهة).
- رقم العميل في الواتساب لا يُحفظ (لا جدول عملاء). اسم المتجر/عنوانه ثابتان في النصوص.
- النسخة الاحتياطية غير مشفّرة (تحوي هاشات PIN) والمجدولة تُحفظ داخل التطبيق فقط؛ نسخة بإصدار قاعدة مختلف تُرفض (لا ترحيل بين الإصدارات بعد).
- اعتماد الجرد وتعديل المخزون محميان على مستوى القائمة/المسار فقط؛ لا مهلة خمول؛ `changeOwnPin` بلا شاشة.

## مخاطر
- نتائج الاختبارات غير مؤكدة، ولم تُجرَّب الكاميرا/الصور/CSV/واتساب (المفتاح غير الموثّق `jid`)/النسخ والاستعادة على جهاز. الاستعادة تغلق القاعدة وتعيد تشغيل التطبيق: تحتاج تجربة يدوية.
- `gradle-wrapper.jar` غير موجود (البناء يعتمد Gradle 8.9 من الـ workflow).
- PIN المدير المؤقت 1234 يُجبَر تغييره عند أول دخول.
- تعليقات Kotlin تتداخل: لا تكتب «شرطة مائلة ثم نجمة» داخل تعليق (مثل مسار مجلد متبوع بـ نجمة)، فهذا كسر البناء مرتين.
- الكتلة المالية للتقارير: صافي المبيعات = مبيعات مكتملة − `sales_returns.refundAmount`؛ تكلفة المرتجع القابل للبيع = `return_items` بحالة RESELLABLE × `unitCostAtSale` (تُطرح من COGS)؛ خسائر التالف/المنتهي/التسويات السالبة × `unitCost`.

## الاختبارات
مكتوبة لكل وحدة (أكثر من 150) و**لم تُراجَع نتائج تشغيلها**. الخطوة التالية: فتح تقرير `reports` في Actions وإصلاح أي فشل.

## قرارات تقنية ثابتة (لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double؛ الكسور عبر BigDecimal+HALF_UP (`Money`)؛ النسب basis points؛ الضريبة على المبلغ بعد الخصومات؛ كرتونة←قطع عبر `Quantities.cartonsToPieceMilli`؛ أي تقسيم مبلغ على أجزاء يجب أن يساوي المبلغ بالضبط (`Allocation.proportional`).
- المخزون = `inventory_movements` (append-only، موقّع)؛ `products.currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction؛ كل كمية داخلة تنشئ `StockBatch`؛ خروج المخزون دائمًا FEFO.
- `idempotencyKey` فريد: `SALE:{uuid}:{line}:{alloc}`، `OPEN:{productId}`، `PURCHASE:{uuid}:{line}`، `PRETURN:{uuid}:{line}:{alloc}`، `SRETURN:{uuid}:{i}:in|dmg`، `ADJ:{uuid}:{n}`، `WO:{uuid}:{n}`، `CONV:{uuid}:out:{n}|in`. المستندات ذات `uuid` (بيع، شراء، مرتجع مبيعات، مصروف) تمنع تكرار المحاولة؛ `number` عند الإتمام/الاعتماد فقط؛ الشراء ومرتجع المشتريات يتشاركان سلسلة أرقام واحدة. كل شاشة تولّد uuid واحدًا وتعيد استخدامه عند المحاولة.
- التكلفة وقت البيع محفوظة (`unitCostAtSale`, `totalCost`). المدفوعات تُخزَّن صافية، وغير النقد لا يتجاوز الإجمالي.
- المشتريات: `costPrice` = آخر سعر شراء فعلي (بعد الخصومات، لا يُضبط لصفر)؛ المرتجع للمورد بـ FEFO بتكلفة الدفعة بلا سالب؛ رصيد المورد = مجموع `remaining` للمشتريات − مجموع `remaining` للمرتجعات؛ حذف مورد ممنوع إن كان له فواتير.
- مرتجع المبيعات: الفاتورة الأصلية لا تُعدَّل؛ التقارير تطرح المرتجعات؛ `ReturnPreview` يجب أن يبقى مطابقًا لمنطق `ReturnService`.
- المنتهي الصلاحية يُتجاهل في FEFO البيع إلا بـ `allowExpired=true`؛ السالب يتبع `allowNegativeStock`. تاريخ الصلاحية يُخزَّن نهاية اليوم عبر `ExpiryDates`.
- باركود الكرتونة = سجل باركود لنفس المنتج بـ `unitsPerScan=N`؛ `searchManage` يشمل الموقوف، `search` (كاشير) للنشط فقط.
- الكاشير: السلة تُحفظ كفاتورة HELD بنفس `uuid` على كل تغيير؛ `app_settings.current_cart_uuid`؛ قائمة «المعلقة» تستثني الحالية؛ عمليات مُسلسلة بـ Mutex؛ غير المدير لا يعدّل السعر وخصمه ≤ `cashier_max_discount_bp`.
- CSV: أعمدة `name, internal_code, category, unit, cost_price, sale_price, low_stock, pieces_per_carton, barcodes, quantity, expiry, active`؛ `quantity` افتتاحي للجديد فقط.
- لا `fallbackToDestructiveMigration`؛ تغيير مخطط = رفع `version` + Migration (المخطط لم يصدر بعد على الفرع المشترك، ابتداءً من أول نسخة تُنشر أي تغيير يحتاج Migration)؛ حذف المنتج منطقي؛ الألوان من `Brand`/`MaterialTheme` فقط؛ النصوص في strings*.xml؛ PIN مشفّر بـ PBKDF2.
- تنقل تبويب «المزيد» بمسارات نصية في `MoreRoute` (لإضافة شاشة: مسار + عنصر في `MoreMenu` + صلاحية في `requiredPermission` + `parentOf` إن لزم).
