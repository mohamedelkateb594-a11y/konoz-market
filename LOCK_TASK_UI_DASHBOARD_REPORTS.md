# LOCK: UI batch 3 - dashboard header + reports card strip + section deep link (Developer B)

Owner: Developer B, branch `claude/developer-b`, started 2026-11-17.
Scope (owner request): the reports section on the main (dashboard) screen becomes a horizontal swipeable strip of landscape cards, a prominent "Start cashier" button at the top corner, and (owner approved) each card opens the Reports screen scrolled to its own section. Visual change plus that navigation parameter.

Files touched (do not edit in parallel):
- `app/src/main/java/com/konoz/market/ui/dashboard/DashboardScreen.kt` (title item replaced by header + strip; `DashboardActions.onOpenReport` added with a default)
- NEW `app/src/main/java/com/konoz/market/ui/dashboard/ReportCardsStrip.kt`
- NEW `app/src/main/java/com/konoz/market/ui/theme/ReportCardColors.kt` (design tokens for the cards)
- NEW `app/src/main/java/com/konoz/market/ui/reports/ReportSections.kt` (section ids and their order)
- `app/src/main/java/com/konoz/market/ui/reports/ReportsScreen.kt` (optional `section` parameter + scroll once after load; nothing else changed)
- `app/src/main/java/com/konoz/market/ui/more/MoreRoute.kt` (ONE line: the REPORTS branch passes `section = parts.getOrNull(1)`; header comment updated)
- `app/src/main/java/com/konoz/market/ui/KonozRoot.kt` (ONE line added: `onOpenReport` in the DashboardActions call)
- NEW `res/values/strings_report_cards.xml` and `res/values-ar/strings_report_cards.xml`

Merge note for A and C: `MoreRoute.kt` is also edited by `claude/developer-a-language` and `claude/developer-c-branding`. Take their version and re-apply only the one REPORTS line above. Same for `KonozRoot.kt`: re-apply only the `onOpenReport = ...` line.

Not touched: `ReportsViewModel`, `ReportService`, `Color.kt`, `Theme.kt`, permissions (route `reports/<id>` still needs VIEW_PROFIT).
Delete this file when the branch is merged.
