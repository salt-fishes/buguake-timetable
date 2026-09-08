package com.buguake.timetable.campus

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.random.Random

/** 开门流程状态（UI 渲染进度与提示）。 */
data class UnlockUiState(
    val running: Boolean = false,
    val phase: String = "",
    val done: Boolean = false,
    val failed: Boolean = false,
)

/**
 * 蓝牙开门执行器：扫描（按服务 UUID 过滤）/ MAC 直连 → 发现服务 → 订阅 notify →
 * 写开门指令 → 完成（随后断开）。
 *
 * 指令格式移植自 zxy19/yunmei_unintelligent（MIT）UnlockService.getPwd：
 * 0xD0, len=secret.len+14, secret, 0xA5, 6 位随机数字（每字节一位）, "ID01", 0xA7。
 * 使用 Android 原生 BLE API（原项目依赖 FastBLE 库，此处不引入）。
 */
class BleUnlocker(private val context: Context) {

    private val _state = MutableStateFlow(UnlockUiState())
    val state: StateFlow<UnlockUiState> = _state

    private var job: Job? = null

    fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun unlock(lock: YmLock) {
        if (_state.value.running) return
        _state.value = UnlockUiState(running = true, phase = "准备中…")
        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                performUnlock(lock)
                _state.value = UnlockUiState(running = false, phase = "开门完成", done = true)
            } catch (t: Throwable) {
                _state.value = UnlockUiState(running = false, phase = t.message ?: "开门失败", failed = true)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun performUnlock(lock: YmLock) {
        val adapter = bluetoothAdapter() ?: throw YunmeiException("设备不支持蓝牙")
        if (!adapter.isEnabled) throw YunmeiException("请先开启蓝牙")

        val written = CompletableDeferred<Unit>()
        val gatt = if (lock.mac.isNotBlank()) {
            setPhase("快速连接中…")
            // 快速直连失败（连不上/掉线/超时）自动回退扫描
            try {
                connectGatt(adapter.getRemoteDevice(lock.mac), lock, written)
            } catch (t: Throwable) {
                setPhase("快速连接失败，改用扫描…")
                connectGatt(scanFirstDevice(adapter, lock).device, lock, written)
            }
        } else {
            connectGatt(scanFirstDevice(adapter, lock).device, lock, written)
        }

        setPhase("写入开门指令…")
        val ok = withTimeoutOrNull(12_000) { written.await() }
        if (ok == null) {
            runCatching { gatt.disconnect(); gatt.close() }
            throw YunmeiException("开门超时，请靠近宿舍门重试")
        }
        // 留出门锁执行时间，随后断开
        delay(600)
        withContext(Dispatchers.IO) { runCatching { gatt.disconnect(); gatt.close() } }
    }

    private fun setPhase(text: String) {
        _state.value = _state.value.copy(phase = text)
    }

    /** 扫描第一个广播目标服务 UUID 的设备（15 秒超时）。 */
    @SuppressLint("MissingPermission")
    private suspend fun scanFirstDevice(adapter: BluetoothAdapter, lock: YmLock): android.bluetooth.le.ScanResult =
        suspendCancellableCoroutine { cont ->
            val scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                cont.resumeWithException(YunmeiException("蓝牙扫描不可用"))
                return@suspendCancellableCoroutine
            }
            setPhase("扫描门锁（请靠近宿舍门）…")
            lateinit var scanCb: android.bluetooth.le.ScanCallback
            scanCb = object : android.bluetooth.le.ScanCallback() {
                override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                    runCatching { scanner.stopScan(this) }
                    setPhase("找到门锁，连接中…")
                    if (cont.isActive) cont.resume(result)
                }

                override fun onScanFailed(errorCode: Int) {
                    if (cont.isActive) cont.resumeWithException(YunmeiException("扫描失败（错误码 $errorCode）"))
                }
            }
            val filter = android.bluetooth.le.ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(UUID.fromString(lock.serviceUuid)))
                .build()
            scanner.startScan(
                listOf(filter),
                android.bluetooth.le.ScanSettings.Builder()
                    .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCb,
            )
            CoroutineScope(Dispatchers.IO).launch {
                delay(15_000)
                runCatching { scanner.stopScan(scanCb) }
                if (cont.isActive) cont.resumeWithException(YunmeiException("附近未发现门锁，请靠近宿舍门重试"))
            }
        }

    /** 连接并走完整链路；任何一步失败都会以异常完结 [written]。 */
    @SuppressLint("MissingPermission")
    private fun connectGatt(
        device: android.bluetooth.BluetoothDevice,
        lock: YmLock,
        written: CompletableDeferred<Unit>,
    ): BluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
        private fun fail(message: String) {
            setPhase(message)
            if (!written.isCompleted) written.completeExceptionally(YunmeiException(message))
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (!written.isCompleted) fail("连接已断开")
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return fail("服务发现失败")
            val service = gatt.getService(UUID.fromString(lock.serviceUuid))
            val notifyChar = service?.getCharacteristic(UUID.fromString(lock.notifyCharUuid))
            val writeChar = service?.getCharacteristic(UUID.fromString(lock.writeCharUuid))
            if (service == null || notifyChar == null || writeChar == null) return fail("门锁特征值缺失")
            gatt.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                if (!gatt.writeDescriptor(cccd)) fail("订阅失败")
            } else {
                writeCommand(gatt, writeChar)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return fail("订阅失败（状态 $status）")
            val service = gatt.getService(UUID.fromString(lock.serviceUuid))
            val writeChar = service?.getCharacteristic(UUID.fromString(lock.writeCharUuid))
            if (writeChar != null) writeCommand(gatt, writeChar)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                setPhase("开门完成")
                written.complete(Unit)
            } else {
                fail("指令写入失败（状态 $status）")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            android.util.Log.i(
                "WebImport",
                "门锁回执: ${characteristic.value?.joinToString("") { "%02x".format(it) }}"
            )
        }

        @SuppressLint("MissingPermission")
        private fun writeCommand(gatt: BluetoothGatt, writeChar: BluetoothGattCharacteristic) {
            val payload = buildUnlockPayload(lock.secret)
            val ok: Boolean = if (android.os.Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(writeChar, payload, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    writeChar.value = payload
                    gatt.writeCharacteristic(writeChar)
                }
            }
            if (!ok) fail("指令写入被拒绝")
        }
    }, android.bluetooth.BluetoothDevice.TRANSPORT_LE)

    /** 开门指令 payload（协议见类注释）。 */
    internal fun buildUnlockPayload(secret: String): ByteArray {
        val out = ByteArrayOutputStream()
        var pw = Random.nextInt(1_000_000)
        out.write(0xD0)
        out.write(secret.length + 2 + 2 + 10)
        out.write(secret.toByteArray(Charsets.UTF_8))
        out.write(0xA5)
        repeat(6) {
            out.write(pw % 10)
            pw /= 10
        }
        out.write(0x49) // I
        out.write(0x44) // D
        out.write(0x30) // 0
        out.write(0x31) // 1
        out.write(0xA7)
        return out.toByteArray()
    }
}
