# LOCK: Task 4.6 - Arabic default + language switch, Cairo font, cashier cart and post-payment send

Owner: Developer A. Branch: `claude/developer-a-language` (from `claude/developer-b` @ 475e852).

Files touched (B and C: do not edit them in parallel without coordinating):
- new `ui/language/AppLanguage.kt`, `res/values{,-ar}/strings_language.xml`, `res/values{,-ar}/strings_cashier_flow.xml`
- `MainActivity.kt`, `ui/more/MoreRoute.kt` (one menu item), `ui/theme/Type.kt`, `ui/theme/Theme.kt` (typography from Cairo)
- `ui/cashier/CashierScreen.kt` (numbered rows, delete button, no ReceiptDialog, no onShareInvoice parameter), `ui/cashier/CashierRoute.kt` (opens the send dialog right after payment)
- `ui/receipt/InvoicesScreen.kt` (`InvoiceShareDialog` got optional header / focusPhone / closeLabelRes; the Invoices screen call is unchanged)

Delete this file on merge.
