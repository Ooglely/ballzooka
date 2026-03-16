package com.example.ballzooka

import android.app.Application
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

val SENSOR_SERVICE_UUID: UUID = UUID.fromString("ba10f731-f94d-45f8-8ccd-89e393b418f4")
//val SENSOR_SERVICE_UUID: UUID = UUID.fromString("ba110000-f94d-45f8-8ccd-89e393b418f4")

val HEADING_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f732-f94d-45f8-8ccd-89e393b418f4")
val LATITUDE_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f733-f94d-45f8-8ccd-89e393b418f4")
val LONGITUDE_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f736-f94d-45f8-8ccd-89e393b418f4")
val BATTERY_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f734-f94d-45f8-8ccd-89e393b418f4")
val RPM_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f735-f94d-45f8-8ccd-89e393b418f4")
val SAFETY_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f737-f94d-45f8-8ccd-89e393b418f4")

// Command Characteristic UUIDs
val COMMAND_FLYWHEEL_RPM_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f738-f94d-45f8-8ccd-89e393b418f4")
val COMMAND_LOADWHEEL_ANGLE_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f739-f94d-45f8-8ccd-89e393b418f4")


data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val currentState: AppState = AppState.IDLE,
    val receivedData: String = "",
    val isLoading: Boolean = false
)

data class Telemetry(
    val heading: Double = 0.00,
    val latitude: Double = 0.00,
    val longitude: Double = 0.00,
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
                    stateMachine.changeState(AppState.SAFETY)
                } else if (!telemetry.safety && uiState.value.currentState == AppState.SAFETY) {
                    stateMachine.changeState(AppState.IDLE)
                }
                _telemetry.value = telemetry
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

    fun send(characteristic: BluetoothGattCharacteristic, data: ByteArray) {
        messenger.send(characteristic, data)
    }

    fun changeState(state: AppState) {
        viewModelScope.launch {
            stateMachine.changeState(state)
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
