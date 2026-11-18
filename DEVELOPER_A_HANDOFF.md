# DEVELOPER_A_HANDOFF.md

_Branch: `claude/developer-a` (مفتوح من `claude/developer-b` @ d2d5a7b بتاريخ 2026-11-18). هذا الملف يخص المطوّر A. التفاصيل التاريخية أسفل القسم الأول كما كانت._

## آخر تحديث (2026-11-18): UI-MINT — هوية Mint & Cream + تفاصيل المنتج
- **قفل:** `LOCK_TASK_UI_MINT.md` (يُحذف عند الدمج؛ فيه ملاحظات الدمج لـ B).
- **طلب المالك:** (1) اعتماد Mint & Cream في الوضعين الفاتح والداكن عبر Theme مركزي دون تغيير المنطق أو ترتيب الشاشات؛ (2) تعديل شكل الأيقونات بأسلوب صورة المرجع؛ (3) بطاقة المنتج تفتح شاشة تفاصيل؛ (4) عنصر من خصائص المنتج يفتح شاشة منفصلة عند الحاجة. المالك سمح بتغيير الهوية السابقة (Modern Blue) لأنها مذكورة في الطلب.
- **الألوان:** كل القيم في `Brand` داخل `Color.kt` كما حددها المالك (فاتح: خلفية #F7FAF5، بطاقات #FFFFFF، أخضر فاتح #E1F2E6، نعناعي #BDE3CA، أساسي #4D9870، نص #263B30/#647568؛ داكن: خلفية #17231D، بطاقات #223229، سطح ثانوي #2B4033، نعناعي #9BD5B1، أساسي #83C69D، نص #F1F7F2/#B5C7BA). المضاف: كريمي (#FFF8E6 → #F6EBCB)، ألوان نجاح/تحذير/خطأ مستقلة (نجاح #1E8E3E، تحذير #E3A008، خطأ #C93C3C، وللداكن #6FD891/#F2B84B/#FF8F8F) وحاويات لها. أسماء `Brand` القديمة كلها باقية.
- **Theme:** `Theme.kt` يربط الألوان بـ Material roles؛ بطاقات Material (`surfaceContainerHighest`) = أبيض في الفاتح و#223229 في الداكن. `KonozColors` أضاف `successContainer/onSuccessContainer`.
- **تدرجات البطاقات:** `ui/theme/CardGradients.kt` (`CardKind`: NEUTRAL, MONEY نعناعي، STOCK كريمي، ALERT خوخي، INFO نعناعي فاتح؛ `cardBrush(kind)` يتبع الوضع). النص فوقها دائمًا `onSurface` (تباين أكثر من 7:1)؛ الوصف الثانوي `onSurface` بشفافية 85% لأن #647568 على النعناعي 3.5:1 فقط.
- **الأيقونات:** `ui/common/KonozComponents.kt` → `KonozIconBadge` (مربع بزوايا مدورة + أيقونة Rounded) و`konozNavItemColors()` (حبة نعناعية خلف التبويب المحدد). طُبّق في: شريط التنقل، شبكة «المزيد»، المنتجات، شاشة التفاصيل، `ProductImage` (إطار مدور). بطاقات التقارير: ألوانها صارت تتبع الوضع (نعناعي/كريمي فاتح، أخضر غامق في الداكن) دون تغيير `ReportCardsStrip.kt`.
- **تفاصيل المنتج:** `ProductsRoute` الآن: قائمة ← تفاصيل (`ProductDetailScreen`) ← شاشة خاصية (`ProductSectionScreen`: الأسعار، المخزون، الباركود)، وزر القلم في التفاصيل يفتح نموذج التعديل الحالي ثم يعود للتفاصيل ويعيد تحميلها. الفئة والمورد والكود والصلاحية تُعرض كبطاقات للقراءة فقط. `ProductDetailViewModel` يقرأ عبر `ProductRepository.getDetails` فقط (لا منطق جديد، لا تغيير في Room). زر الرجوع في النظام يتراجع خطوة خطوة.
- **ما لم يتغير:** منطق التطبيق، الصلاحيات، الكاشير، القواعد التقنية الثابتة. المنتج الجديد من الكاشير (باركود غير معروف) ما زال يفتح نموذج الإضافة مباشرة.
- **غير مُختبر (معلن):** لم يُبنَ المشروع ولم تُشغَّل اختبارات (لا Android SDK هنا؛ العمل عبر GitHub API). مواضع احتمال خطأ ترجمة: أسماء أيقونات `Rounded` (`Sell`, `QrCode2`, `Tag`, `Event`, `AssignmentReturn`, `FactCheck`)، و`ProductDetailSection.entries`، و`ExpiryDates.toPickerMillis` المستخدمة في `formatExpiry`، وأنواع `Product.piecesPerCarton/expiryDate`. لم تُراجَع الألوان بصريًا على جهاز في الوضعين.
- **لم يُراجَع:** شاشات لم أفتحها قد تحتوي ألوانًا مكتوبة مباشرة (الكاشير، الفاتورة، الجرد، الورديات...). الفاتورة المطبوعة تُترك بالأبيض والأسود عمدًا (قرار B). يلزم تدقيق ألوان على جهاز.
- **الخطوة التالية بالضبط:** (1) تشغيل بناء `claude/developer-a` في Actions وإرسال أي `e:`/`error:`؛ (2) تجربة الوضعين على جهاز: التبويبات، المنتجات، التفاصيل، الخصائص، المزيد، لوحة التقارير؛ (3) PR من `claude/developer-a` إلى `claude/developer-b` بعد نجاح البناء وموافقة المالك؛ (4) تدقيق ألوان الشاشات المتبقية.

---

## القسم السابق (2026-11-15، قبل UI-MINT)

## ملكية المهام (LOCK)
- **المهمة 4.3 (مشاركة وطباعة الفواتير): مملوكة للمطوّر A** منذ هذا الـ commit. لا يبدأها B أو C.
- الملفات التي ستُلمس في 4.3 (لا تعدّلوها بالتوازي):
  - جديد: `domain/InvoiceDocument.kt`، `domain/EscPos.kt`، `ui/receipt/*` (رسم الإيصال، تصدير PNG/PDF، مشاركة، طباعة Bluetooth، شاشة الفواتير والتفاصيل)، `res/xml/file_paths.xml`، `strings_receipt.xml`.
  - مشتركة: `AndroidManifest.xml` (FileProvider)، `KonozApp.kt`، `ui/more/MoreRoute.kt`، `ui/cashier/CashierScreen.kt` (أزرار مشاركة/طباعة في نافذة نجاح البيع).
- بعد 4.3: تصدير التقارير PDF/CSV ثم النسخ الاحتياطي (4.4).

## ما أنجزه A على هذا الفرع (مرتب)
1. **المهمة 9.1 (المصروفات) — إكمال عمل B:**
   - أُضيف `uuid` فريد إلى `Expense` (المخطط لم يصدر بعد فلا Migration). `ExpenseInput.uuid` يمنع تكرار الإضافة عند إعادة المحاولة.
   - `ui/expenses/{ExpensesViewModel,ExpensesScreen}`: فترات يوم/شهر/سنة، إجماليات، إضافة/تعديل/حذف بتأكيد، صورة إيصال (`ui/common/ReceiptImages`).
   - `strings_expenses.xml` (AR/EN)، `ExpenseIdempotencyTest` (3).
2. **المهمة 9.2 (الجرد):**
   - `domain/StockCountService` (+`CountMath`, `CountException`): بدء جرد (شامل/تصنيف) يلتقط الكمية والتكلفة، إدخال الكميات يدويًا أو بالباركود، **الاعتماد وحده يعدّل المخزون** عبر `COUNT_ADJUSTMENT`، يطبّق الفرق الذي وجده الجرد على المخزون الحالي (مبيعات أثناء الجرد محفوظة)، لا يهبط المخزون تحت الصفر، منع اعتماد/تعديل مكرر، تسجيل المعتمِد والتاريخ.
   - `StockAdjustmentService.adjust` أخذ معامل `type` اختياريًا (ADJUSTMENT أو COUNT_ADJUSTMENT).
   - واجهات: `ui/stockcount/{StockCountListViewModel,StockCountListScreen,StockCountViewModel,StockCountScreen}` + `strings_stockcount.xml` + مساران في `MoreRoute` (`counts`, `count/{id}`).
   - اختبارات: `StockCountServiceTest` (11).
3. قبل ذلك على `main` (المهام 1–8): الهوية، قاعدة Room (20 جدول)، المنتجات، الكاشير والماسح، المشتريات والموردون، المرتجعات، التسويات، التنبيهات، CSV. تفاصيلها في `PROJECT_STATE.md` و`TASK_LOG.md` على main.

## ما أنجزه B (مرجعه `DEVELOPER_B_HANDOFF.md`)
التقارير (10.1 وما بعده)، الورديات والخزينة، المستخدمون وتسجيل الدخول والصلاحيات (4.2 و4.2b). **اقرأ ملف B قبل أي تعديل في هذه الأجزاء.**

## قواعد ثابتة (من `PROJECT_STATE.md`، لا تُغيَّر)
- المال `Long` بالقروش، الكميات `Long` بالألف، لا Double. تقسيم أي مبلغ على أجزاء عبر `Allocation.proportional`.
- المخزون = `inventory_movements` (append-only)؛ `currentQtyMilli` كاش يتغير فقط مع حركة في نفس الـ Transaction؛ الخروج دائمًا FEFO.
- كل مستند له `uuid` لمنع التكرار؛ الأرقام المتسلسلة تُمنح عند الاعتماد فقط.
- لا `fallbackToDestructiveMigration`؛ أي تغيير مخطط بعد أول نسخة منشورة = رفع `version` + Migration.

## الحالة والمخاطر
- **لم يُبنَ المشروع قط** (لا Android SDK في بيئة Claude). 13+ مهمة مكتوبة بلا بناء. أول خطوة قبل أي دمج: تشغيل workflow البناء على هذا الفرع (Actions) وإرسال سطور الأخطاء (`e:` / `error:`) للإصلاح.
- الفرع لم يُدمج في `main`. الدمج بعد نجاح البناء (Pull Request).
- نواقص معروفة: خط Cairo غير مضمّن، النسخ الاحتياطي لم يبدأ (يجب أن يشمل مجلدي `product_images` و`receipts`)، لا اختبارات واجهة، `gradle-wrapper.jar` غير موجود.
