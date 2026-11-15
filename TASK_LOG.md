# TASK_LOG.md

| # | الوصف | الملفات | النتيجة | الاختبارات | أخطاء محلولة |
|---|-------|---------|---------|-----------|--------------|
| 0 | إنشاء ريبو GitHub | - | تم يدويًا من المستخدم | - | 403 على create_repository |
| 1 | هيكل + Gradle + هوية + تنقل | Gradle، Manifest، strings، `ui/theme/*`، `KonozRoot` | مرفوع (d21760d) | لم يُبنَ | - |
| 1b | workflow لبناء APK | `.github/workflows/build-apk.yml` | **فشل** (403) ثم نُقل يدويًا من المالك وعمل | - | - |
| 2 | Room (20 جدول) + DAOs + `Money` | `data/entity/*`, `data/dao/*`, `KonozDatabase`, `Money`, `MoneyTest` | مرفوع (f48c5b2) | لم تُشغَّل | - |
| 2b | ملفات الذاكرة | `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md` | مرفوع (e248341) | - | - |
| 3 | منطق البيع + seed + PinHasher + Robolectric | `domain/{SaleModels,SaleCalculator,SaleService}`, `DatabaseInitializer`, `PinHasher`, tests | مرفوع (417c649) | 16، لم تُشغَّل | - |
| 4 | إدارة المنتجات | `ProductRepository`, `Quantities`, `CatalogDaos`, `ui/products/*`, strings, tests | مرفوع (c0f812a) | 12، لم تُشغَّل | إزالة Flow داخل Transaction في seed |
| 5 | الكاشير + ماسح الباركود | `domain/{Cart,CashierSession}`, `ui/scanner/*`, `ui/cashier/*`, strings_cashier, tests | مرفوع | 25، لم تُشغَّل | - |
| 6 | إكمال نواقص المرحلة 1 | `core/{ExpiryDates,Percent,Csv}`, `ProductCsvService`, `ui/common/*`, `ui/products/*`, tests | مرفوع (5419e8e + 04d102a) | 15، لم تُشغَّل | تجميع `ProductCsvServiceTest` |
| 7a | منطق المشتريات | `domain/Purchase*`, `OperationsDaos`, tests | مرفوع (f2a4dba) | 15، لم تُشغَّل | - |
| 7b | شاشات الموردين والمشتريات + تمكين المزيد | `CatalogRepository`, `PurchaseEntry`, `SupplierBalances`, `ui/suppliers/*`, `ui/purchases/*`, `ui/more/*`, tests | مرفوع (c49deea) | 10، لم تُشغَّل | فشل دفعة أولى بسبب JSON غير صالح؛ أُعيدت |
| 8a | منطق مرتجعات المبيعات + تسويات المخزون | `domain/{ReturnModels,ReturnService,StockAdjustmentService}`, `core/Allocation`, tests | مرفوع (65e2661 + 28af3e0) | 24، لم تُشغَّل | ملف مؤقت زائد دُفع ثم حُذف (e7c7072) |
| 8b | شاشات مرتجع/تسويات/حركة/تنبيهات | `domain/ReturnPreview`, `ui/{returns,stockops,movements,alerts}/*`, `strings_inventory.xml` | مرفوع (f7246f3) + إصلاح | 4، لم تُشغَّل | استيراد `items` ناقص في `StockOpsScreen` |
| 9 | المصروفات + الجرد (منطق + شاشات) | `domain/{ExpenseService,ExpensePeriods,StockCountService}`, `ui/{expenses,stockcount}/*`, `Expense.uuid` | مدمج في main (PR #1) | 26، لم تُشغَّل | - |
| 10 | التقارير والأرباح واللوحة الرئيسية | `domain/ReportService`, `ui/reports/*`, `ui/dashboard/*` | على `claude/developer-b` (غير مدمج) | لم تُشغَّل | إصلاح `ReportService:245` (C، 4a4da7e)، البناء نجح حتى 11.1 |
| 11 | الورديات والخزينة (11.1 B، 11.2 C) | `domain/ShiftService`, `ui/shifts/*`, tests | على `claude/developer-b` | لم تُشغَّل | تضارب ملكية مع B في 2026-11-13 تم التراجع عنه (8f59c5d) |
| 4.2 / 4.2b | المستخدمون والدخول والصلاحيات (تنفيذ B هو المعتمد) | `domain/{Permissions,AuthSession,UserService}`, `ui/{auth,users}/*` | على `claude/developer-b` | لم تُشغَّل | نسخة C المكررة (`claude/developer-c`) مهملة |
| 4.3 / 4.3b | فاتورة PDF/صورة + واتساب برقم العميل + زر الكاشير + تنبيه السكان (B) | `domain/{PhoneNumbers,InvoiceData}`, `ui/receipt/*`, FileProvider | على `claude/developer-b` | لم تُشغَّل | - |
| 4.4 | النسخ الاحتياطي والاستعادة (B هو المعتمد) | `backup/*`, `ui/backup/*`, `strings_backup.xml` | على `claude/developer-b` | `BackupServiceTest` 11، لم تُشغَّل | تعليق KDoc فيه علامة نجمة بعد شرطة مائلة فتح تعليقًا متداخلًا غير مغلق فكسر البناء (أُصلح: 6ca67b5) |
| 4.5 | طباعة حرارية بلوتوث 58/80 مم | — | **مسجلة لـ A ولم تُدمج**. نسخة C مسودة في PR #4 وتتعارض في `EscPos.kt` — بانتظار قرار المالك | `EscPosTest` 5 (نسخة C) | - |
