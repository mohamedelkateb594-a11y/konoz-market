# KONOZ MARKET - كنوز ماركت

تطبيق Android لإدارة محل بقالة / سوبر ماركت (POS + مخزون + مشتريات + مصروفات + جرد + تقارير).
يعمل Offline بالكامل. Kotlin + Jetpack Compose + Material 3 + Room + CameraX + ML Kit.

- Package: `com.konoz.market`
- minSdk 24 / targetSdk 35

## البناء
1. افتح المجلد في Android Studio (Ladybug أو أحدث) وانتظر Gradle Sync.
2. شغّل على جهاز أو محاكي، أو: Build > Build APK(s).

## الخط (Cairo)
لم يتم تضمين ملفات الخط داخل المستودع. لإضافة Cairo:
1. نزّل Cairo من Google Fonts.
2. ضع `cairo_regular.ttf` و `cairo_bold.ttf` في `app/src/main/res/font/`.
3. عدّل `AppFontFamily` في `ui/theme/Type.kt`.

## المراحل
- [x] المرحلة 1 - الدفعة 1: هيكل المشروع + الهوية البصرية (Design Tokens)
- [ ] المرحلة 1 - الدفعة 2: قاعدة Room (19 جدول)
- [ ] المرحلة 1 - الدفعة 3: إدارة المنتجات
- [ ] المرحلة 1 - الدفعة 4: الكاشير + الباركود + حفظ الفواتير
- [ ] المراحل 2 - 5
