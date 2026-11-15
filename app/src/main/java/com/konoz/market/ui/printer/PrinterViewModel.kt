package com.konoz.market.ui.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.konoz.market.KonozApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PrinterMessage { TEST_SENT, NO_PRINTER, NO_PERMISSION, BLUETOOTH_OFF, FAILED }

data class PrinterUi(
    val devices: List<PrinterDevice> = emptyList(),
    val selectedAddress: String? = null,
    val selectedName: String? = null,
    val width: PaperWidth = PaperWidth.MM80,
    val hasPermission: Boolean = true,
    val busy: Boolean = false,
    val message: PrinterMessage? = null,
)

class PrinterViewModel(private val app: KonozApp) : ViewModel() {
    private val service get() = app.printService
    private val _ui = MutableStateFlow(PrinterUi())
    val ui: StateFlow<PrinterUi> = _ui.asStateFlow()

    init { refresh() }

    /** Also called after the permission dialog and when coming back from Android's Bluetooth settings. */
    fun refresh() {
        val devices = service.printer.pairedDevices()
        _ui.update {
            it.copy(
                devices = devices, hasPermission = service.printer.hasPermission(),
                selectedAddress = service.settings.address, selectedName = service.settings.name, width = service.settings.width,
            )
        }
    }

    fun select(device: PrinterDevice) {
        service.settings.address = device.address
        service.settings.name = device.name
        refresh()
    }

    fun setWidth(width: PaperWidth) {
        service.settings.width = width
        refresh()
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun printTest() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) { service.printTestPage() }
                PrinterMessage.TEST_SENT
            } catch (e: PrintException) {
                when (e.reason) {
                    PrintException.Reason.NO_PRINTER -> PrinterMessage.NO_PRINTER
                    PrintException.Reason.NO_PERMISSION -> PrinterMessage.NO_PERMISSION
                    PrintException.Reason.BLUETOOTH_OFF -> PrinterMessage.BLUETOOTH_OFF
                    else -> PrinterMessage.FAILED
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PrinterMessage.FAILED
            }
            _ui.update { it.copy(busy = false, message = message) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PrinterViewModel(this[APPLICATION_KEY] as KonozApp) }
        }
    }
}
