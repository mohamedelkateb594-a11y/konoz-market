# DEVELOPER_B_HANDOFF.md

_Branch: `claude/developer-b` (created from `main` @ eae104f). Developer A's files/handoff are not touched._

## المرحلة الحالية
المرحلة 3 — المهمة 9 (المصروفات والجرد). المهمة 9.1 (منطق المصروفات) مكتوبة؛ لم تُبنَ ولم تُختبر.

## ملاحظة تنسيق
لا يوجد `DEVELOPER_A_HANDOFF.md` في الريبو؛ المرجع الوحيد هو `PROJECT_STATE.md` و`TASK_LOG.md` (آخر commit من A على `main`: eae104f "Update build-apk.yml"). افترضتُ أن A لا يعمل على المصروفات. **يرجى التأكيد قبل الدمج.**

## مكتمل (على هذا الفرع)
- **9.1 منطق المصروفات:** `domain/ExpenseService` (add/update/delete داخل Transaction + Audit، ربط الوردية المفتوحة، إجمالي يومي/شهري/سنوي)، `domain/ExpensePeriods` (`Periods.rangeOf` بـ Calendar ليعمل على minSdk 24 بدون desugaring).
- اختبارات: `PeriodsTest` (4)، `ExpenseServiceTest` (8) — **لم تُشغَّل**.

## الملفات
- جديد: `domain/ExpensePeriods.kt`, `domain/ExpenseService.kt`, `test/.../PeriodsTest.kt`, `test/.../ExpenseServiceTest.kt`, `DEVELOPER_B_HANDOFF.md`.
- معدّل (سطر واحد): `data/dao/OperationsDaos.kt` ← إضافة `ExpenseDao.getById(id)`. (ملف مشترك: عند الدمج تأكد من عدم تعارضه مع تعديلات A.)

## معلّق
- 9.1 المتبقي: شاشة المصروفات (UI) + صورة الإيصال (تخزين في `filesDir`) + تمكينها في `MoreRoute` + سلك `expenseService` في `KonozApp` (ملفات A المحتملة: `KonozApp.kt`, `MoreRoute.kt`, strings — لم ألمسها).
- 9.2 `StockCountService` (الجرد) + شاشاته.
- ثم المهمة 10: التقارير/الأرباح/اللوحة الرئيسية.

## مشاكل معروفة / قرارات للمراجعة
- `Expense` بلا `uuid`، فإعادة محاولة `add` تُنشئ مصروفًا مكررًا. الحل المقترح (يتطلب تعديل مخطط `expenses` + إذن): إضافة `uuid` فريد. حتى ذلك يجب أن تعطّل الواجهة زر الحفظ أثناء التنفيذ.
- لم يُبنَ المشروع قط (لا Android SDK) — التوصية السابقة بالبناء في Android Studio/Actions قبل المزيد من الكود ما زالت قائمة. قد يكون `.github/workflows/build-apk.yml` الجديد على `main` كافيًا لذلك.

## الخطوة التالية بالضبط
بعد تأكيد عدم تعارض مع A: شاشة المصروفات `ui/expenses/{ExpensesViewModel,ExpensesScreen}` + نصوص `strings_expenses.xml` (AR/EN) + سلك الخدمة وتمكين المسار، ثم 9.2.
