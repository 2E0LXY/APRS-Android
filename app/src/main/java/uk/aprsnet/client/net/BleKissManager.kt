package uk.aprsnet.client.net

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Manages a BLE connection to a KISS-over-BLE radio (Radtel RT-950 Pro or compatible).
 * Receives AX.25 UI frames encapsulated in KISS framing, decodes them to raw APRS
 * packet strings, and emits them on [rawPackets] — the same pipeline used by the
 * WebSocket feed so the station map updates identically.
 *
 * BLE protocol (reverse-engineered by mecta02/aprs):
 *   Service  : 0000FFE0-0000-1000-8000-00805F9B34FB
 *   Notify   : 0000FFE1-0000-1000-8000-00805F9B34FB  (RX)
 *   Write    : 0000FF31-0000-1000-8000-00805F9B34FB  (TX)
 *   Framing  : KISS — delimiter 0xC0, ESC 0xDB
 *   L2       : AX.25 UI — ctrl=0x03, pid=0xF0
 *   Position : Mic-E (6-char AX.25 destination field)
 */
class BleKissManager(private val appCtx: Context) {

    enum class BleState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    companion object {
        private val SVC_UUID  = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        private val CHAR_UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val TARGET_PREFIXES = listOf("RT-950", "RT950")

        private const val FEND: Byte  = 0xC0.toByte()
        private const val FESC: Byte  = 0xDB.toByte()
        private const val TFEND: Byte = 0xDC.toByte()
        private const val TFESC: Byte = 0xDD.toByte()

        private const val SCAN_TIMEOUT_MS  = 15_000L
        private const val RECONNECT_DELAY_MS = 8_000L
    }

    /** Raw APRS packet strings decoded from BLE. Consumed by AprsViewModel. */
    val rawPackets = MutableSharedFlow<String>(extraBufferCapacity = 256)

    private val _state = MutableStateFlow(BleState.DISCONNECTED)
    val state: StateFlow<BleState> = _state.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _pktCount = MutableStateFlow(0)
    val pktCount: StateFlow<Int> = _pktCount.asStateFlow()

    private val handler      = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var isScanning   = false
    private var shouldReconnect = false
    private val kissBuffer   = mutableListOf<Byte>()

