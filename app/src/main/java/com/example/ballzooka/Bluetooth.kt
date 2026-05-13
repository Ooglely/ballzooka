package com.example.ballzooka

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.coroutines.resume

class BluetoothSensorCharacteristics(
    val heading: BluetoothGattCharacteristic,
    val latitude: BluetoothGattCharacteristic,
    val longitude: BluetoothGattCharacteristic,
    val battery: BluetoothGattCharacteristic,
    val leftrpm: BluetoothGattCharacteristic,
    val rightrpm: BluetoothGattCharacteristic,
    val safety: BluetoothGattCharacteristic,
    val pitch: BluetoothGattCharacteristic,

    val commandFlywheelRPM: BluetoothGattCharacteristic,
    val commandLoadwheelYaw: BluetoothGattCharacteristic,
    val commandLoadwheelPitch: BluetoothGattCharacteristic
) {
    val values: List<BluetoothGattCharacteristic> = listOf(latitude, heading, longitude, battery, leftrpm, rightrpm, safety, pitch, commandLoadwheelYaw, commandFlywheelRPM, commandLoadwheelPitch)
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
                    super.onScanResult(callbackType, result)
                    Log.w("Ballzooka", "onScanResult: $result")
                    scanner.stopScan(this)
                    continuation.resume(result.device)
                }

                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    Log.w("Ballzooka", "onScanFailed: $errorCode")
                    continuation.resume(null)
                }
            }

            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            Log.w("Ballzooka", "starting scan")
            scanner.startScan(listOf(filter), settings, scanCallback)

            continuation.invokeOnCancellation {
                scanner.stopScan(scanCallback)
            }
        }
    }
}

@SuppressLint("MissingPermission")
class BluetoothMessenger(val context: Context, val scope: CoroutineScope) {

    private val _receivedData = MutableSharedFlow<ByteArray>()
    val receivedData: SharedFlow<ByteArray> = _receivedData.asSharedFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
//    val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter

    private var gatt: BluetoothGatt? = null
    var characteristics: BluetoothSensorCharacteristics? = null

    private val _incomingData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("Ballzooka", "onConnectionStateChange: $newState");
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
//                    _connectionState.value = ConnectionStatus.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("Ballzooka", "Handling disconnection")
                    characteristics = null
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SENSOR_SERVICE_UUID)

                if (service != null) {
                    Log.w("Ballzooka", "onServicesDiscovered: $service")
                    initializeSensorData()
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
            Log.w("BallzookaBT", "onCharacteristicChanged: ${value.toHexString(HexFormat.Default)}, ${characteristic.uuid}")
            value.reverse()
            when (characteristic) {
                characteristics!!.heading -> {
                    val heading: Double = ByteBuffer.wrap(value).double
                    Log.d("BallzookaBT", "heading: $heading")
                    _telemetry.update { it.copy(heading = heading) }
                }
                characteristics!!.pitch -> {
                    val pitch: Double = ByteBuffer.wrap(value).double
                    Log.d("BallzookaBT", "pitch: $pitch")
                    _telemetry.update { it.copy(pitch = pitch) }
                }
                characteristics!!.latitude -> {
                    val latitude: Double = ByteBuffer.wrap(value).double
                    Log.d("BallzookaBT", "latitude: $latitude")
                    _telemetry.update { it.copy(latitude = latitude) }
                }
                characteristics!!.longitude -> {
                    val longitude: Double = ByteBuffer.wrap(value).double
                    Log.d("BallzookaBT", "longitude: $longitude")
                    _telemetry.update { it.copy(longitude = longitude) }
                }
                characteristics!!.safety -> {
                    val safety: Boolean = value[0].toInt() == 1
                    Log.d("BallzookaBT", "safety: $safety")
                    _telemetry.update { it.copy(safety = safety)}
                }
                characteristics!!.leftrpm -> {
                    val rpm: Int = ByteBuffer.wrap(value).int
                    if (rpm < 10000) {
                        Log.w("Ballzooka", "leftrpm: $rpm")
                        _telemetry.update { it.copy(leftrpm = rpm) }
                    }
                }
                characteristics!!.rightrpm -> {
                    val rpm: Int = ByteBuffer.wrap(value).int
                    if (rpm < 10000) {
                        Log.w("Ballzooka", "rightrpm: $rpm")
                        _telemetry.update { it.copy(rightrpm = rpm) }
                    }
                }
            }
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

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.i("Ballzooka", "onDescriptorWrite: $status")
        }
    }

    fun initializeSensorData() {
        characteristics = BluetoothSensorCharacteristics(
            heading = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(HEADING_CHARACTERISTIC_UUID),
            latitude = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(LATITUDE_CHARACTERISTIC_UUID),
            longitude = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(LONGITUDE_CHARACTERISTIC_UUID),
            battery = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(BATTERY_CHARACTERISTIC_UUID),
            leftrpm = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(LEFTRPM_CHARACTERISTIC_UUID),
            rightrpm = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(RIGHTRPM_CHARACTERISTIC_UUID),
            safety = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(SAFETY_CHARACTERISTIC_UUID),
            pitch = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(PITCH_CHARACTERISTIC_UUID),
            commandFlywheelRPM = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(COMMAND_FLYWHEEL_RPM_CHARACTERISTIC_UUID),
            commandLoadwheelYaw = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(COMMAND_LOADWHEEL_YAW_CHARACTERISTIC_UUID),
            commandLoadwheelPitch = gatt!!.getService(SENSOR_SERVICE_UUID).getCharacteristic(COMMAND_LOADWHEEL_PITCH_CHARACTERISTIC_UUID)
        )

        Log.w("Ballzooka", "$characteristics")

        scope.launch {
            gatt?.let { gatt ->
                for (characteristic in characteristics!!.values) {
                    gatt.setCharacteristicNotification(characteristic, true)
                    val descriptor = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
                    )
                    try {
                        var status = gatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                        while (status != BluetoothGatt.GATT_SUCCESS) {
                            delay(100)
                            status = gatt.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.wtf("Ballzooka", "Error enabling notifications for ${characteristic.uuid}", e)
                    }
                    Log.i("Ballzooka", "${characteristic.uuid} enabled")
                }
                _connectionState.value = ConnectionStatus.CONNECTED
            } ?: run {
                Log.w("Ballzooka", "BluetoothGatt not initialized")
            }
        }

    }

    fun connect(device: BluetoothDevice) {
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun checkBLEStatus(): Boolean {
        return if (gatt != null) {
            val state = bluetoothManager.getConnectionState(gatt!!.device, BluetoothProfile.GATT)
            Log.w("Ballzooka", "checkBLEStatus: $state)")
            state == BluetoothProfile.STATE_CONNECTED
        } else {
            false
        }
    }

    fun disconnect() {
        _connectionState.value = ConnectionStatus.DISCONNECTED
        gatt?.disconnect()
    }

    fun send(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        // TODO: add timeout
        var status = gatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        while (status != BluetoothGatt.GATT_SUCCESS) {
            Thread.sleep(1000)
            status = gatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }
}