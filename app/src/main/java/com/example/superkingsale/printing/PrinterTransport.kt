package com.example.superkingsale.printing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.*
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class PrinterTarget(val connection: String, val address: String = "", val port: Int = 9100, val usbName: String = "", val usbInterface: Int = -1)

/** One job/connection; cancellation closes the transport. Never retries or reports printer acknowledgement. */
class PrinterTransport(private val context: Context) : Closeable {
    private val closed = AtomicBoolean(false)
    private val connection = AtomicReference<Closeable?>()
    private fun own(resource: Closeable) {
        if (closed.get()) { resource.close(); throw IOException("Print cancelled") }
        connection.set(resource)
        if (closed.get()) { connection.getAndSet(null)?.close(); throw IOException("Print cancelled") }
    }
    @SuppressLint("MissingPermission")
    fun send(target: PrinterTarget, chunks: Sequence<ByteArray>, cut: Boolean, progress: (Int) -> Unit) {
        check(!closed.get())
        var write: (ByteArray) -> Unit
        when (target.connection) {
            "tcp" -> {
                require(target.address.isNotBlank() && target.address.none { it.isWhitespace() || it == '/' } && target.port in 1..65535)
                val socket = Socket(); own(socket)
                socket.connect(InetSocketAddress(target.address, target.port), 8000)
                socket.tcpNoDelay = true
                write = { socket.getOutputStream().write(it); socket.getOutputStream().flush() }
            }
            "bluetooth" -> {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth is unavailable.")
                check(adapter.isEnabled) { "Turn on Bluetooth and pair the printer in Android settings." }
                val device = adapter.bondedDevices.firstOrNull { it.address == target.address } ?: error("Select a paired Bluetooth printer.")
                val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                own(socket); socket.connect()
                write = { bytes ->
                    for (offset in bytes.indices step 1024) { socket.outputStream.write(bytes, offset, minOf(1024, bytes.size - offset)); Thread.sleep(8) }
                    socket.outputStream.flush()
                }
            }
            "usb" -> {
                val usb = context.getSystemService(UsbManager::class.java)
                val device = usb.deviceList[target.usbName] ?: error("USB printer disconnected.")
                check(usb.hasPermission(device)) { "USB permission is required. Select the printer again." }
                val iface = (0 until device.interfaceCount).map(device::getInterface).firstOrNull { it.id == target.usbInterface }
                    ?: error("Select a USB printer interface.")
                val endpoint = (0 until iface.endpointCount).map(iface::getEndpoint).firstOrNull {
                    it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT
                } ?: error("USB interface has no bulk output endpoint.")
                val deviceConnection = usb.openDevice(device) ?: error("Cannot open USB printer.")
                own(Closeable { deviceConnection.close() })
                check(deviceConnection.claimInterface(iface, true)) { "USB printer is busy." }
                write = { bytes ->
                    var offset = 0
                    while (offset < bytes.size) {
                        if (closed.get()) throw IOException("Print cancelled")
                        val sent = deviceConnection.bulkTransfer(endpoint, bytes, offset, minOf(4096, bytes.size - offset), 5000)
                        if (sent <= 0) throw IOException("USB write failed. Check the printout before retrying.")
                        offset += sent
                    }
                }
            }
            else -> error("Select a printer connection.")
        }
        try {
            write(EscPos.initialize)
            var count = 0
            for (chunk in chunks) { if (closed.get()) throw IOException("Print cancelled"); write(chunk); progress(++count) }
            write(EscPos.feed)
            if (cut) write(EscPos.cut)
        } finally { close() }
    }
    override fun close() { closed.set(true); runCatching { connection.getAndSet(null)?.close() } }
}
