# LOCK: Task 4.4 (backup / restore)

Owner: Developer C, started 2026-11-14. Based on `claude/developer-b` @ cd494c6 (branch `claude/developer-c-4-4`).
New files: `domain/BackupService.kt`, `domain/BackupModels.kt`, `domain/BackupScheduler.kt` (WorkManager), `ui/backup/*`, `strings_backup.xml` (AR/EN), tests `BackupServiceTest`, `BackupManifestTest`.
Shared files touched (small additions only): `KonozApp.kt` (+backupService), `MoreRoute.kt` (+backup route/menu item), `Permissions.kt` (+MANAGE_BACKUP, admin only), `res/xml/file_paths.xml` (maybe).
Not touched: thermal printing, report PDF/Excel export (still open).
Delete this file when the task is merged.
