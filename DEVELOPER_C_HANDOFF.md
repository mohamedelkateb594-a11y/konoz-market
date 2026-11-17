# DEVELOPER_C_HANDOFF.md

## آخر تحديث (2026-11-18): حل تعارض PR #15 (Developer B) على main الحالي
- **الفرع:** `claude/developer-c-main-ui` (من main @ 2fae67f). لم أعدّل فرع B `claude/developer-b-report-cards` ولا PR #15.
- **سبب التعارض:** PR #15 مبني على main القديم (4c14302). منذ ذلك الوقت دُمج PR #12 (ألوان/وضع ليلي: `MoreRoute` فيه عنصر المظهر) وPR #14 (شريط التقارير: `onOpenReport` في `KonozRoot`، قسم `reports/<id>` في `MoreRoute`، وملف `ReportCardColors.kt` أُضيف في الجهتين).
- **ما فعلته (نتيجة PR #15 كما كتبها B، مع حل التعارض):**
  - `ui/home/HomeScreen.kt` (جديد من B) + صف «المظهر» جديد في `HomeSideMenu` (معاملان اختياريان `themeLabelRes`, `onTheme`) لأن تبديل المظهر كان في قائمة «المزيد» وصار غير قابل للوصول.
  - `ui/KonozRoot.kt`: نسخة B (شاشتان home وcashier بلا شريط سفلي) + `onOpenReport` من PR #14 (يفتح `reports/<sectionId>` داخل الرئيسية) + استدعاء تبديل المظهر.
  - `ui/more/MoreRoute.kt`: main الحالي (المظهر وقسم التقارير) + تعديلات B (`embedded`, `onHome`, أول مسار، `LaunchedEffect`، تفريع `else`).
  - `ui/cashier/CashierScreen.kt`: نسخة B كما هي (عدسة البحث، ترويسة الفاتورة، صفوف بيضاوية)؛ لم يلمسها main فلا تعارض (تحققت من تطابق عدد الأسطر +292/-163 مع PR #15).
  - `res/values{,-ar}/strings_home.xml`: كما كتبها B.
  - **أبقيت** `ui/theme/ReportCardColors.kt` من main (نفس القيم التي يستخدمها B).
- **لم أنسخ:** `DEVELOPER_B_HANDOFF.md` و`LOCK_TASK_UI_HOME.md` من PR #15 (ملفات B)؛ تبقى على فرعه.
- **غير مُختبر:** لا Android SDK هنا؛ المراجعة بالقراءة فقط. البناء الحقيقي يتم عبر CI في PR.
- **تكرار معلن في الواجهة:** `DashboardScreen` (PR #14) ما زال يعرض ترويسته وزر «ابدأ الكاشير» وشريط بطاقات التقارير تحت شريط الرئيسية (B) وترويسته. وظيفيًا يعمل لكنه تكرار بصري؛ حله يحتاج قرار المالك (حذف ترويسة/شريط DashboardScreen أو إبقاءهما) وتعديل `DashboardScreen.kt` (ملك A).
- **تبعيات:** A: `DashboardScreen.kt`/`ReportCardsStrip.kt`. B: PR #15 يصبح مكررًا بعد دمج هذا الفرع (يُغلق).
- **الخطوة التالية بالضبط:** دمج PR بعد نجاح CI، ثم إغلاق PR #15، ثم قرار التكرار أعلاه.

---

_(ما يلي قديم، من جلسات 13/11)_

الفرع القديم: `claude/developer-c-4-2b` (مبني على `claude/developer-b` @ ddf8a8b).

## تنبيه تكرار
كتبت 4.2 على `claude/developer-c` (af23c02) بينما كتب B نفسها على `claude/developer-b`. **تنفيذ B هو المعتمد. لا تُدمج `claude/developer-c`** (مهمل ويتعارض).

## قرارات المالك (2026-11-13) وتطبيقها
1. **لا مهلة خمول نهائيًا.**
2. **البيع يحتاج وردية مفتوحة:** `CashierSession.completeLocked` يرفض الدفع برسالة `CashierMessage.NoOpenShift` (السلة تبقى). المسودة/التعليق مسموحان. `CashierSessionTest` يفتح وردية في `setUp` وفيه اختباران جديدان.
3. **سداد الموردين يؤثر على الخزينة:** جدول `supplier_payments` (`SupplierPayment`)، قاعدة البيانات الإصدار 2 مع `MIGRATION_1_2` يدوي (خطر عدم تطابق SQL مع Room: تحقق يدوي بتثبيت v1 ثم التحديث فوقه)، `PurchaseService` يسجّل الدفع عند `approve` و`settle`، `expectedCash = opening + cashSales - cashRefunds - cashExpenses - supplierPayments`، اختبارات `SupplierPaymentShiftTest` (7). **ناقص:** سطر «مدفوع للموردين» في `ShiftScreen`.

## ما لم يُنجز (قديم)
- سطر «مدفوع للموردين» في `ShiftScreen` وتقرير الوردية.
- فرض الصلاحية داخل `StockCountService`/`StockAdjustmentService`.
- المرحلة 5 (اختبارات/أداء/APK).
- تحديث `PROJECT_STATE.md` و`TASK_LOG.md` و`CLAUDE.md`.
