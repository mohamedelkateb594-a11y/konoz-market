# DEVELOPER_C_HANDOFF.md

_Developer C. الفرع الحالي: `claude/developer-c-4-2b` (مبني على `claude/developer-b` @ ddf8a8b). لم أعدّل `DEVELOPER_A_HANDOFF.md` ولا `DEVELOPER_B_HANDOFF.md`. لا يعمل مطور آخر حاليًا (حسب المالك)._

## تنبيه تكرار
كتبت 4.2 على `claude/developer-c` (af23c02) بينما كتب B نفسها على `claude/developer-b`. **تنفيذ B هو المعتمد. لا تُدمج `claude/developer-c`** (مهمل ويتعارض). كل العمل الجديد على `claude/developer-c-4-2b`.

## قرارات المالك (2026-11-13) وتطبيقها
1. **لا مهلة خمول نهائيًا** — لا شيء يُبنى (هذا الوضع الحالي).
2. **البيع يحتاج وردية مفتوحة:** `CashierSession.completeLocked` يرفض الدفع برسالة `CashierMessage.NoOpenShift` (السلة تبقى). المسودة/التعليق مسموحان. نص الرسالة في `strings_shift_gate.xml` (EN/AR) وفرع `NoOpenShift` في `CashierScreen.infoText`. `CashierSessionTest` يفتح وردية في `setUp` وفيه اختباران جديدان (ربط البيع بالوردية، منع البيع بلا وردية).
3. **سداد الموردين يؤثر على الخزينة:**
   - جدول جديد `supplier_payments` (`SupplierPayment`: مبلغ موقّع، موجب = دفعنا للمورد، سالب = استرداد مرتجع)، ليس مصروفًا فلا يدخل في تقارير الأرباح.
   - **قاعدة البيانات صارت الإصدار 2** مع `KonozDatabase.MIGRATION_1_2` المكتوب يدويًا (CREATE TABLE فقط، لا تمس البيانات الموجودة). **خطر:** إن لم يطابق SQL الهجرة ما تولّده Room سيظهر خطأ «Migration didn't properly handle» عند تشغيل تطبيق مثبّت سابقًا على v1 (الاختبارات لا تكشفه لأنها تبني قاعدة في الذاكرة). التحقق اليدوي: تثبيت نسخة v1 ثم تحديث فوقها.
   - `PurchaseService` يسجّل الدفع عند `approve` (لا يكرّر مع نفس uuid) وعند `settle`، مربوطًا بالوردية المفتوحة (لا وردية = `shiftId` null ولا خزينة تتأثر).
   - `ShiftService`/`ShiftSummary`/`ShiftMath`: `expectedCash = opening + cashSales - cashRefunds - cashExpenses - supplierPayments`، وحقل `supplierPayments` في `ShiftSummary` (بقيمة افتراضية 0) والملخص الحي يُعاد حسابه عند أي دفع لمورد.
   - اختبارات: `SupplierPaymentShiftTest` (7).
   - **ناقص:** شاشة الورديات لا تعرض سطر «مدفوع للموردين» بعد (المتوقع يحتسبه، لكن المستخدم لا يرى سبب النقص). ملفا `ShiftScreen.kt` وموردات النصوص يحتاجان سطرًا.

## المنجز على هذا الفرع
- إصلاح `ReportService` (4a4da7e)، ورديات 11.2 (في فرع B).
- 4.2b: `CashierSession` يفرض `EDIT_PRICES`/`GIVE_LARGE_DISCOUNT`؛ صلاحية `MANAGE_PRODUCTS` (المدير ومسؤول المخزون) تخفي تبويب المنتجات وزر تسجيل الباركود الغائب عن الكاشير.
- قرارات المالك أعلاه.
- **كل هذا لم يُبنَ ولم تُختبر اختباراته.**

## ما لم يُنجز
- سطر «مدفوع للموردين» في `ShiftScreen` وتقرير الوردية.
- فرض الصلاحية داخل `StockCountService`/`StockAdjustmentService`.
- 4.3 مشاركة/طباعة الفواتير وتصدير التقارير (PDF/Excel/CSV)، فترة مخصصة في التقارير.
- 4.4 النسخ الاحتياطي (يشمل `receipts` و`product_images` وقاعدة البيانات v2).
- المرحلة 5 (اختبارات/أداء/APK).
- تحديث `PROJECT_STATE.md` و`TASK_LOG.md` و`CLAUDE.md` (عند 8b).

## الخطوة التالية بالضبط
1. بناء `claude/developer-c-4-2b` وتشغيل `./gradlew testDebugUnitTest` وإصلاح الأخطاء.
2. سطر الموردين في ShiftScreen، ثم 4.3.
