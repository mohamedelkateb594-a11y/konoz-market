# DEVELOPER_C_HANDOFF.md

_Developer C. فرعي: `claude/developer-c` (مبني على `claude/developer-b` @ db6c139). لم أعدّل `DEVELOPER_A_HANDOFF.md` ولا `DEVELOPER_B_HANDOFF.md`. `main` @ ffa2c4c لم يتغير. لا يعمل أي مطور آخر حاليًا (حسب المالك)._

## الحالة
- البناء نجح حتى 11.1 (أكده المالك). **11.2 (الورديات) و4.2 (المستخدمون) لم تُبنَ ولم تُختبر** — لا Android SDK هنا. المطلوب: تشغيل CI (`./gradlew testDebugUnitTest`) وإرسال الأخطاء.

## المنجز
1. إصلاح `ReportService.kt:245` (`a.qty`) — 4a4da7e.
2. **11.2 الورديات** (ShiftService.observeLiveSummary، ShiftViewModel/Screen، strings، اختبارات `ShiftServiceTest` 11 و`ShiftLiveSummaryTest` 3).
3. **4.2 المستخدمون والصلاحيات (commit af23c02) — ملكية C:**
   - `domain/Permissions.kt`: `Permission` + `Permissions.has(role, perm)` (ADMIN الكل؛ CASHIER: SELL/MANAGE_SHIFTS/SALES_RETURNS؛ STOCK_KEEPER: المنتجات/المشتريات/المخزون/الجرد دون اعتماد).
   - `domain/SessionManager.kt` (المستخدم في الذاكرة فقط)، `domain/AuthService.kt` (login مع تدقيق LOGIN/LOGIN_FAILED، mustChangePin، changePin، createUser، updateUser مع حارس آخر مدير، resetPin، recordLogout).
   - `UserDao`: `findByUsername`, `countActiveAdmins`. `DatabaseInitializer.TEMP_ADMIN_PIN` صار public.
   - `KonozApp`: `session`, `authService`؛ `activeUserId()` يرجع المستخدم المسجَّل (error إن لم يوجد).
   - واجهة: `ui/auth/` (AuthGate، LoginScreen، ChangePinScreen إجباري/اختياري، ViewModels، CurrentUser/signOutAndRestart) + `strings_auth.xml` (EN/AR).
   - تقييد بالصلاحيات: تبويبات `KonozRoot`، قائمة `MoreRoute` (+ تغيير PIN وتسجيل خروج)، اختصارات ولوحة المعلومات (الربح يظهر لـ VIEW_PROFITS فقط).
   - اختبارات: `PermissionsTest` (4) و`AuthServiceTest` (~15) — لم تُشغَّل.

## قرارات افتراضية (قابلة للتغيير من المالك)
- الدخول مطلوب عند كل تشغيل بارد، بلا مهلة خمول.
- PIN من 4 إلى 8 أرقام؛ الجديد ≠ القديم و≠ 1234.
- البيع بدون وردية مفتوحة غير ممنوع؛ سداد الموردين النقدي لا يمس الخزينة.

## الملفات المشتركة التي عدّلتها
`KonozApp.kt`, `MainActivity.kt`, `KonozRoot.kt`, `MoreRoute.kt`, `DashboardScreen.kt` (DashboardActions صارت nullable + `showProfit`), `OperationsDaos.kt`, `DatabaseInitializer.kt`.

## مؤجل داخل 4.2 (4.2c)
- شاشة إدارة المستخدمين (MANAGE_USERS؛ الخدمة جاهزة).
- حماية اعتماد الجرد (APPROVE_COUNT) داخل `StockCountScreen/Service`.
- إخفاء زر «إضافة منتج» للباركود غير المعروف في الكاشير لمن لا يملك MANAGE_PRODUCTS (الآن no-op آمن).
- تفاصيل صلاحيات التقارير.

## الخطوة التالية بالضبط
1. تشغيل البناء/الاختبارات على `claude/developer-c` وإصلاح الأخطاء.
2. 4.2c ثم 4.3 (مشاركة/طباعة الفواتير وتصدير التقارير) ثم 4.4 (نسخ احتياطي يشمل `receipts` و`product_images`) ثم المرحلة 5.
3. تحديث `PROJECT_STATE.md` و`TASK_LOG.md` و`CLAUDE.md` (ما زالت عند 8b) عند الدمج.
