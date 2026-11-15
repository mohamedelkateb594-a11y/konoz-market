# LOCK: Task 4.5 (Bluetooth thermal printing 58/80 mm)

Owner: Developer C, started 2026-11-15. Branch `claude/developer-c-4-5`, based on `claude/developer-c-4-4` (PR #3, backup).
New files: `domain/EscPos.kt`, `ui/printer/*` (ThermalPrinter, PrintService, PrinterViewModel, PrinterScreen), `strings_printer.xml` (AR/EN), test `EscPosTest`.
Shared files touched (small additions): `KonozApp.kt` (+printService), `MoreRoute.kt` (+printer route/menu).
NOT touched (owned by B): `InvoiceRenderer`, `InvoicesScreen`, `InvoicesViewModel`, cashier dialog. A "Print" button there is left for B to add using `PrintService.printInvoice(id)`.
Delete this file when merged.