    // ── Permissions ──────────────────────────────────────────────────────────
    fun hasPermissions(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        checkPerm(Manifest.permission.BLUETOOTH_SCAN) &&
        checkPerm(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        checkPerm(Manifest.permission.BLUETOOTH)
    }

    private fun checkPerm(p: String) =
        ContextCompat.checkSelfPermission(appCtx, p) == PackageManager.PERMISSION_GRANTED

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.BLUETOOTH)
    }

    // ── Public API ───────────────────────────────────────────────────────────
    fun scan() {
        if (isScanning || _state.value == BleState.CONNECTED || _state.value == BleState.CONNECTING) return
        if (!hasPermissions()) return
        val btMgr  = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val scanner = btMgr.adapter?.bluetoothLeScanner ?: return

        shouldReconnect = true
        _state.value  = BleState.SCANNING
        isScanning    = true

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SVC_UUID)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        scanner.startScan(filters, settings, scanCallback)

        handler.postDelayed({
            if (isScanning) {
                scanner.stopScan(scanCallback)
                isScanning = false
                if (_state.value == BleState.SCANNING) _state.value = BleState.DISCONNECTED
            }
        }, SCAN_TIMEOUT_MS)
    }

    fun disconnect() {
        shouldReconnect = false
        val btMgr = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (isScanning) {
            runCatching { btMgr?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
            isScanning = false
        }
        gatt?.close(); gatt = null
        _state.value      = BleState.DISCONNECTED
        _deviceName.value = null
        _pktCount.value   = 0
        kissBuffer.clear()
    }

    // ── BLE scan callback ────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            if (TARGET_PREFIXES.none { name.startsWith(it, ignoreCase = true) }) return
            val btMgr = appCtx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            btMgr?.adapter?.bluetoothLeScanner?.stopScan(this)
            isScanning = false
            connectGatt(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            isScanning  = false
            _state.value = BleState.DISCONNECTED
        }
    }

    // ── GATT connection ──────────────────────────────────────────────────────
    private fun connectGatt(device: BluetoothDevice) {
        _state.value = BleState.CONNECTING
        gatt = device.connectGatt(appCtx, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close(); gatt = null
                    _state.value      = BleState.DISCONNECTED
                    _deviceName.value = null
                    if (shouldReconnect) {
                        handler.postDelayed({ scan() }, RECONNECT_DELAY_MS)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                g.close(); gatt = null; _state.value = BleState.DISCONNECTED; return
            }
            val ch = g.getService(SVC_UUID)?.getCharacteristic(CHAR_UUID) ?: run {
                g.close(); gatt = null; _state.value = BleState.DISCONNECTED; return
            }
            g.setCharacteristicNotification(ch, true)
            ch.getDescriptor(CCCD_UUID)?.let { desc ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(desc)
                }
            }
            _state.value      = BleState.CONNECTED
            _deviceName.value = g.device.name
            kissBuffer.clear()
            _pktCount.value = 0
        }

        // API 33+
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) =
            processBytes(value)

        // API < 33
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            processBytes(ch.value ?: return)
        }
    }

    // ── KISS + AX.25 decoding ────────────────────────────────────────────────
    private fun processBytes(bytes: ByteArray) {
        kissBuffer.addAll(bytes.toList())
        if (kissBuffer.size > 16384) repeat(kissBuffer.size - 8192) { kissBuffer.removeAt(0) }

        extractFrames().forEach { frame ->
            ax25ToAprs(frame)?.let { pkt ->
                rawPackets.tryEmit(pkt)
                _pktCount.value++
            }
        }
    }

    private fun extractFrames(): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        var fStart = -1

        for (i in 0..kissBuffer.size) {
            val b = if (i < kissBuffer.size) kissBuffer[i] else FEND
            if (b == FEND) {
                if (fStart >= 0 && i > fStart + 1) {
                    val raw = mutableListOf<Byte>()
                    var j = fStart + 1
                    while (j < i) {
                        if (kissBuffer[j] == FESC && j + 1 < i) {
                            j++
                            raw.add(if (kissBuffer[j] == TFEND) FEND else if (kissBuffer[j] == TFESC) FESC else kissBuffer[j])
                        } else {
                            raw.add(kissBuffer[j])
                        }
                        j++
                    }
                    if (raw.size >= 16) frames.add(raw.toByteArray())
                }
                fStart = i
            }
        }

        // Trim processed bytes
        if (fStart > 0) repeat(fStart) { kissBuffer.removeAt(0) }
        return frames
    }

    private fun ax25ToAprs(frame: ByteArray): String? {
        if (frame.size < 17) return null
        var off = 0

        // KISS command byte — 0x00 = data frame
        if ((frame[off].toInt() and 0x0F) != 0x00) return null
        off++

        data class Addr(val call: String, val last: Boolean, val hBit: Boolean)

        fun decodeAddr(o: Int): Addr {
            var c = ""
            for (k in 0 until 6) {
                val ch = ((frame[o + k].toInt() and 0xFF) ushr 1) and 0x7F
                if (ch > 0x20 && ch < 0x7F) c += ch.toChar()
            }
            val sb   = frame[o + 6].toInt() and 0xFF
            val ssid = (sb ushr 1) and 0x0F
            return Addr(
                call  = c.trim() + if (ssid != 0) "-$ssid" else "",
                last  = (sb and 0x01) != 0,
                hBit  = (sb and 0x80) != 0
            )
        }

        if (off + 14 > frame.size) return null
        val dst = decodeAddr(off); off += 7
        val src = decodeAddr(off); off += 7

        val digis = mutableListOf<String>()
        var last  = src.last
        while (!last && off + 7 <= frame.size) {
            val dg = decodeAddr(off); off += 7
            digis.add(dg.call + if (dg.hBit) "*" else "")
            last = dg.last
        }

        if (off + 2 > frame.size) return null
        val ctrl = frame[off++].toInt() and 0xFF
        val pid  = frame[off++].toInt() and 0xFF
        if (ctrl != 0x03 || pid != 0xF0) return null   // AX.25 UI only

        val info = String(frame.sliceArray(off until frame.size), Charsets.ISO_8859_1)
        if (info.isBlank()) return null

        val path = (listOf(dst.call) + digis).joinToString(",")
        return "${src.call}>$path:$info"
    }
}
