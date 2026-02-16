package com.example.ballzooka

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

val SENSOR_SERVICE_UUID: UUID = UUID.fromString("ba10f731-f94d-45f8-8ccd-89e393b418f4")

val HEADING_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f732-f94d-45f8-8ccd-89e393b418f4")
val POSITION_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f733-f94d-45f8-8ccd-89e393b418f4")
val BATTERY_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f734-f94d-45f8-8ccd-89e393b418f4")
val RPM_CHARACTERISTIC_UUID: UUID = UUID.fromString("ba10f735-f94d-45f8-8ccd-89e393b418f4")

data class UiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val currentState: AppState = AppState.START,
    val receivedData: String = "",
    val isLoading: Boolean = false
)

enum class ConnectionStatus { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR }
enum class AppState { START, IDLE, AIMING, ARMED, SAFETY }

class BallzookaViewModel(
    application: Application
): AndroidViewModel(application) {

    // Expose state as StateFlow for Compose
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val scanner = BluetoothScanner(application)
    private val messenger: BluetoothMessenger = BluetoothMessenger(application)

    private val stateMachine = StateMachine()


    init {
        // Observe Bluetooth connection
        viewModelScope.launch {
            messenger.connectionState.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        // Observe state machine
        viewModelScope.launch {
            stateMachine.currentState.collect { state ->
                _uiState.update { it.copy(currentState = state) }
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

    fun changeValue(data: ByteArray) {
        messenger.send(data)
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