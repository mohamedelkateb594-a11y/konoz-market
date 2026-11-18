# LOCK: UI-MINT (Developer A)

قرار المالك (2026-11-18): اعتماد هوية **Mint & Cream** بدل Modern Blue، وبطاقة المنتج تفتح شاشة تفاصيل، وخصائص المنتج تفتح شاشات منفصلة.

الفرع: `claude/developer-a` (مفتوح من `claude/developer-b` @ d2d5a7b). لا أعدّل فرع B أو C.

## الملفات التي ألمسها (لا تعدّلوها بالتوازي)
- `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/ReportCardColors.kt` (واجهة `ReportCardTokens` كما هي، لكن الـ getters صارت `@Composable` وتتبع الوضع)
- جديد: `ui/theme/CardGradients.kt`, `ui/common/KonozComponents.kt`
- `ui/common/ProductImage.kt`
- `ui/products/{ProductsRoute,ProductsScreen}.kt`، جديد: `ProductDetailScreen.kt`, `ProductDetailViewModel.kt`
- `ui/more/MoreTileGrid.kt`, `ui/KonozRoot.kt` (التبويبات فقط)
- جديد: `res/values{,-ar}/strings_product_details.xml`

## ملاحظات دمج لـ B
- `Color.kt`/`Theme.kt`: خذ نسخة A كاملة (قيم Mint)؛ أسماء `Brand.*` القديمة كلها باقية فلا يكسر شيء.
- `ReportCardsStrip.kt` لم يُمس. `ReportCardTokens.Title/Description/IconDefault/IconBadge/styles` صارت `@Composable get()`؛ لا تستخدمها خارج دالة composable.
- `ProductsScreen(onEdit)` أُعيدت تسميته إلى `onOpen` (زر التعديل صار داخل شاشة التفاصيل).
- `MoreTileGrid.kt`: خذ نسخة A (الأيقونات Rounded + بطاقات متدرجة)؛ سطر `section` في `MoreRoute` لم يُمس.

يُحذف هذا الملف عند الدمج.
