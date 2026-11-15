package com.konoz.market.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.backup.BackupException
import com.konoz.market.backup.BackupInfo
import com.konoz.market.backup.BackupScheduler
import com.konoz.market.backup.BackupService
import com.konoz.market.backup.BackupSettings
import com.konoz.market.data.entity.AppSetting
import com.konoz.market.data.entity.AuditLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class LocalBackup(val name: String, val size: Long, val modified: Long, val path: String)

/** A verified backup file waiting for the user's confirmation. [temporary] files (picked from storage) are deleted afterwards. */
class PendingRestore(val info: BackupInfo, val source: File, val temporary: Boolean)

enum class BackupMessage { SAVED, COPIED, RESTORED, NOT_BACKUP, VERSION, CORRUPT, NO_ADMIN, FAILED }

data class BackupUi(
    val auto: Boolean = false,
    val lastBackupAt: Long? = null,
    val local: List<LocalBackup> = emptyList(),
    val busy: Boolean = false,
    val pending: PendingRestore? = null,
    val message: BackupMessage? = null,
)

class BackupViewModel(private val app: KonozApp) : ViewModel() {
    private val service = BackupService(app, app.database)

    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val settings = app.database.settings()
        val auto = settings.get(BackupSettings.AUTO_ENABLED) == "true"
        val last = settings.get(BackupSettings.LAST_AT)?.toLongOrNull()
        val local = withContext(Dispatchers.IO) {
            service.listLocalBackups().map { LocalBackup(it.name, it.length(), it.lastModified(), it.absolutePath) }
        }
        _ui.update { it.copy(auto = auto, lastBackupAt = last, local = local) }
    }

    fun consumeMessage() = _ui.update { it.copy(message = null) }

    /** Saves a backup to the place the user picked (Downloads, Drive, a memory card...). */
    fun exportTo(uri: Uri) = launchBusy {
        val out = withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri) } ?: throw IOException("cannot open the target file")
        service.createBackup(out)
        service.recordBackupTime()
        audit("BACKUP_EXPORTED")
        refresh()
        _ui.update { it.copy(message = BackupMessage.SAVED) }
    }

    /** Copies a backup kept on this phone to the place the user picked. */
    fun copyLocalTo(path: String, uri: Uri) = launchBusy {
        withContext(Dispatchers.IO) {
            val out = app.contentResolver.openOutputStream(uri) ?: throw IOException("cannot open the target file")
            File(path).inputStream().use { input -> out.use { input.copyTo(it) } }
        }
        _ui.update { it.copy(message = BackupMessage.COPIED) }
    }

    /** Step 1 of a restore from a file the user picked: copy it, verify it, and ask for confirmation. Nothing is changed yet. */
    fun prepareRestore(uri: Uri) = launchBusy {
        val file = File(app.cacheDir, "incoming-backup.zip")
        withContext(Dispatchers.IO) {
            val input = app.contentResolver.openInputStream(uri) ?: throw IOException("cannot open the backup file")
            input.use { i -> file.outputStream().use { i.copyTo(it) } }
        }
        val info = try {
            service.inspect(file)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        _ui.update { it.copy(pending = PendingRestore(info, file, temporary = true)) }
    }

    /** Same, for a backup kept on this phone. */
    fun prepareRestoreLocal(path: String) = launchBusy {
        val file = File(path)
        val info = service.inspect(file)
        _ui.update { it.copy(pending = PendingRestore(info, file, temporary = false)) }
    }

    fun cancelRestore() {
        _ui.value.pending?.let { if (it.temporary) it.source.delete() }
        _ui.update { it.copy(pending = null) }
    }

    /** Step 2: replaces all data on the phone with the verified backup, then restarts the app. */
    fun confirmRestore() {
        val pending = _ui.value.pending ?: return
        launchBusy {
            val result = service.restore(pending.source, app.session.user.value?.id)
            if (pending.temporary) pending.source.delete()
            BackupScheduler.setEnabled(app, result.autoBackupEnabled)
            _ui.update { it.copy(pending = null, message = BackupMessage.RESTORED) }
            delay(1_200)
            restartApp(app)
        }
    }

    fun setAuto(enabled: Boolean) {
        viewModelScope.launch {
            app.database.settings().upsert(AppSetting(BackupSettings.AUTO_ENABLED, enabled.toString()))
            BackupScheduler.setEnabled(app, enabled)
            refresh()
        }
    }

    private suspend fun audit(action: String) {
        app.database.audit().insert(
            AuditLog(userId = app.session.user.value?.id, action = action, entity = "backup", createdAt = System.currentTimeMillis()),
        )
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                _ui.update { it.copy(message = messageOf(e)) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = BackupMessage.FAILED) }
            } finally {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun messageOf(e: BackupException): BackupMessage = when (e) {
        is BackupException.NotZip, is BackupException.NotKonozBackup -> BackupMessage.NOT_BACKUP
        is BackupException.IncompatibleVersion -> BackupMessage.VERSION
        is BackupException.Corrupt -> BackupMessage.CORRUPT
        is BackupException.NoAdmin -> BackupMessage.NO_ADMIN
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BackupViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}

/** After a restore the database is closed, so the app starts again from the launcher (which opens the restored data). */
internal fun restartApp(context: Context) {
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
    }
    Runtime.getRuntime().exit(0)
}
