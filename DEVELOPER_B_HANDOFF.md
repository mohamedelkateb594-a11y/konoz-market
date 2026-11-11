# DEVELOPER_B_HANDOFF.md

_Branch: `claude/developer-b` (created from `main` @ eae104f)._

## الحالة
المهمة 9 (المصروفات والجرد) **مكتملة كتابةً** (منطق + واجهات). لم تُبنَ ولم تُشغَّل أي اختبارات. لا توجد مهمة نصف مكتملة.

## قرارات التنسيق (من مالك الملفات المشتركة)
- المطوّر A لا يعمل على المصروفات أو الجرد: تم التأكيد وهذا الفرع تابع له مباشرة.
- **تمت الموافقة على إضافة `uuid` فريد إلى `Expense`** (المخطط لم يصدر بعد فلا Migration). `ExpenseInput.uuid` هو مفتاح التكرار للـ `add` — كل نموذج يولّد واحدًا ويعيد استخدامه.
- ملفات مشتركة عُدّلت على هذا الفرع: `KonozApp.kt` (+`expenseService`, `stockCountService`)، `MoreRoute.kt` (+مسارا المصروفات والجرد)، `OperationsDaos.kt`، `PurchaseExpenseCountEntities.kt`، `StockAdjustmentService.kt` (معامل `type` اختياري في `adjust`). لا تعارض متوقع عند الدمج ما لم يعدّل A هذه الملفات على `main`.

## مكتمل على هذا الفرع
- 9.1 المصروفات: `domain/{ExpenseService (idempotent add),ExpensePeriods}`، `ui/expenses/{ExpensesViewModel,ExpensesScreen}`، `ui/common/ReceiptImages`، `strings_expenses.xml` (AR/EN)، مسار في المزيد.
- 9.2 الجرد (منطق): `domain/StockCountService` (+`CountMath`, `CountException`)، استعلامات `StockCountDao`.
- 9.2 الجرد (واجهات، commit 5893044): `ui/stockcount/{StockCountListViewModel,StockCountListScreen,StockCountViewModel,StockCountScreen}` (قائمة مسودات/سجل، بدء جرد شامل أو بتصنيف، إدخال يدوي + باركود، ملخص عجز/زيادة بالتكلفة، اعتماد بتأكيد، حذف مسودة)، `strings_stockcount.xml` (AR/EN)، مسار في `MoreRoute`. حقل الكمية يحتفظ بما يكتبه المستخدم ولا يُعاد ضبطه أثناء الكتابة (يتغير نصه فقط عند تغيير خارجي مثل مسح باركود).
- مراجعة يدوية لـ `StockCountScreen` (لا بناء): موارد النصوص المستخدمة موجودة (`done`, `delete`, `cancel`, `ok`, `scan_barcode`...)، توقيع `CameraBarcodeScanner(enabled, onBarcode, modifier)` مطابق، و`Quantities.parseMilli` و`formatQty`/`unitLabelRes` موجودة.
- اختبارات مكتوبة: `PeriodsTest` 4، `ExpenseServiceTest` 8، `ExpenseIdempotencyTest` 3، `StockCountServiceTest` 11 — **لم تُشغَّل**.

## معلّق
- **المهمة 10**: التقارير والأرباح واللوحة الرئيسية (اللوحة الحالية placeholder).
- `PROJECT_STATE.md` و`TASK_LOG.md` ما زالا على نقطة المهمة 8b — لم أعدّلهما لأنهما مرجع مشترك مع A؛ يُحدَّثان عند الدمج (أو أخبرني لأحدّثهما على هذا الفرع).

## ملاحظات
- المصروف: الإجماليات اليومية/الشهرية/السنوية تُحسب بالتاريخ وقت إنشاء الـ ViewModel (تبقى قديمة إن بقي التطبيق مفتوحًا عبر منتصف الليل حتى يعاد فتح الشاشة).
- صورة إيصال مستبدلة أثناء التعديل تبقى ملفاتها القديمة في `filesDir/receipts` (لا تُحذف إلا عند حذف المصروف).
- مجلد `receipts` وصور المنتجات يجب أن يدخلا في النسخ الاحتياطي (المرحلة 4).
- لم يُبنَ المشروع قط (لا Android SDK هنا). توصية قوية: تشغيل workflow البناء على GitHub Actions على هذا الفرع وإرسال الأخطاء قبل أي كود جديد — 12 مهمة الآن بلا بناء واحد.

## الخطوة التالية بالضبط
بعد نتيجة البناء: المهمة 10.1 `domain/ReportService` (حسب الكتلة المالية في `PROJECT_STATE.md`: صافي المبيعات = مبيعات مكتملة − `sales_returns.refundAmount`، COGS يطرح تكلفة المرتجع القابل للبيع، خسائر التالف/المنتهي/التسويات السالبة × `unitCost`، المصروفات من `expenses`) مع اختبارات، ثم 10.2 شاشات التقارير واللوحة الرئيسية.
