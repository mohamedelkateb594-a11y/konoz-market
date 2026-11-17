# LOCK: UI home screen redesign (Developer B)

Owner: Developer B, branch `claude/developer-b-report-cards` (from main @ 4c14302), 2026-11-17.
Scope (owner request): home screen with a swipeable strip of cards (products + every former More item), side menu (75% width) for program settings, cashier opened only from the "Start cashier" button (no bottom bar), product-search lens on the cashier camera, fixed invoice header with small pill rows.

Files touched (do not edit in parallel):
- NEW `ui/home/HomeScreen.kt`, NEW `ui/theme/ReportCardColors.kt`, NEW `res/values/strings_home.xml`, NEW `res/values-ar/strings_home.xml`
- REWRITTEN `ui/KonozRoot.kt` (no NavigationBar; home + cashier screens)
- `ui/more/MoreRoute.kt`: params `embedded`, `onHome`; first route = startRoute when embedded; else-branch
- `ui/cashier/CashierScreen.kt`: search lens + panel, CartHeader, CartRow (pill); search field removed from the bottom panel

Not touched: DashboardScreen, ReportsScreen, ProductsRoute, CashierRoute/ViewModel, permissions, database.
Conflict note: PR #12 (`claude/developer-b-colors`) edits MoreRoute.kt; re-apply the `embedded`/`onHome` changes on top of it.
Delete this file when merged.
