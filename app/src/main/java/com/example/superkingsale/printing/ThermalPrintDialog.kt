package com.example.superkingsale.printing

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.*
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.superkingsale.SalesApplication
import com.example.superkingsale.ui.*
import kotlinx.coroutines.launch

/** Explicit selections only: no discovery scanning, no automatic writes after permission grants/recreation. */
class ThermalPrintDialog : DialogFragment() {
    private val app get() = requireActivity().application as SalesApplication
    private val vm: ThermalPrintViewModel by viewModels { viewModelFactory { initializer { ThermalPrintViewModel(app, createSavedStateHandle()) } } }
    private val prefs get() = requireContext().getSharedPreferences("printer.${requireArguments().getLong("userId")}", Context.MODE_PRIVATE)
    private var controls: LinearLayout? = null
    private var status: TextView? = null
    private var usbReceiver: BroadcastReceiver? = null
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let(vm::export) }
    private val nearby = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.message(if (granted) "Permission granted. Select the printer, then press Print again." else "Permission denied. Allow Nearby devices in app settings to use this connection.")
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val context = requireContext()
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(context.dp(20), 0, context.dp(20), context.dp(12)) }
        status = root.label("")
        val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }; root.addView(form); controls = form
        fun pref(key: String, fallback: String) = prefs.getString(key, fallback).orEmpty()
        var connection = pref("connection", "bluetooth")
        var paper = prefs.getInt("paper", 58)
        var bluetooth = pref("bluetooth", "")
        var usbName = pref("usb", "")
        var usbInterface = prefs.getInt("usbInterface", -1)
        val sections = mutableMapOf<String, LinearLayout>()
        var refreshSelection: () -> Unit = {}
        fun updateConnection() { sections.forEach { (key, section) -> section.isVisible = key == connection }; refreshSelection() }
        fun section(key: String) = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; sections[key] = this; form.addView(this) }
        fun save(key: String, value: String) { prefs.edit().putString(key, value).apply() }
        form.label("Direct printing requires ESC/POS raster support. Bluetooth uses paired Classic/SPP devices, not BLE. USB requires OTG. Use Android Print for other printer protocols.")
        form.choice("Connection", listOf("bluetooth" to "Bluetooth", "usb" to "USB / OTG", "tcp" to "Wi-Fi / TCP"), connection) { connection = it; save("connection", it); updateConnection() }
        lateinit var dots: com.google.android.material.textfield.TextInputEditText
        form.choice("Paper width", listOf("50" to "50 mm", "58" to "58 mm", "80" to "80 mm"), paper.toString()) {
            paper = it.toInt(); prefs.edit().putInt("paper", paper).apply(); dots.setText(EscPos.defaultDots(paper).toString())
        }
        dots = form.field("Printable dots", prefs.getInt("dots", EscPos.defaultDots(paper)).toString(), InputType.TYPE_CLASS_NUMBER) { it.toIntOrNull()?.let { value -> prefs.edit().putInt("dots", value).apply() } }
        form.label("Use the printer's printable dot width, not roll width. Typical 203 dpi: 58 mm = 384 dots; 80 mm = 576 dots. Adjust after a test print.")
        val large = CheckBox(context).apply { text = context.tr("Larger receipt text"); isChecked = prefs.getBoolean("large", false); setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("large", checked).apply() } }; form.addView(large)
        val cut = CheckBox(context).apply { text = context.tr("Cut paper after printing (supported cutters only)"); isChecked = prefs.getBoolean("cut", false); setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("cut", checked).apply() } }; form.addView(cut)
        val selected = form.label("")
        fun selection() { selected.text = context.tr("Selected device") + ": " + when (connection) {
            "bluetooth" -> bluetooth.ifBlank { context.tr("Not selected") }
            "usb" -> if (usbName.isBlank()) context.tr("Not selected") else "$usbName / $usbInterface"
            else -> context.tr("Use the host and port below.")
        } }
        refreshSelection = ::selection
        val bluetoothSection = section("bluetooth")
        bluetoothSection.button("Select paired Bluetooth printer") {
            if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                nearby.launch(Manifest.permission.BLUETOOTH_CONNECT); return@button
            }
            try {
                val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                val devices = if (adapter?.isEnabled == true) adapter.bondedDevices.toList() else emptyList()
                if (devices.isEmpty()) { vm.message("Turn on Bluetooth and pair the printer in Android settings."); return@button }
                LocalizedDialogBuilder(context).setTitle("Select paired Bluetooth printer")
                    .setItems(devices.map { "${it.name.orEmpty()} · ${it.address}" }.toTypedArray()) { _, index ->
                        bluetooth = devices[index].address; save("bluetooth", bluetooth)
                        selection(); vm.message("Printer selected. Choose the matching connection above before printing.")
                    }.setNegativeButton("Cancel", null).show()
            } catch (_: SecurityException) { vm.message("Permission denied. Allow Nearby devices in app settings to use this connection.") }
        }
        bluetoothSection.button("Bluetooth settings") { runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } }
        val usbSection = section("usb")
        usbSection.button("Select USB printer") {
            val usb = context.getSystemService(UsbManager::class.java)
            val candidates = usb.deviceList.values.flatMap { device -> (0 until device.interfaceCount).map(device::getInterface)
                .filter { iface -> iface.interfaceClass in listOf(UsbConstants.USB_CLASS_PRINTER, UsbConstants.USB_CLASS_VENDOR_SPEC) &&
                    (0 until iface.endpointCount).map(iface::getEndpoint).any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT } }
                .map { device to it } }
            if (candidates.isEmpty()) { vm.message("No USB bulk-output printer found. Connect an OTG printer, then retry."); return@button }
            LocalizedDialogBuilder(context).setTitle("Select USB printer")
                .setItems(candidates.map { (device, iface) -> "USB ${device.vendorId}:${device.productId} · ${iface.id}" }.toTypedArray()) { _, index ->
                    val (device, iface) = candidates[index]
                    usbName = device.deviceName; usbInterface = iface.id; save("usb", usbName); prefs.edit().putInt("usbInterface", usbInterface).apply()
                    selection()
                    if (!usb.hasPermission(device)) {
                        unregisterUsb()
                        val action = "${context.packageName}.USB_PRINTER.${java.util.UUID.randomUUID()}"
                        usbReceiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                if (intent.action != action) return
                                vm.message(if (usb.hasPermission(device)) "USB permission granted. Press Print to continue." else "USB permission denied.")
                                unregisterUsb()
                            }
                        }
                        ContextCompat.registerReceiver(context, usbReceiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
                        usb.requestPermission(device, PendingIntent.getBroadcast(context, 0, Intent(action).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    }
                }.setNegativeButton("Cancel", null).show()
        }
        val tcpSection = section("tcp")
        val host = tcpSection.field("Printer host / IP", pref("host", ""), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) { save("host", it.trim()) }
        val port = tcpSection.field("TCP port", pref("port", "9100"), InputType.TYPE_CLASS_NUMBER) { save("port", it) }
        tcpSection.label("TCP printing is unencrypted. Use a trusted local network and verify the printer address.")
        updateConnection()
        fun settings(): Int? = dots.text.toString().toIntOrNull()?.takeIf { it in 200..832 && it % 8 == 0 }
        fun print(test: Boolean) {
            val width = settings() ?: run { vm.message("Printable dots must be a multiple of 8 from 200 to 832."); return }
            if (connection == "bluetooth" && Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                nearby.launch(Manifest.permission.BLUETOOTH_CONNECT); return
            }
            if (connection == "tcp" && Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
                nearby.launch("android.permission.ACCESS_LOCAL_NETWORK"); return
            }
            val target = PrinterTarget(connection, if (connection == "bluetooth") bluetooth else host.text.toString().trim(), port.text.toString().toIntOrNull() ?: 0, usbName, usbInterface)
            if (connection == "tcp" && target.address.any { it == '/' || it.isWhitespace() }) { vm.message("Enter a printer host or IP address without a URL scheme or path."); return }
            if ((connection == "bluetooth" && bluetooth.isBlank()) || (connection == "usb" && usbName.isBlank()) ||
                (connection == "tcp" && (target.address.isBlank() || target.port !in 1..65535))) { vm.message("Select a printer and check its connection details."); return }
            LocalizedDialogBuilder(context).setTitle(if (test) "Print test receipt?" else "Print invoice?")
                .setMessage(context.tr("Confirm the selected printer. A retry may print another copy.") + "\n$connection · ${if (connection == "usb") usbName else target.address}\n$paper mm / $width dots")
                .setNegativeButton("Cancel", null).setPositiveButton("Print") { _, _ -> vm.print(target, width, large.isChecked, cut.isChecked, test) }.show()
        }
        form.button("Print test receipt") { print(true) }
        val isInvoice = requireArguments().getLong("saleId") > 0
        if (isInvoice) form.button("Print this invoice") { print(false) }
        form.button("Save exact-width PDF") {
            val width = settings() ?: run { vm.message("Printable dots must be a multiple of 8 from 200 to 832."); return@button }
            export.launch(vm.prepareExport(paper, width, large.isChecked, !isInvoice))
        }
        root.button("Retry loading invoice") { vm.load(requireArguments().getLong("saleId")) }
        root.button("Cancel active print") { vm.cancel() }
        vm.load(requireArguments().getLong("saleId"))
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect { state ->
            status?.text = context.tr(state.message); controls?.enableChildren(!state.busy && state.ready)
            isCancelable = !state.busy
            (dialog as? androidx.appcompat.app.AlertDialog)?.getButton(-2)?.isEnabled = !state.busy
        } } }
        return LocalizedDialogBuilder(context).setTitle("Thermal printer / PDF")
            .setView(ScrollView(context).apply { addView(root) }).setNegativeButton("Close", null).create()
    }
    private fun unregisterUsb() { usbReceiver?.let { runCatching { context?.unregisterReceiver(it) } }; usbReceiver = null }
    override fun onDestroyView() { unregisterUsb(); status = null; controls = null; super.onDestroyView() }
    companion object { fun forSale(id: Long = 0, userId: Long = 0) = ThermalPrintDialog().apply {
        arguments = Bundle().apply { putLong("saleId", id); putLong("userId", userId) }
    } }
}
