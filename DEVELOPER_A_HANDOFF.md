# DEVELOPER_A_HANDOFF.md

_Branch: `claude/developer-b` (الفرع المشترك الحالي). هذا الملف يخص المطوّر A (المسؤول عن المعمارية والملفات المشتركة)._

## آخر تحديث (2026-11-15): المهمة 4.6 مفتاح اللغة (A) - على `claude/developer-a-language`
- **المطلوب:** مفتاح تبديل عربي/English داخل التطبيق. نصوص AR/EN كانت موجودة لكل الوحدات (15 ملف في `values-ar`) و`supportsRtl=true`؛ الناقص كان الاختيار داخل التطبيق فقط.
- **المنفّذ:** `ui/language/AppLanguage.kt` (يحفظ الاختيار في SharedPreferences `konoz_ui_prefs`، `wrap` يطبّق اللغة والاتجاه على الـActivity، `applyToApplication` للخدمات، `switch` يبدّل ويعيد إنشاء الشاشة). `MainActivity` يستدعي `attachBaseContext` و`applyToApplication`. عنصر «اللغة» في قائمة «المزيد» لكل مستخدم مسجّل (بلا صلاحية). نصوص `strings_language.xml` (AR/EN).
- **سلوك:** بلا اختيار محفوظ يتبع لغة الهاتف كما كان. القرار الافتراضي (عربي دائمًا؟) متروك للمالك.
- **غير مُختبر:** لم يُبنَ ولم يُجرَّب (لا SDK هنا). المتوقع تجربة يدوية: التبديل يعيد رسم الشاشة بالاتجاه الصحيح، ويبقى المستخدم مسجّلًا. الإيصالات المرسومة بعد التبديل تتبع لغة التطبيق؛ WorkManager (النسخ اليومي) لا يتأثر.
- **ملكية:** `LOCK_TASK_4_6_LANGUAGE.md`؛ الملفان المشتركان المعدّلان: `MainActivity.kt` و`MoreRoute.kt` (حقن سطرين). يُحذف القفل عند الدمج.
- **متبقٍ (اختياري):** تضمين خط Cairo (ما زال غير مضمّن)، مراجعة تنسيق RTL على جهاز.

## ملكية المهام (LOCK)
- **المهمة 4.3 (مشاركة وطباعة الفواتير): مملوكة للمطوّر A** منذ هذا الـ commit. لا يبدأها B أو C.
- ملاحظة لاحقة: B أنجز نواة 4.3 (رسم/مشاركة/واتساب/شاشة الفواتير/زر الكاشير). المتبقي لـA: طباعة ESC/POS بلوتوث وتصدير التقارير (انظر `LOCK_TASK_4_3_INVOICES.md`). PR #4 (C) مسودة تتداخل مع الطباعة بانتظار قرار المالك.
- الملفات التي ستُلمس في 4.3 (لا تعدّلوها بالتوازي):
  - جديد: `domain/InvoiceDocument.kt`، `domain/EscPos.kt`، `ui/receipt/*` (رسم الإيصال، تصدير PNG/PDF، مشاركة، طباعة Bluetooth، شاشة الفواتير والتفاصيل)، `res/xml/file_paths.xml`، `strings_receipt.xml`.
  - مشتركة: `AndroidManifest.xml` (FileProvider)، `KonozApp.kt`، `ui/more/MoreRoute.kt`، `ui/cashier/CashierScreen.kt` (أزرار مشاركة/طباعة في نافذة نجاح البيع).
- بعد 4.3: تصدير التقارير PDF/CSV ثم النسخ الاحتياطي (4.4، أنجزه B).

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
4. **المهمة 4.6 (مفتاح اللغة):** انظر أعلى الملف.

## ما أنجزه B (مرجعه `DEVELOPER_B_HANDOFF.md`)
التقارير (10.1 وما بعده)، الورديات والخزينة، المستخدمون وتسجيل الدخول والصلاحيات (4.2 و4.2b)، فاتورة PDF/صورة وواتساب (4.3)، النسخ الاحتياطي والاستعادة (4.4). **اقرأ ملف B قبل أي تعديل في هذه الأجزاء.**

## قواعد ثابتة (من `PROJECT_STATE.md`، لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double. تقسيم أي مبلغ على أجزاء عبر `Allocation.proportional`.
- المخزون = `inventory_movements` (append-only)؛ `currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction؛ الخروج دائمًا FEFO.
- كل مستند له `uuid` لمنع التكرار؛ الأرقام المتسلسلة تُمنح عند الاعتماد فقط.
- لا `fallbackToDestructiveMigration`؛ أي تغيير مخطط بعد أول نسخة منشورة = رفع `version` + Migration.
- لا تكتب «شرطة مائلة ثم نجمة» داخل تعليق Kotlin (كسر البناء مرتين).

## الحالة والمخاطر
- البناء: نجح `assembleDebug` سابقًا على الفرع المشترك؛ الاختبارات لم تُراجَع نتائجها. بناء `claude/developer-a-language` لم يُشغَّل بعد: شغّل workflow البناء وأرسل سطور الأخطاء (`e:` / `error:`).
- نواقص معروفة: خط Cairo غير مضمّن، لا اختبارات واجهة، `gradle-wrapper.jar` غير موجود.

## الخطوة التالية بالضبط
1. تشغيل البناء على `claude/developer-a-language` (Actions) وإصلاح أي خطأ، ثم تجربة المفتاح على جهاز، ثم PR إلى `claude/developer-b` بعد المراجعة.
2. A: طباعة ESC/POS بلوتوث وتصدير التقارير (بعد قرار المالك في PR #4).
3. B/C: المرحلة 5 (إصلاح أخطاء البناء ثم الاختبارات) ولا مهمة جديدة قبل تسجيل ملكيتها.
