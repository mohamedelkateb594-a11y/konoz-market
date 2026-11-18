# LOCK: UI home screen redesign (Developer B)

Owner: Developer B, branch `claude/developer-b-home` (from main @ 2fae67f), 2026-11-18. Replaces `claude/developer-b-report-cards` (PR #15, conflicted with main after PR #14): close PR #15 and open a PR from this branch.
Scope (owner requests): home screen with a swipeable strip of cards (products + every former More item), side menu (75% width) for program settings, cashier opened only from the "Start cashier" button (no bottom bar, back button at the bottom of the cashier), product-search lens on the cashier camera, fixed invoice header with small pill rows, mint and creamy palette in light and dark.

Files touched (do not edit in parallel):
- NEW `ui/home/HomeScreen.kt`, NEW `res/values/strings_home.xml`, NEW `res/values-ar/strings_home.xml`
- `ui/theme/Color.kt` (Brand values: mint and creamy; names unchanged, new surface-container tokens), `ui/theme/Theme.kt` (every role set, surface containers), `ui/theme/ReportCardColors.kt` (mint and cream card tints)
- `app/src/test/.../ThemeContrastTest.kt` (new pairs + the home cards)
- REWRITTEN `ui/KonozRoot.kt` (no NavigationBar; home + cashier screens; one explicit page Surface)
- `ui/more/MoreRoute.kt`: params `embedded`, `onHome`; first route = startRoute when embedded; else-branch
- `ui/cashier/CashierScreen.kt`: search lens + panel, CartHeader, CartRow (pill); search field removed from the bottom panel
- `ui/dashboard/DashboardScreen.kt`: its own header and report-cards strip removed (the home screen provides them)

Not touched: ReportsScreen, ProductsRoute, CashierRoute/ViewModel, ReportCardsStrip.kt (now unused, kept), permissions, database.
Delete this file when merged.
