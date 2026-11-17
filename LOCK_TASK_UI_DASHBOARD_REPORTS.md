# LOCK: UI batch 3 - dashboard header + reports card strip (Developer B)

Owner: Developer B, branch `claude/developer-b`, started 2026-11-17.
Scope (owner request): the reports section on the main (dashboard) screen becomes a horizontal swipeable strip of landscape cards, plus a prominent "Start cashier" button at the top corner. Visual change only.

Files touched (do not edit in parallel):
- `app/src/main/java/com/konoz/market/ui/dashboard/DashboardScreen.kt` (only the title item is replaced by the header + strip items)
- NEW `app/src/main/java/com/konoz/market/ui/dashboard/ReportCardsStrip.kt`
- NEW `app/src/main/java/com/konoz/market/ui/theme/ReportCardColors.kt` (design tokens for the cards)
- NEW `res/values/strings_report_cards.xml` and `res/values-ar/strings_report_cards.xml`

Not touched: `ReportsScreen`, `ReportsViewModel`, `ReportService`, `KonozRoot`, `MoreRoute`, `Color.kt`, `Theme.kt`, navigation, permissions.
Delete this file when the branch is merged.
