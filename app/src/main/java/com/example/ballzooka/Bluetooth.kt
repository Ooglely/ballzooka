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
    // Data class for holding all of the Bluetooth LE characteristics,
    // makes it easier to cycle through all of them for initial connection
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
    // Class for initally scanning for and connecting to the Ballzooka Arduino
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
                // Since we filter for the serviceUUID, if we get a result we know its the Ballzooka
                // so we return and stop scanning
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    super.onScanResult(callbackType, result)
                    Log.w("Ballzooka", "onScanResult: $result")
                    scanner.stopScan(this)
                    continuation.resume(result.device)
                }

                // Return null if scan failed so that we show a connection state of ERROR
                override fun onScanFailed(errorCode: Int) {
                    super.onScanFailed(errorCode)
                    Log.w("Ballzooka", "onScanFailed: $errorCode")
                    continuation.resume(null)
                }
            }

            // setServiceUuid is used to filter for the Ballzooka service UUID
            val filter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(serviceUuid))
                .build()

            // Scan for Bluetooth LE
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
    // Class to control receiving and sending data to the cannon

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private var gatt: BluetoothGatt? = null
    var characteristics: BluetoothSensorCharacteristics? = null

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        // Whenever we notice the connection state changes we need to change the connectionState so it's reflected across the app.
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.i("Ballzooka", "onConnectionStateChange: $newState");
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionStatus.CONNECTED
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i("Ballzooka", "Handling disconnection")
                    characteristics = null
                    disconnect()
                }
            }
        }

        // Discovering a service means its the sensor service.
        // Once we find it, we can initialize all of the telemetry
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

        // This will get called every time a notification is sent to the app
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.w("BallzookaBT", "onCharacteristicChanged: ${value.toHexString(HexFormat.Default)}, ${characteristic.uuid}")
            value.reverse()
            when (characteristic) {
                // This is basically a switch state to update the correct characteristic in the telemetry
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

        // Just to add some logging on write
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
        // Go through each characteristic and set the characteristicNotification
        // This makes it so the app is notified every time the characteristic changes
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
                    // This is the Client Characteristic Configuration descriptor.
                    val descriptor = characteristic.getDescriptor(
                        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
                    )
                    try {
                        // Write the enable notification value to the Client Characteristic Configuration descriptor
                        var status = gatt.writeDescriptor(
                            descriptor,
                            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        )
                        // Sometimes the "pipe is clogged". Unfortunately the best way to handle this is
                        // to just keep attempting to send until it works.
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
        // Connect to the Ballzooka
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun checkBLEStatus(): Boolean {
        // Check if the device is connect to the Ballzooka
        return if (gatt != null) {
            val state = bluetoothManager.getConnectionState(gatt!!.device, BluetoothProfile.GATT)
            Log.w("Ballzooka", "checkBLEStatus: $state)")
            state == BluetoothProfile.STATE_CONNECTED
        } else {
            false
        }
    }

    fun disconnect() {
        // Disconnect from the Ballzooka.
        _connectionState.value = ConnectionStatus.DISCONNECTED
        gatt?.disconnect()
    }

    fun send(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        // Send a piece of data in a ByteArray to a given characteristic.
        // TODO: add timeout, and make this async
        var status = gatt?.writeCharacteristic(characteristic, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        while (status != BluetoothGatt.GATT_SUCCESS) {
            Thread.sleep(100)
            status = gatt?.writeCharacteristic(
                characteristic,
                data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        }
    }
}