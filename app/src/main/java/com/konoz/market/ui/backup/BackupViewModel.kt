package com.konoz.market.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import com.konoz.market.domain.BackupException
import com.konoz.market.domain.BackupFailure
import com.konoz.market.domain.BackupManifest
import com.konoz.market.domain.BackupScheduler
import com.konoz.market.domain.BackupService
import com.konoz.market.domain.LocalBackup
import com.konoz.market.domain.Permission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BackupMessage { EXPORTED, LOCAL_SAVED, NOT_A_BACKUP, CORRUPT, NEWER, IO, NOT_ALLOWED }

/** A file the user picked (or a local backup) that passed validation and now waits for the confirmation. */
data class PendingRestore(val uri: Uri?, val localName: String?, val manifest: BackupManifest)

data class BackupUi(
    val local: List<LocalBackup> = emptyList(),
    val autoEnabled: Boolean = false,
    val busy: Boolean = false,
    val message: BackupMessage? = null,
    val pending: PendingRestore? = null,
)

class BackupViewModel(private val app: KonozApp) : ViewModel() {
    private val service get() = app.backupService
    private val _ui = MutableStateFlow(BackupUi())
    val ui: StateFlow<BackupUi> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = _ui.update { it.copy(local = service.listLocalBackups(), autoEnabled = BackupScheduler.isEnabled(app)) }

    fun suggestedName(): String =
        "konoz_backup_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + BackupService.EXTENSION

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun dismissPending() = _ui.update { it.copy(pending = null) }

    fun setAuto(enabled: Boolean) {
        if (!allowed()) return
        BackupScheduler.setEnabled(app, enabled)
        refresh()
    }

    fun exportTo(uri: Uri) = launchWork {
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("no stream")
        out.use { service.createBackup(it, app.session.user.value?.id) }
        BackupMessage.EXPORTED
    }

    fun saveLocal() = launchWork {
        service.createLocalBackup(automatic = false, actorId = app.session.user.value?.id)
        BackupMessage.LOCAL_SAVED
    }

    /** Step 1 of a restore: validate the picked file and show what it holds. */
    fun pickForRestore(uri: Uri) = launchWork {
        val manifest = app.contentResolver.openInputStream(uri)?.use { service.inspect(it) } ?: throw java.io.IOException("no stream")
        _ui.update { it.copy(pending = PendingRestore(uri, null, manifest)) }
        null
    }

    fun pickLocalForRestore(name: String) = launchWork {
        val file = service.localFile(name) ?: throw java.io.IOException("missing")
        val manifest = FileInputStream(file).use { service.inspect(it) }
        _ui.update { it.copy(pending = PendingRestore(null, name, manifest)) }
        null
    }

    /** Step 2: after the confirmation. The app restarts when it succeeds. */
    fun confirmRestore() {
        val pending = _ui.value.pending ?: return
        _ui.update { it.copy(pending = null) }
        launchWork {
            val actor = app.session.user.value?.id
            if (pending.uri != null) {
                app.contentResolver.openInputStream(pending.uri)?.use { service.restore(it, actor) } ?: throw java.io.IOException("no stream")
            } else {
                val file = service.localFile(pending.localName ?: "") ?: throw java.io.IOException("missing")
                FileInputStream(file).use { service.restore(it, actor) }
            }
            AppRestart.restart(app)
            null
        }
    }

    private fun allowed() = app.session.can(Permission.MANAGE_BACKUP)

    private fun launchWork(block: suspend () -> BackupMessage?) {
        if (!allowed()) { _ui.update { it.copy(message = BackupMessage.NOT_ALLOWED) }; return }
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: BackupException) {
                when (e.failure) {
                    BackupFailure.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
                    BackupFailure.CORRUPT -> BackupMessage.CORRUPT
                    BackupFailure.NEWER_VERSION -> BackupMessage.NEWER
                    BackupFailure.IO -> BackupMessage.IO
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                BackupMessage.IO
            }
            _ui.update { it.copy(busy = false, message = message, local = service.listLocalBackups()) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { BackupViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
