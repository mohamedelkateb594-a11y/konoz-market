# LOCK: Task 4.3 (invoice PDF/image, WhatsApp, share) - STATUS UPDATE FOR DEVELOPER A

Owner of the original claim: Developer B (2026-11-14). Developer A also claimed 4.3 in DEVELOPER_A_HANDOFF.md (2026-11-15) without these commits in view.
**The core of 4.3 is already DONE on this branch. Do not recreate it; extend it.** Commits: bb9d132, 8b4f637, 9233472, 7f30dbf (see DEVELOPER_B_HANDOFF.md, section 4.3/4.3b).

Already present (read these files, do not rewrite them from scratch):
- `domain/InvoiceData.kt` (`InvoiceLoader.load`), `domain/PhoneNumbers.kt`
- `ui/receipt/InvoiceRenderer.kt` (80 mm receipt: `prepare(data)` -> `Page`, `writePdf`, `writePng`; Arabic via StaticLayout), `InvoiceSharer.kt` (FileProvider, WhatsApp to a typed number, share sheet), `InvoicesViewModel.kt`, `InvoicesScreen.kt` (`InvoiceShareDialog`)
- `res/xml/file_paths.xml`, FileProvider + WhatsApp `<queries>` in `AndroidManifest.xml`, `strings_invoices.xml` (AR/EN)
- Cashier: "Send invoice" button on the receipt dialog, and a scan alert (beep, vibration, banner) on every scan that adds a product
- More menu: "Invoices and sharing"

Still open and free for Developer A (the part B did NOT do):
- ESC/POS Bluetooth thermal printing, 58 mm and 80 mm (`domain/EscPos.kt`, a Bluetooth printer screen/service). Suggested reuse: add a `toBitmap()` helper next to `InvoiceRenderer.writePng` and send it as an ESC/POS raster image.
- Report export (PDF / CSV / Excel).
Use different file names than the ones above (for example `ui/receipt/BluetoothPrinter*.kt`, `domain/EscPos.kt`, `strings_printing.xml`) so nothing already working is overwritten.

Shared files: B changed `MoreRoute.kt` (menu entries) and `CashierScreen.kt`/`CashierViewModel.kt`/`CashierRoute.kt` for 4.3/4.3b, and B also adds a Backup entry to `MoreRoute.kt` for Task 4.4 (see LOCK_TASK_4_4_BACKUP.md). Always read the latest version before editing them.
Delete this file when 4.3 is merged.
