# DEVELOPER_A_HANDOFF.md

_هذا الملف يخص المطوّر A. آخر تحديث: 2026-11-18 على `claude/developer-a-mint` (PR #22 مفتوح، لم يُدمج بعد)._

## آخر دفعة (2026-11-18): شاشة الكاميرا وشاشة الدخول — **لم تُبنَ ولم تُجرَّب**
طلب المالك، تنفيذ A فوق `claude/developer-a-mint` (آخر عمل C مدموج في `main`؛ لم أمسّ ملفات C/B):
1. **زر الرجوع للقائمة الرئيسية** (أيقونة اللوحة فوق الكاميرا) حُذف من `KonozRoot.kt`. الرجوع يبقى بزر الهاتف (`BackHandler`).
2. **زر «الورديات»** (فتح الوردية) انتقل من فوق الكاميرا إلى اللوحة السفلية بجانب زر «دفع» (`CashierScreen`، `FilledTonalButton`). `CashierRoute` و`CashierScreen` لهما بارامتر جديد `onOpenShifts: (() -> Unit)? = null`؛ `KonozRoot` يمرّره فقط لمن يملك `OPERATE_SHIFTS` (وإلا null فيختفي الزر). السلوك نفسه: يفتح شاشة الورديات كصفحة منفصلة ثم يعود للكاشير.
3. **شاشة الدخول** (`AuthScreens.kt`) أُعيد تصميمها: بسيطة ومتمركزة، دوائر ناعمة في الخلفية، شعار (حقيبة تسوق مرسومة بالأشكال) واسم البرنامج **«تاجر»** بخط ثقيل (Black 52sp) وتحته «TAJER» بتباعد حروف وخط تحته، حقلان وزر. الألوان من `KonozTheme_`/`MaterialTheme` فقط. نصوص جديدة: `strings_login_brand.xml` (`login_brand_name`, `login_brand_latin`) في `values` و`values-ar`. `ForcePinScreen` لم تتغير.
- الرسمة القديمة (`drawLoginIllustration`) حُذفت؛ مفاتيح `Brand.Illustration*` و`signInHeader` ما زالت في الثيم (الأخير يُستخدم الآن للدوائر).

## ملاحظات للمالك / قرارات مفتوحة
- **الخط الخاص بـ«تاجر»:** لا أستطيع رفع ملفات خطوط. الآن يستخدم خط الثيم (Cairo إن رُفع `cairo_bold.ttf`، وإلا خط النظام) بوزن Black + الشعار المرسوم. لهوية أقوى: ارفع ملف خط عربي عرضي (مثل Lalezar أو Changa) إلى `res/font/` وأخبرني لأربطه بالاسم فقط.
- اسم التطبيق تحت الأيقونة (`app_name`) لم يتغير. إن أردته «تاجر» أيضًا فهو تغيير سطر واحد.
- مكان زر الورديات (جنب «دفع») وعرضه: قابل للتبديل مع المالك بعد التجربة على الجهاز (الشريط: الإجمالي | الورديات | دفع).

## الملفات المتغيرة في هذه الدفعة
`ui/KonozRoot.kt`، `ui/cashier/CashierRoute.kt`، `ui/cashier/CashierScreen.kt`، `ui/auth/AuthScreens.kt`، `res/values{,-ar}/strings_login_brand.xml`.

## الخطوة التالية بالضبط
1. تشغيل بناء `claude/developer-a-mint` (Actions) وإرسال أي `e:`؛ أماكن الاحتمال: `AuthScreens.kt` (`drawArc` بالمعاملات الموضعية، `displayMedium`)، `CashierScreen` (استيراد `Icons.Rounded.Schedule` يلزم أن يكون `material-icons-extended` موجودًا، وهو مستخدم أصلًا في الفرع).
2. تجربة على جهاز: ازدحام الصف السفلي في الكاشير على شاشة صغيرة، ومظهر الدخول في الوضعين الفاتح والداكن.
3. دمج PR #22 في `main`، ثم حذف `LOCK_TASK_UI_MINT.md` وتحديث `PROJECT_STATE.md` و`TASK_LOG.md`.
4. ما بقي من خطة C/B (دون تغيير): تجربة الطباعة وتصدير Excel/PDF، رفع خط Cairo، المرحلة 5 (أداء، APK، `gradle-wrapper.jar`).

## قواعد ثابتة
- المال `Long` بالقروش، الكميات `Long` بالألف؛ المخزون = `inventory_movements` (append-only)؛ لا `fallbackToDestructiveMigration`.
- لا ألوان مكتوبة داخل الشاشات (Design Tokens فقط)؛ النصوص في `strings*.xml`.
- لا تكتب «شرطة مائلة ثم نجمة» داخل تعليق Kotlin.
- اقرأ `DEVELOPER_B_HANDOFF.md` و`DEVELOPER_C_HANDOFF.md` قبل تعديل أجزائهما.
