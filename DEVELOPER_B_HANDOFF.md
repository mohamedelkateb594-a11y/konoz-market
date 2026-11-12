# DEVELOPER_B_HANDOFF.md

_Branch: `claude/developer-b` (created from `main` @ eae104f). Nothing new from Developer A on `main` since then._

## الحالة
المهمة 9 (المصروفات والجرد) مكتملة. المهمة 10.1 و10.2 (منطق التقارير) مكتوبتان. لم يُبنَ المشروع ولم تُشغَّل أي اختبارات. لا توجد مهمة نصف مكتملة.

## قرارات التنسيق
- المطوّر A لا يعمل على المصروفات أو الجرد؛ هذا الفرع تابع للمالك مباشرة.
- تمت الموافقة على إضافة `uuid` فريد إلى `Expense`.
- ملفات مشتركة عُدّلت: `KonozApp.kt`، `MoreRoute.kt`، `OperationsDaos.kt`، `PurchaseExpenseCountEntities.kt`، `StockAdjustmentService.kt`، `KonozDatabase.kt` (+`reports()`).

## مكتمل على هذا الفرع
- 9.1 المصروفات، 9.2 الجرد (منطق + واجهات).
- **10.1** `ReportService`: `summary(range|kind)`، `compare(kind)`، `dailySeries(days)` + `ReportServiceTest` (6).
- **10.2** `ReportService` (نفس الملف) + `ReportDao`:
  - `productPerformance`, `topByQuantity`, `topByProfit`, `leastSold`, `nonMoving`.
  - `salesByCategory`, `expensesByCategory`.
  - `peakHours` (24 ساعة)، `weekdayComparison` (إجمالي ومتوسط لكل يوم أسبوع، المتوسط يقسم على أيام الأسبوع التي مرّت حتى الآن).
  - اختبارات `ReportBreakdownTest` (7). **لم تُشغَّل.**
- المنخفضة المخزون والقريبة من الانتهاء والفترة المخصصة: متاحة دون كود جديد (`ProductDao.observeLowStock`, `StockDao.observeExpiringSoon`, و`summary(PeriodRange)`).

## قواعد الحساب المعتمدة (للمراجعة)
- صافي المبيعات = إجمالي الفواتير المكتملة − المرتجعات (بتاريخ المرتجع).
- COGS = `totalCost` المحفوظ − تكلفة المرتجع القابل لإعادة البيع. مرتجع التالف يبقى في COGS.
- خسائر المخزون = DAMAGED/EXPIRED/ADJUSTMENT/COUNT_ADJUSTMENT السالبة بـ`unitCost`، دون حركات `SALES_RETURN`؛ لا تُخصم منها الزيادات.
- صافي الربح التقديري = مجمل الربح − المصروفات − خسائر المخزون.
- **ربح المنتج/التصنيف:** الإيراد = `lineTotal` (بعد خصم الصنف، قبل خصم الفاتورة والضريبة) − المبالغ المستردة؛ لذلك مجموع أرباح المنتجات قد يزيد قليلًا عن مجمل الربح في `summary` عند وجود خصم على مستوى الفاتورة.

## أسئلة مفتوحة (بلا رد حتى الآن)
- **الضريبة:** `total` يشمل الضريبة فيتضخم الربح إذا فُعّلت. هل تُستبعد من الإيراد (يلزم تقسيم ضريبة المرتجعات بالتناسب)؟
- هل تدخل خسائر المخزون في صافي الربح؟ (افتراضيًا نعم.)

## معلّق
- **10.3 الواجهات:** مركز التقارير، اللوحة الرئيسية (بدل placeholder)، سلك `reportService` في `KonozApp`.
- التصدير PDF/Excel/CSV والمشاركة (المرحلة 4).
- `PROJECT_STATE.md` و`TASK_LOG.md` ما زالا عند 8b — يُحدَّثان عند الدمج.

## ملاحظات
- النسخ الاحتياطي (المرحلة 4) يجب أن يشمل مجلد `receipts` وصور المنتجات.
- `ReportServiceTest.seed()` فيه سطر زائد `service.let { }` (تجميلي).
- لم يُبنَ المشروع قط (لا Android SDK هنا). 14 مهمة بلا بناء واحد: شغّل workflow البناء على هذا الفرع وأرسل الأخطاء قبل 10.3.

## الخطوة التالية بالضبط
بعد نتيجة البناء ورد المالك على الأسئلة: 10.3 — `ui/reports/{ReportsViewModel,ReportsScreen}` (ملخص يومي/شهري/سنوي/مخصص، مقارنة، منتجات، تصنيفات، مصروفات، ساعات الذروة، أيام الأسبوع) ثم اللوحة الرئيسية، ونصوص AR/EN في `strings_reports.xml`.
