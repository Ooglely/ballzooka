# Ballzooka

This is the Android app side of the Ballzooka ECE Senior Design project. To load the app onto new devices, please refer to the *"Loading the Ballzooka App onto New Devices"* section in the manual.

## File Breakdown

All of the files with code are in the `app/src/main/java/com/example/ballzooka` directory. When the project is opened up in Android Studio, the Android view should enabled, which should automatically open up this folder in the Project view.

### MainActivity.kt

This is the main activity of the app, which is responsible for the app's main UI and behavior. This imports the ViewModel (from `ViewModel.kt`) and uses it to update the UI.

### ViewModel.kt

This is the ViewModel for the app, which is responsible for the app's data and business logic. The ViewModel stores instances of different data classes in order to keep track of the state through the rest of the app:
- `uiState`: Contains the current connection state and general app state. Is used to both pass updates from the state machine to the UI, and is used to change the connection status whenever the Bluetooth connection state changes.
- `telemetry`: Contains all telemetry data received from either the Arduino and its sensors or from user interaction. Stores `heading`, `pitch`, cannon position (`latitude`, `longitude`), selected position (`selection`), `desiredPitch`, motor RPM (`leftrpm`, `rightrpm`), and the safety check (`safety`). These are all visible to both the UI and other classes in the app.
- `events`: A channel that is used to pass events (basically a visible notification) to the UI.
- `stateMachine`: The state machine.

### Bluetooth.kt

Holds the BluetoothMessenger class, which controls all communication over Bluetooth LE on the backend.

### Calculate.kt

Has all the functions for calculating the yaw, pitch, and RPM needed to fire at the user's selected position based on where the cannon is.

### Map.kt

Has a Composable for displaying the map (using Google's map API) and control over the map (selecting the location, showing the cannon/user/selection location, etc.). Also has a class to get the user/device location.

### Toolbar.kt

Has a Composable for the app's toolbar, which displays available telemetry and controls based on the AppState in the StateMachine.
