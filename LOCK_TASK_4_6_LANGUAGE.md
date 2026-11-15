# LOCK: Task 4.6 + 4.5 + 4.7 (Developer A)

Owner: Developer A. Branch: `claude/developer-a-language` (from `claude/developer-b` @ 475e852).

**4.6 Arabic default, language switch, Cairo, cashier cart + post-payment send:** `ui/language/AppLanguage.kt`, `MainActivity.kt`, `ui/theme/{Type,Theme}.kt`, `ui/cashier/{CashierScreen,CashierRoute}.kt`, `ui/receipt/InvoicesScreen.kt`, `strings_language.xml`, `strings_cashier_flow.xml`.

**4.5 Bluetooth thermal printing:** C's design from PR #4 adopted and ported here (owner decision: A finishes printing). New `domain/EscPos.kt`, `ui/printer/*`, `strings_printer.xml`, `EscPosTest`; `KonozApp.kt` (+printService), `MoreRoute.kt` (+printer entry); `InvoicesViewModel.print()` and a Print button in the invoice dialog. **PR #4 should be closed, not merged** (same file names would conflict).

**4.7 Report export PDF / Excel / CSV:** `domain/{CsvRows,XlsxWriter}.kt`, `ui/reports/ReportPdf.kt`, `ui/common/ShareBytes.kt`, export menu in `ReportsScreen.kt`, `strings_report_export.xml`, `ReportExportFormatsTest`.

B and C: do not edit these files in parallel without coordinating. Delete this file on merge.
