# DEVELOPER_C_HANDOFF.md

## آخر تحديث (2026-11-19): مراجعة دمج «قائمة العملاء» (عمل B منقول فوق main بواسطة A)
**الفرع:** `claude/developer-c-customers` (من `claude/developer-a-customers-on-main` @ 69c5b63؛ لم أعدّل فرع A). main = 5518269 وهو سلف الفرع (لا تعارض).

### ما فُحص (قراءة يدوية، لا بناء)
- كل ملفات البند 1 من ملاحظة A موجودة بالفعل على فرع A: `Scrollbar.kt`، `CustomersScreen/ViewModel`، `strings_customers.xml` (EN/AR)، `CustomerServiceTest`، مسار `CUSTOMERS` في `MoreRoute`، بطاقة «العملاء» في `MoreTileGrid` والرئيسية.
- `op_failed` استُبدل بـ `cust_err_failed` في شاشة العملاء. (`op_failed` نفسه موجود في main ويستخدمه المنتجات والموردون، لا مشكلة.)
- `ShareMessage.NAME_REQUIRED`: الـ`when` الوحيد عليه في `InvoicesScreen` ويغطي كل القيم. `CashierRoute/CashierScreen/CashierViewModel` لا يستخدمان `ShareMessage` ويستدعيان `InvoiceShareDialog` بنفس التوقيع. لا خطأ.
- `KonozDatabase`: version=3، `MIGRATION_2_3` مسجّلة في `addMigrations`، كيان `Customer` مضاف، عمودا `customerName/customerPhone` في `SalesInvoice`.
- لا أسماء نصوص مكررة بين `strings_customers.xml` والملفات الأخرى.

### خطأ ترجمة وُجد وأُصلح
- `HomeScreen.kt` يستخدم `R.string.hc_customers_desc` وهو غير معرّف. أضيف في `values/strings_home.xml` و`values-ar/strings_home.xml` (commit e600f21 على هذا الفرع).

### غير مُتحقَّق
- لم يُشغَّل بناء ولا اختبارات (لا شبكة/Gradle هنا). يجب تشغيل `build-apk.yml` على PR هذا الفرع وإرسال أي `e:`.
- هجرة 2→3 تحتاج تجربة يدوية: تثبيت نسخة v2 ثم التحديث فوقها.
- لم أحدّث `PROJECT_STATE.md` و`TASK_LOG.md` و`DEVELOPER_A_HANDOFF.md` ولم أحذف ملفات LOCK (تُفعل عند الدمج النهائي).

### الخطوة التالية بالضبط
1. PR من `claude/developer-c-customers` إلى `main`، وانتظار نتيجة البناء في Actions.
2. إصلاح أي أخطاء `e:` تظهر، ثم الدمج.
3. بعد الدمج: تحديث `PROJECT_STATE.md` و`TASK_LOG.md`، وحذف `LOCK_TASK_*.md` المنتهية، وتجربة هجرة v2→v3 على جهاز.

---

## سجل سابق (2026-11-13): فرع `claude/developer-c-4-2b`
_(مبني على `claude/developer-b` @ ddf8a8b؛ ما بعده دُمج لاحقًا في main حسب PROJECT_STATE.md، راجعه قبل الاعتماد على القوائم التالية.)_

- **تنبيه:** `claude/developer-c` (af23c02) مهمل ولا يُدمج؛ تنفيذ B للمهمة 4.2 هو المعتمد.
- قرارات المالك: لا مهلة خمول؛ البيع يحتاج وردية مفتوحة (`CashierMessage.NoOpenShift`)؛ سداد الموردين يؤثر على الخزينة (`supplier_payments`، `MIGRATION_1_2`، `expectedCash = opening + cashSales - cashRefunds - cashExpenses - supplierPayments`).
- 4.2b: فرض `EDIT_PRICES`/`GIVE_LARGE_DISCOUNT` في `CashierSession`، وصلاحية `MANAGE_PRODUCTS`.
- ناقص وقتها: سطر «مدفوع للموردين» في `ShiftScreen`، فرض الصلاحية في `StockCountService`/`StockAdjustmentService`، المرحلة 5.
