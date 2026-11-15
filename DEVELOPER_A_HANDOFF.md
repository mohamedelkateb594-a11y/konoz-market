# DEVELOPER_A_HANDOFF.md

_Branch: `claude/developer-b` (الفرع المشترك الحالي). هذا الملف يخص المطوّر A (المسؤول عن المعمارية والملفات المشتركة)._

## ملكية المهام (LOCK)
- **المهمة 4.3 (مشاركة وطباعة الفواتير): مملوكة للمطوّر A** منذ هذا الـ commit. لا يبدأها B أو C.
- الملفات التي ستُلمس في 4.3 (لا تعدّلوها بالتوازي):
  - جديد: `domain/InvoiceDocument.kt`، `domain/EscPos.kt`، `ui/receipt/*` (رسم الإيصال، تصدير PNG/PDF، مشاركة، طباعة Bluetooth، شاشة الفواتير والتفاصيل)، `res/xml/file_paths.xml`، `strings_receipt.xml`.
  - مشتركة: `AndroidManifest.xml` (FileProvider)، `KonozApp.kt`، `ui/more/MoreRoute.kt`، `ui/cashier/CashierScreen.kt` (أزرار مشاركة/طباعة في نافذة نجاح البيع).
- بعد 4.3: تصدير التقارير PDF/CSV ثم النسخ الاحتياطي (4.4).

## ما أنجزه A على هذا الفرع (مرتب)
1. **المهمة 9.1 (المصروفات) — إكمال عمل B:**
   - أُضيف `uuid` فريد إلى `Expense` (المخطط لم يصدر بعد فلا Migration). `ExpenseInput.uuid` يمنع تكرار الإضافة عند إعادة المحاولة.
   - `ui/expenses/{ExpensesViewModel,ExpensesScreen}`: فترات يوم/شهر/سنة، إجماليات، إضافة/تعديل/حذف بتأكيد، صورة إيصال (`ui/common/ReceiptImages`).
   - `strings_expenses.xml` (AR/EN)، `ExpenseIdempotencyTest` (3).
2. **المهمة 9.2 (الجرد):**
   - `domain/StockCountService` (+`CountMath`, `CountException`): بدء جرد (شامل/تصنيف) يلتقط الكمية والتكلفة، إدخال الكميات يدويًا أو بالباركود، **الاعتماد وحده يعدّل المخزون** عبر `COUNT_ADJUSTMENT`، يطبّق الفرق الذي وجده الجرد على المخزون الحالي (مبيعات أثناء الجرد محفوظة)، لا يهبط المخزون تحت الصفر، منع اعتماد/تعديل مكرر، تسجيل المعتمِد والتاريخ.
   - `StockAdjustmentService.adjust` أخذ معامل `type` اختياريًا (ADJUSTMENT أو COUNT_ADJUSTMENT).
   - واجهات: `ui/stockcount/{StockCountListViewModel,StockCountListScreen,StockCountViewModel,StockCountScreen}` + `strings_stockcount.xml` + مساران في `MoreRoute` (`counts`, `count/{id}`).
   - اختبارات: `StockCountServiceTest` (11).
3. قبل ذلك على `main` (المهام 1–8): الهوية، قاعدة Room (20 جدول)، المنتجات، الكاشير والماسح، المشتريات والموردون، المرتجعات، التسويات، التنبيهات، CSV. تفاصيلها في `PROJECT_STATE.md` و`TASK_LOG.md` على main.

## ما أنجزه B (مرجعه `DEVELOPER_B_HANDOFF.md`)
التقارير (10.1 وما بعده)، الورديات والخزينة، المستخدمون وتسجيل الدخول والصلاحيات (4.2 و4.2b). **اقرأ ملف B قبل أي تعديل في هذه الأجزاء.**

## قواعد ثابتة (من `PROJECT_STATE.md`، لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double. تقسيم أي مبلغ على أجزاء عبر `Allocation.proportional`.
- المخزون = `inventory_movements` (append-only)؛ `currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction؛ الخروج دائمًا FEFO.
- كل مستند له `uuid` لمنع التكرار؛ الأرقام المتسلسلة تُمنح عند الاعتماد فقط.
- لا `fallbackToDestructiveMigration`؛ أي تغيير مخطط بعد أول نسخة منشورة = رفع `version` + Migration.

## الحالة والمخاطر
- **لم يُبنَ المشروع قط** (لا Android SDK في بيئة Claude). 13+ مهمة مكتوبة بلا بناء. أول خطوة قبل أي دمج: تشغيل workflow البناء على هذا الفرع (Actions) وإرسال سطور الأخطاء (`e:` / `error:`) للإصلاح.
- الفرع لم يُدمج في `main`. الدمج بعد نجاح البناء (Pull Request).
- نواقص معروفة: خط Cairo غير مضمّن، النسخ الاحتياطي لم يبدأ (يجب أن يشمل مجلدي `product_images` و`receipts`)، لا اختبارات واجهة، `gradle-wrapper.jar` غير موجود.

## الخطوة التالية بالضبط
المهمة 4.3 (A): رسم الإيصال كـ Bitmap (يدعم العربية) ← مشاركة PNG/PDF عبر FileProvider ← طباعة ESC/POS (58/80mm) عبر Bluetooth كصورة Raster ← شاشة الفواتير + أزرار في نافذة نجاح البيع ← اختبارات `EscPos` و`InvoiceService`.
