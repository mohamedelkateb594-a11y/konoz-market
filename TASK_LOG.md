# TASK_LOG.md

| # | الوصف | الملفات | النتيجة | الاختبارات | أخطاء محلولة |
|---|-------|---------|---------|-----------|--------------|
| 0 | إنشاء ريبو GitHub | - | تم يدويًا من المستخدم | - | 403 على create_repository |
| 1 | هيكل + Gradle + هوية + تنقل | Gradle، Manifest، strings، `ui/theme/*`، `KonozRoot` | مرفوع (d21760d) | لم يُبنَ | - |
| 1b | workflow لبناء APK | `.github/workflows/build-apk.yml` | **فشل** (403) | - | - |
| 2 | Room (20 جدول) + DAOs + `Money` | `data/entity/*`, `data/dao/*`, `KonozDatabase`, `Money`, `MoneyTest` | مرفوع (f48c5b2) | لم تُشغَّل | - |
| 2b | ملفات الذاكرة | `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md` | مرفوع (e248341) | - | - |
| 3 | منطق البيع + seed + PinHasher + Robolectric | `domain/{SaleModels,SaleCalculator,SaleService}`, `DatabaseInitializer`, `PinHasher`, tests | مرفوع (417c649) | 16، لم تُشغَّل | - |
| 4 | إدارة المنتجات | `ProductRepository`, `Quantities`, `CatalogDaos`, `ui/products/*`, strings, tests | مرفوع (c0f812a) | 12، لم تُشغَّل | إزالة Flow داخل Transaction في seed |
| 5 | الكاشير + ماسح الباركود | `domain/{Cart,CashierSession}`, `ui/scanner/*`, `ui/cashier/*`, strings_cashier, tests | مرفوع | 25، لم تُشغَّل | - |
| 6 | إكمال نواقص المرحلة 1 | `core/{ExpiryDates,Percent,Csv}`, `ProductCsvService`, `ui/common/*`, `ui/products/*`, tests | مرفوع (5419e8e + 04d102a) | 15، لم تُشغَّل | تجميع `ProductCsvServiceTest` |
| 7a | منطق المشتريات | `domain/Purchase*`, `OperationsDaos`, tests | مرفوع (f2a4dba) | 15، لم تُشغَّل | - |
| 7b | شاشات الموردين والمشتريات + تمكين المزيد | `CatalogRepository`, `PurchaseEntry`, `SupplierBalances`, `ui/suppliers/*`, `ui/purchases/*`, `ui/more/*`, tests | مرفوع (c49deea) | 10، لم تُشغَّل | فشل دفعة أولى بسبب JSON غير صالح؛ أُعيدت |
| 8a | منطق مرتجعات المبيعات + تسويات المخزون | `domain/{ReturnModels,ReturnService,StockAdjustmentService}`, `core/Allocation`, `StockAndSalesEntities` (+`SalesReturn.uuid`), `StockSalesDaos` (+استعلامات مرتجعات), `KonozApp`, tests | مرفوع (65e2661 + 28af3e0) | 24 (Allocation 5، ReturnService 10، StockAdjustment 9)، لم تُشغَّل | ملف مؤقت زائد دُفع خطأً ثم حُذف (e7c7072) |
| 8b | شاشات: مرتجع مبيعات، تسويات، سجل حركة، تنبيهات + قائمة المزيد | `domain/ReturnPreview`, `ui/common/ProductSearch`, `ui/{returns,stockops,movements,alerts}/*`, `ui/more/MoreRoute`, `strings_inventory.xml`, `ReturnPreviewTest` | مرفوع (f7246f3) + إصلاح | 4، لم تُشغَّل | استيراد `items` ناقص في `StockOpsScreen` اكتُشف أثناء حفظ نقطة التوقف فأُصلح |
