package com.example.ballzooka

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun BluetoothScreen(
    onBluetoothReady: (BluetoothAdapter) -> Unit
) {
    val context = LocalContext.current
    val bluetoothManager = remember {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    val bluetoothAdapter = remember { bluetoothManager.adapter }

//    val enableBluetoothLauncher = rememberLauncherForActivityResult(
//        ActivityResultContracts.StartActivityForResult()
//    ) { result ->
//        if (result.resultCode == Activity.RESULT_OK) {
//            onBluetoothReady(bluetoothAdapter)
//        } else {
//            // User declined to enable Bluetooth
//        }
//    }

//    LaunchedEffect(bluetoothAdapter) {
//        if (bluetoothAdapter == null) {
//            // Device doesn't support Bluetooth
//            return@LaunchedEffect
//        }
//
//        if (!bluetoothAdapter.isEnabled) {
//            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//            enableBluetoothLauncher.launch(enableBtIntent)
//        } else {
//            onBluetoothReady(bluetoothAdapter)
//        }
//    }
}