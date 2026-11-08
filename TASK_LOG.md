# TASK_LOG.md

| # | الوصف | الملفات | النتيجة | الاختبارات | أخطاء محلولة |
|---|-------|---------|---------|-----------|--------------|
| 0 | إنشاء ريبو GitHub `konoz-market` | - | تم (أنشأه المستخدم يدويًا) | - | 403 على create_repository: حُلّ بإنشاء يدوي |
| 1 | هيكل + Gradle + هوية بصرية + تنقل | `settings/build.gradle.kts`, `libs.versions.toml`, `app/build.gradle.kts`, Manifest, strings, `ui/theme/*`, `KonozRoot.kt`, `MainActivity.kt`, `KonozApp.kt` | مرفوع (d21760d) | لم يُبنَ | - |
| 1b | workflow لبناء APK | `.github/workflows/build-apk.yml` | **فشل** (403)، لم يُرفع | - | - |
| 2 | مخطط Room (20 جدول) + DAOs + `Money` | `data/entity/*`, `data/dao/*`, `KonozDatabase.kt`, `Money.kt`, `MoneyTest.kt` | مرفوع (f48c5b2) | مكتوب، لم يُشغَّل | - |
| 2b | ملفات الذاكرة | `CLAUDE.md`, `PROJECT_STATE.md`, `TASK_LOG.md` | مرفوع (e248341) | - | - |
| 3 | منطق البيع + seed + PinHasher + ربط KonozApp + إعداد Robolectric | `domain/{SaleModels,SaleCalculator,SaleService}.kt`, `data/seed/DatabaseInitializer.kt`, `core/PinHasher.kt`, `KonozApp.kt`, `app/build.gradle.kts`, `libs.versions.toml`, `SaleCalculatorTest`, `SaleServiceTest` | مرفوع (كتابة فقط) | 16 اختبارًا جديدًا، لم تُشغَّل | - |
