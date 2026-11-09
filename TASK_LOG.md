# TASK_LOG.md

| # | الوصف | الملفات | النتيجة | الاختبارات | أخطاء محلولة |
|---|-------|---------|---------|-----------|--------------|
| 0 | إنشاء ريبو GitHub | - | تم يدويًا من المستخدم | - | 403 على create_repository |
| 1 | هيكل + Gradle + هوية + تنقل | Gradle، Manifest، strings، `ui/theme/*`، `KonozRoot` | مرفوع (d21760d) | لم يُبنَ | - |
| 1b | workflow لبناء APK | `.github/workflows/build-apk.yml` | **فشل** (403) | - | - |
| 2 | Room (20 جدول) + DAOs + `Money` | `data/entity/*`, `data/dao/*`, `KonozDatabase`, `Money`, `MoneyTest` | مرفوع (f48c5b2) | لم تُشغَّل | - |
| 2b | ملفات الذاكرة | `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md` | مرفوع (e248341) | - | - |
| 3 | منطق البيع + seed + PinHasher + Robolectric | `domain/{SaleModels,SaleCalculator,SaleService}`, `DatabaseInitializer`, `PinHasher`, tests | مرفوع (417c649) | 16، لم تُشغَّل | - |
| 4 | إدارة المنتجات (Repository + شاشات + كرتونة + أسعار بالجملة منطقًا) | `ProductRepository`, `Quantities`, `CatalogDaos`, `ui/products/*`, strings, tests | مرفوع (c0f812a) | 12، لم تُشغَّل | إزالة Flow داخل Transaction في seed |
| 5 | الكاشير + ماسح الباركود | `domain/{Cart,CashierSession}`, `ui/scanner/*`, `ui/cashier/*`, `KonozRoot`, `ProductsRoute`, `strings_cashier.xml`, tests | مرفوع (كتابة فقط) | 25، لم تُشغَّل | - |
| 6 | إكمال نواقص المرحلة 1 (مسح باركود بالنموذج، مورد/تصنيف، صورة، فلتر، تأكيد إيقاف، أسعار بالجملة، CSV) | `core/{ExpiryDates,Percent,Csv}`, `data/repo/{CatalogRepository,ProductCsvService}`, `ui/common/*`, `ui/products/*`, `strings_catalog.xml`, tests | مرفوع (5419e8e + 04d102a) | 15، لم تُشغَّل | إصلاح تجميع `ProductCsvServiceTest` |
| 7a | منطق المشتريات: مسودة/اعتماد/مرتجع مورد/سداد/رصيد وكشف حساب | `domain/{PurchaseModels,PurchaseCalculator,PurchaseService}.kt`, `OperationsDaos.kt`, `KonozApp`, tests | مرفوع (f2a4dba) | 15، لم تُشغَّل | - |
| 7b | شاشات الموردين وكشف الحساب وفاتورة الشراء/المرتجع + تمكين تبويب المزيد | `data/repo/CatalogRepository.kt` (مُعاد), `domain/{PurchaseEntry,SupplierBalances}.kt`, `ui/suppliers/*` (4), `ui/purchases/*` (2), `ui/more/MoreRoute.kt`, `KonozRoot`, `strings_purchases.xml`, tests | مرفوع (c49deea) | 10 (PurchaseEntry 5، SupplierBalances 2، CatalogRepository 3)، لم تُشغَّل | الدفعة الأولى فشلت (JSON غير صالح في ملف واحد) فأُعيدت كاملة؛ تم أيضًا تفادي ظل اسم متغير في `PurchaseEntry.parsed()` |
