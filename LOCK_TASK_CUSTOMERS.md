# LOCK: قائمة العملاء + اسم العميل في الفاتورة (Developer B)

_سُجّل بتاريخ 2026-11-19 قبل بدء الكتابة. المالك: B._

## طلب المالك
عند إضافة رقم العميل في الفاتورة يُضاف معه **اسم العميل**، ويُحفظ الرقم بالاسم في **قائمة العملاء** (قائمة جديدة، عناصرها بشريط تمرير)، ويُسجَّل اسم العميل في الفاتورة المرسلة له.

## ملفات جديدة (مملوكة لـ B)
- `data/entity/CustomerEntity.kt`، `data/dao/CustomerDao.kt`
- `domain/CustomerService.kt`
- `ui/customers/CustomersViewModel.kt`، `ui/customers/CustomersScreen.kt`
- `ui/common/Scrollbar.kt`
- `res/values{,-ar}/strings_customers.xml`
- `test/.../domain/CustomerServiceTest.kt`

## ملفات مشتركة ستُعدَّل (تعديل محدود فقط)
- `data/KonozDatabase.kt`: **الإصدار 3** + `MIGRATION_2_3` + كيان وDAO العملاء. **لا يغيّر A أو C إصدار القاعدة بالتوازي.**
- `data/entity/StockAndSalesEntities.kt`: عمودان اختياريان في `SalesInvoice` (`customerName`, `customerPhone`).
- `domain/InvoiceData.kt`: حقلان اختياريان في آخر `InvoiceData` وقراءتهما في `InvoiceLoader`.
- `ui/receipt/InvoiceRenderer.kt`: سطر «العميل» داخل بطاقة الرأس فقط.
- `ui/receipt/InvoiceSharer.kt`: `caption` فقط (تحية باسم العميل).
- `ui/receipt/InvoicesViewModel.kt` و`InvoicesScreen.kt`: حقل الاسم وحفظ العميل قبل الإرسال.
- `ui/more/MoreRoute.kt`: مسار `customers` وعنصر قائمة (بلا صلاحية خاصة: أي مستخدم مسجّل) + `MoreTileGrid.kt`: أيقونة.

## ملاحظة دمج لـ A وC
عند تعارض `MoreRoute.kt`/`MoreTileGrid.kt` خذ نسختكم وأعيدوا فقط: ثابت `MoreTargets.CUSTOMERS`، فرع `CUSTOMERS` في `when`، عنصر القائمة `menu_customers`، وسطر الأيقونة. عند تعارض `KonozDatabase.kt` أبقوا `version = 3` و`MIGRATION_2_3` وأضيفوا أي هجرة لاحقة بعدها.

يُحذف هذا الملف عند الدمج.
