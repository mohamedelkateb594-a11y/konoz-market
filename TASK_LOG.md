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
| 5 | الكاشير + ماسح الباركود | `domain/{Cart,CashierSession}`, `ui/scanner/*`, `ui/cashier/*`, `KonozRoot`, `ProductsRoute`, `strings_cashier.xml`, tests | مرفوع (كتابة فقط) | **25** جديدًا (CartTest 11، ScanDebouncerTest 3، CashierSessionTest 11)، لم تُشغَّل (تصحيح: كان مسجّلًا 28 خطأً) | - |
| 6 | إكمال نواقص المرحلة 1: مسح باركود بالنموذج، مورد/تصنيف جديد، صورة، فلتر تصنيف، تأكيد إيقاف، تحديث أسعار بالجملة (واجهة)، استيراد/تصدير CSV | `core/{ExpiryDates,Percent,Csv}`, `data/repo/{CatalogRepository,ProductCsvService}`, `ui/common/{ProductImages,ProductImage}`, `ui/products/*` (مُعاد كتابتها), `KonozApp`, `strings_catalog.xml`, tests | مرفوع (5419e8e) + تصحيح | 15 جديدًا (Csv 5، Percent 2، ExpiryDates 2، ProductCsvService 6)، لم تُشغَّل | اكتُشف أثناء حفظ نقطة التوقف: اختبار `ProductCsvServiceTest` كان يستخدم `flow.first(...)` كدالة عامة (خطأ تجميع) — أُصلح بـ `observeAll().first()` |
