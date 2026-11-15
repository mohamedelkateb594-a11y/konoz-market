package com.konoz.market.ui.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

/** A paired Bluetooth device the user may pick as the receipt printer. */
data class PrinterDevice(val name: String, val address: String)

/** Sends bytes to a paired Bluetooth (SPP) receipt printer. Blocking: call from a background dispatcher. */
class ThermalPrinter(private val context: Context) {

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun adapter() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    /** Paired devices; empty without permission or when Bluetooth is off. Pairing itself is done in Android settings. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PrinterDevice> {
        if (!hasPermission()) return emptyList()
        val adapter = adapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.map { PrinterDevice(it.name ?: it.address, it.address) }.sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun isBluetoothOn(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    @Throws(IOException::class)
    fun send(address: String, bytes: ByteArray) {
        if (!hasPermission()) throw IOException("no Bluetooth permission")
        val adapter = adapter() ?: throw IOException("no Bluetooth")
        if (!adapter.isEnabled) throw IOException("Bluetooth is off")
        val socket = try {
            adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP)
        } catch (e: IllegalArgumentException) {
            throw IOException("bad address", e)
        } catch (e: SecurityException) {
            throw IOException("no Bluetooth permission", e)
        }
        try {
            adapter.cancelDiscovery()
            socket.connect()
            val out = socket.outputStream
            // Small chunks: cheap printers have tiny buffers and drop data otherwise.
            var offset = 0
            while (offset < bytes.size) {
                val n = minOf(CHUNK, bytes.size - offset)
                out.write(bytes, offset, n)
                out.flush()
                offset += n
                Thread.sleep(20)
            }
            Thread.sleep(300) // let the printer drain before the link closes
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("interrupted", e)
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                // nothing more to do
            }
        }
    }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHUNK = 512
    }
}
