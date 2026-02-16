package com.example.ballzooka

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

class BluetoothSensorCharacteristics(
    heading: BluetoothGattCharacteristic,
    position: BluetoothGattCharacteristic,
    battery: BluetoothGattCharacteristic,
    rpm: BluetoothGattCharacteristic
) {
    val heading: BluetoothGattCharacteristic = heading
    val position: BluetoothGattCharacteristic = position
    val battery: BluetoothGattCharacteristic = battery
    val rpm: BluetoothGattCharacteristic = rpm
}

class BluetoothScanner(
    context: Context
) {
    private val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    val scanner: BluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    suspend fun findDevice(
        serviceUuid: UUID,
        timeoutMs: Long = 10_000
    ): BluetoothDevice? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    scanner.stopScan(this)
                    continuation.resume(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    continuation.resume(null)
                }
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(listOf(filter), settings, scanCallback)

            continuation.invokeOnCancellation {
                scanner.stopScan(scanCallback)
            }
        }
    }
}

@SuppressLint("MissingPermission")
class BluetoothMessenger(val context: Context) {

    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData.asSharedFlow()

//    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    private var characteristic: BluetoothGattCharacteristic? = null

    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionStatus.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionStatus.DISCONNECTED
                    characteristic = null
                    gatt.close()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SENSOR_SERVICE_UUID)

                if (service != null) {
                    enableNotifications()
                    _connectionState.value = ConnectionStatus.CONNECTED
                } else {
                    _connectionState.value = ConnectionStatus.ERROR
                }
            } else {
                _connectionState.value = ConnectionStatus.ERROR
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            // change this later
            _incomingData.tryEmit(value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            // change this later
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _incomingData.tryEmit(value)
            }
        }
    }

    fun connect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    private fun enableNotifications() {
//        characteristic?.let { char ->
//            gatt?.setCharacteristicNotification(char, true)
//
//            val descriptor = char.getDescriptor(CCC_DESCRIPTOR_UUID)
//            descriptor?.let {
//                gatt?.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
//            }
//        }
    }

    fun send(data: ByteArray) {
        characteristic?.let { char ->
            gatt?.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
    }

    fun read() {
        characteristic?.let { char ->
            gatt?.readCharacteristic(char)
        }
    }
}