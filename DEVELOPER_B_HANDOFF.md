# DEVELOPER_B_HANDOFF.md

_Branch: `claude/developer-b` (created from `main` @ eae104f)._

## الحالة
المهمة 9.1 (المصروفات) **مكتملة كتابةً** ومنطق الجرد 9.2 **مكتوب ومختبَر كتابةً** (لم تُبنَ ولم تُشغَّل اختباراتهما). لا توجد مهمة نصف مكتملة.

## قرارات التنسيق (من مالك الملفات المشتركة)
- المطوّر A لا يعمل على المصروفات أو الجرد: تم التأكيد وهذا الفرع تابع له مباشرة.
- **تمت الموافقة على إضافة `uuid` فريد إلى `Expense`** (المخطط لم يصدر بعد فلا Migration). `ExpenseInput.uuid` هو مفتاح التكرار للـ `add` — كل نموذج يولّد واحدًا ويعيد استخدامه.
- بالنظر للملفات المشتركة: عُدّلت `KonozApp.kt` (+`expenseService`, `stockCountService`)، `MoreRoute.kt` (+مسار المصروفات)، `OperationsDaos.kt`، `PurchaseExpenseCountEntities.kt`، `StockAdjustmentService.kt` (معامل `type` اختياري في `adjust`). لم يعدّل A هذه الملفات منذ الفرع، فلا تعارض متوقع عند الدمج.

## مكتمل على هذا الفرع
- 9.1: `domain/{ExpenseService (idempotent add),ExpensePeriods}`، `ui/expenses/{ExpensesViewModel,ExpensesScreen}` (فترات يوم/شهر/سنة، إجماليات، إضافة/تعديل/حذف بتأكيد، صورة إيصال)، `ui/common/ReceiptImages`، `strings_expenses.xml` (AR/EN)، مسار في قائمة المزيد.
- 9.2 (منطق فقط): `domain/StockCountService` (+`CountMath`, `CountException`)، استعلامات `StockCountDao` المضافة.
- اختبارات: `PeriodsTest` 4، `ExpenseServiceTest` 8، `ExpenseIdempotencyTest` 3، `StockCountServiceTest` 11.

## معلّق
- **9.2 الواجهات**: قائمة الجرد (مسودات + سجل تاريخي)، بدء جرد (شامل/تصنيف)، شاشة إدخال الكميات (يدوي + باركود)، مراجعة الفروق مع القيمة، اعتماد بتأكيد. المنطق جاهز ويُستدعى من `app.stockCountService`.
- تمكين الجرد في `MoreRoute`.
- المهمة 10: التقارير/الأرباح/اللوحة الرئيسية.

## ملاحظات
- المصروف: الإجماليات اليومية/الشهرية/السنوية تُحسب بالتاريخ وقت إنشاء الـ ViewModel (لو بقي التطبيق مفتوحًا عبر منتصف الليل تبقى قديمة حتى يعاد فتح الشاشة).
- صورة إيصال مستبدلة أثناء التعديل تبقى ملفاتها القديمة في `filesDir/receipts` (لا تُحذف إلا عند حذف المصروف).
- مجلد `receipts` يجب أن يدخل في النسخ الاحتياطي (المرحلة 4).
- لم يُبنَ المشروع قط (لا Android SDK). توصية: تشغيل workflow البناء على GitHub Actions قبل أي دمج أو كود جديد.

## الخطوة التالية بالضبط
واجهات الجرد 9.2 (`ui/stockcount/*` + `strings_stockcount.xml` + مسار في `MoreRoute`)، ثم المهمة 10.
