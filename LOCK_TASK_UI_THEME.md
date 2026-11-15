# LOCK: المهمة UI-THEME (Developer B)

المالك اعتمد هوية Modern Blue (2026-11-15). نطاق هذه الدفعة: الألوان المركزية فقط.

## الملفات
- `app/src/main/java/com/konoz/market/ui/theme/Color.kt` (قيم Brand الجديدة + أسماء قديمة كبدائل حتى لا ينكسر البناء)
- `app/src/main/java/com/konoz/market/ui/theme/Theme.kt` (أجزاء الألوان وKonozColors فقط)

## تنبيه للمطوّر A (فرع `claude/developer-a-language`)
فرعك يعدّل `Theme.kt` أيضًا (استيراد `remember`/`LocalContext` وبناء `typography` من `konozTypography(cairoFontFamily(context))` داخل `KonozTheme`). تعديلي لا يلمس الاستيرادات ولا جسم `KonozTheme` عدا ما يلزم، فالمفروض أن يندمج دون تعارض؛ إن ظهر تعارض فاحتفظ بسطور الخط من فرعك وبكتل الألوان من فرعي. لا تغيّر `Type.kt` من جهتي.

## لا أعدّل
أسماء التطبيق، إعدادات المتجر (فرع C branding)، اللغة (A)، أي شاشة. تُحذف هذه الملف عند الدمج.
