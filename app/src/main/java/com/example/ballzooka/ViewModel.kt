package com.example.ballzooka

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.UUID

val SENSOR_SERVICE_UUID: UUID = UUID.fromString("ba10f731-f94d-45f8-8ccd-89e393b418f4")
//val SENSOR_SERVICE_UUID: UUID = UUID.fromString("ba110000-f94d-45f8-8ccd-89e393b418f4")

val HEADING_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f732-f94d-45f8-8ccd-89e393b418f4")
val PITCH_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f73b-f94d-45f8-8ccd-89e393b418f4")
val LATITUDE_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f733-f94d-45f8-8ccd-89e393b418f4")
val LONGITUDE_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f736-f94d-45f8-8ccd-89e393b418f4")
val BATTERY_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f734-f94d-45f8-8ccd-89e393b418f4")
val RPM_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f735-f94d-45f8-8ccd-89e393b418f4")
val SAFETY_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f737-f94d-45f8-8ccd-89e393b418f4")

// Command Characteristic UUIDs
val COMMAND_FLYWHEEL_RPM_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f738-f94d-45f8-8ccd-89e393b418f4")
val COMMAND_LOADWHEEL_YAW_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f739-f94d-45f8-8ccd-89e393b418f4")


data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val currentState: AppState = AppState.IDLE
)

data class Telemetry(
    val heading: Double = 0.00,
    val pitch: Double = 0.00,
    val latitude: Double = 0.00,
    val longitude: Double = 0.00,
    val selection: LatLng = LatLng(0.0, 0.0),
    val desiredPitch: Double = 45.00,
    val rpm: Int = 0,
    val safety: Boolean = false
)

enum class ConnectionStatus { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR }
enum class AppState { START, IDLE, AIMING, ARMED, SAFETY }

class BallzookaViewModel(
    application: Application
) : AndroidViewModel(application) {
    // Expose state as StateFlow for Compose
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val scanner = BluetoothScanner(application)

    private val stateMachine = StateMachine()
    val messenger: BluetoothMessenger = BluetoothMessenger(application, viewModelScope)

    private var heartbeatJob: Job? = null

    init {
        // Observe Bluetooth connection
        viewModelScope.launch {
            messenger.connectionState.collect { status ->
                Log.i("Ballzooka", "Connection state: $status")
                _uiState.update { it.copy(connectionStatus = status) }
                if (status == ConnectionStatus.DISCONNECTED) {
                    stateMachine.changeState(AppState.START)
                } else if (status == ConnectionStatus.CONNECTED) {
                    stateMachine.changeState(AppState.IDLE)
                }
            }
        }

        viewModelScope.launch {
            messenger.telemetry.collect { telemetry ->
                if (telemetry.safety && uiState.value.currentState != AppState.SAFETY) {
                    disarm()
                    stateMachine.changeState(AppState.SAFETY)
                } else if (!telemetry.safety && uiState.value.currentState == AppState.SAFETY) {
                    stateMachine.changeState(AppState.IDLE)
                }
                _telemetry.update { current -> telemetry.copy(selection = current.selection, desiredPitch = current.desiredPitch) }
            }
        }

        // Observe state machine
        viewModelScope.launch {
            stateMachine.currentState.collect { state ->
                _uiState.update { it.copy(currentState = state) }
            }
        }

        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(3_000)
                if (uiState.value.connectionStatus == ConnectionStatus.CONNECTED && !messenger.checkBLEStatus()) {
                    messenger.disconnect()
                } else if (uiState.value.connectionStatus == ConnectionStatus.DISCONNECTED && !messenger.checkBLEStatus()) {
                    findAndConnect()
                }
            }
        }
    }

    fun findAndConnect() {
        viewModelScope.launch {
            while (!scanner.bluetoothAdapter.isEnabled) {
                delay(1_000)
            }

            _uiState.update { it.copy(connectionStatus = ConnectionStatus.SCANNING) }

            val device = scanner.findDevice(SENSOR_SERVICE_UUID, 600_000)

            if (device != null) {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTING) }
                messenger.connect(device)
            } else {
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.ERROR) }
            }
        }
    }

    fun updateSelectionLocation(latLng: LatLng) {
        _telemetry.update { it.copy(selection = latLng) }
    }

    fun updateDesiredPitch(pitch: Double) {
        _telemetry.update { it.copy(desiredPitch = pitch) }
    }

    fun arm(params: ArmParams) {
//        val yawThreshold = 0.5
        val yawThreshold = 300
        val rpmThreshold = 100
        Log.d("Ballzooka", "Arming. RPM: ${params.speed}, Yaw: ${params.yaw}, Pitch: ${params.pitch}")
        viewModelScope.launch {
            val rpmBytes = ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(params.speed).array()
            Log.d("Ballzooka", rpmBytes.toHexString())
            rpmBytes.reverse()
            Log.d("Ballzooka", rpmBytes.toHexString())
            val yawBytes = ByteBuffer.allocate(Double.SIZE_BYTES).putDouble(params.yaw).array()
            Log.d("Ballzooka", yawBytes.toHexString())
            yawBytes.reverse()
            Log.d("Ballzooka", yawBytes.toHexString())

            messenger.send(
                messenger.characteristics!!.commandFlywheelRPM,
                rpmBytes
            )
            messenger.send(
                messenger.characteristics!!.commandLoadwheelYaw,
                yawBytes
            )

            // add pitch when we have a sensor for it

            // wait for 10s for it to arm
            val reached = withTimeoutOrNull(10_000L) {
                telemetry.first { (it.rpm >= params.speed - rpmThreshold) && (it.rpm <= params.speed + rpmThreshold) && (it.heading >= params.yaw - yawThreshold) && (it.heading <= params.yaw + yawThreshold) }
            }

            if (reached != null) {
                stateMachine.changeState(AppState.ARMED)
            } else {
                Log.w("Ballzooka", "arm() timed out waiting for RPM target")
                _events.send("Timed out waiting for RPM target")

                // kill the motors (do this properly later)
                messenger.send(
                    messenger.characteristics!!.commandFlywheelRPM,
                    ByteBuffer.allocate(java.lang.Long.BYTES).putLong(java.lang.Double.doubleToLongBits(0.0)).array()
                )
                stateMachine.changeState(AppState.IDLE)
            }
        }
    }

    fun disarm() {
        Log.d("Ballzooka", "Disarming")
        viewModelScope.launch {
            // kill the motors (do this properly later)
            messenger.send(
                messenger.characteristics!!.commandFlywheelRPM,
                ByteBuffer.allocate(java.lang.Long.BYTES).putLong(java.lang.Double.doubleToLongBits(0.0)).array()
            )
            stateMachine.changeState(AppState.IDLE)
        }
    }

    fun send(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        messenger.send(characteristic, data)
    }

    fun changeState(state: AppState) {
        viewModelScope.launch {
            stateMachine.changeState(state)
        }
    }

    fun addEvent(message: String) {
        viewModelScope.launch {
            _events.send(message)
        }
    }
}

class StateMachine {
    private val _currentState = MutableStateFlow(AppState.IDLE)
    val currentState: StateFlow<AppState> = _currentState.asStateFlow()

    fun changeState(state: AppState) {
        _currentState.value = state
    }
}
