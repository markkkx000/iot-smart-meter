# Android App Implementation Plan: IoT Smart Meter

This document outlines a phased approach to developing the Android application for the IoT Smart Meter project using Kotlin and Jetpack Compose.

---

### **Phase 1: Project Setup & Foundational Components**

This phase focuses on creating a solid architectural base for the application.

1.  **Project Setup**:
    *   Create a new Android Studio project with the "Empty Activity" template, ensuring Kotlin and Jetpack Compose are selected.
    *   Set up version control (Git).

2.  **Dependency Management**:
    *   Add essential libraries to `build.gradle.kts`:
        *   **UI**: Jetpack Compose (UI, Material3, Navigation, ViewModel).
        *   **Networking**: Retrofit for REST API, OkHttp for interceptors.
        *   **MQTT**: A robust MQTT client library like `eclipse/paho.mqtt.android`.
        *   **Persistence**: Jetpack DataStore for simple key-value storage (settings).
        *   **Asynchronous Ops**: Kotlin Coroutines.

3.  **App Architecture (MVVM)**:
    *   Create the following package structure:
        *   `data`: For models, repositories, and data sources (remote/local).
        *   `di`: For dependency injection setup (e.g., Hilt).
        *   `ui`: For composable screens, viewmodels, and navigation.
        *   `service`: For the MQTT connection manager.

4.  **Data Models**:
    *   Create Kotlin `data class` files based on the API and MQTT payloads from `README.md`. Examples: `DeviceStatus`, `PzemMetrics`, `Schedule`, `Threshold`.

5.  **Settings Screen & Persistence**:
    *   Build a simple settings screen where the user can input the REST API base URL and MQTT broker address (IP or mDNS).
    *   Use Jetpack DataStore to save and retrieve these settings, making them accessible throughout the app.

---

### **Phase 2: MQTT Integration & Real-time Dashboard**

This phase brings the app to life with real-time data.

1.  **MQTT Connection Service**:
    *   Create a manager class (`MqttManager`) to handle all MQTT logic: connect, disconnect, subscribe, publish, and automatic reconnection. This should run in a background service or be managed as a singleton.

2.  **Dashboard ViewModel & UI**:
    *   Create a `DashboardViewModel` that interacts with the `MqttManager`.
    *   Design a dashboard screen that displays a list of devices.
    *   The ViewModel will hold a list of `Device` objects in a `StateFlow`, which the UI will observe.

3.  **Device Discovery & Status**:
    *   In `MqttManager`, subscribe to the wildcard topic `dev/+/status`.
    *   When a message arrives, parse the `client_id` from the topic and update the corresponding device's online/offline status in the ViewModel.

4.  **Real-time Data Display**:
    *   Subscribe to `dev/+/pzem/metrics` and `dev/+/pzem/energy`.
    *   Update the UI in real-time with the latest voltage, current, power, and energy readings for each device.

5.  **Relay Control**:
    *   Add a `Switch` composable for each device on the dashboard.
    *   When toggled, publish `RELAY_ON` or `RELAY_OFF` to the `dev/<CLIENT_ID>/relay/commands` topic.
    *   Subscribe to `dev/+/relay/state` to ensure the switch accurately reflects the device's actual state, especially on initial connection.

---

### **Phase 3: REST API Integration & Device Details**

This phase focuses on historical data and device-specific configuration.

1.  **Retrofit Setup**:
    *   Define a Retrofit interface with functions corresponding to the REST API endpoints in the `README.md` (`/api/schedules`, `/api/thresholds`, etc.).

2.  **Repository Implementation**:
    *   Create a `DeviceRepository` that fetches data from the Retrofit service. This will abstract the data-fetching logic from the ViewModels.

3.  **Navigation**:
    *   Implement navigation from the dashboard screen to a new `DeviceDetailsScreen`, passing the `client_id` as an argument.

4.  **Historical Data Graphs**:
    *   On the `DeviceDetailsScreen`, create a `DeviceDetailsViewModel` that calls the repository to fetch energy data (`/api/energy/<client_id>`).
    *   Add tabs or buttons for "Daily," "Weekly," and "Monthly" views.
    *   Integrate a Compose-compatible charting library to visualize the consumption data.

5.  **Energy Bill Computation**:
    *   Add an input field on the details screen for the user to enter their price per kWh (this can be saved in DataStore).
    *   Calculate and display the estimated cost based on the fetched historical data.

---

### **Phase 4: Schedule & Threshold Management**

This phase implements the core automation features.

1.  **Schedule Management UI**:
    *   On the `DeviceDetailsScreen`, add a section to display existing schedules fetched via `GET /api/schedules/<client_id>`.
    *   Create a "New Schedule" button that opens a dialog or a new screen.
    *   Implement a **wheel number picker** composable for selecting `start_time` and `end_time`.
    *   Use `POST` and `PUT` requests to create and update schedules. Handle both "daily" and "timer" types.

2.  **Threshold Management UI**:
    *   Add a section to display and configure the energy threshold.
    *   Fetch the current setting with `GET /api/thresholds/<client_id>`.
    *   Provide UI elements to set the `limit_kwh` and `reset_period`.
    *   Use the `PUT /api/thresholds/<client_id>` endpoint to save the configuration.

---

### **Phase 5: Nice-to-Have Features & Polish**

This final phase adds user-friendly features and refines the app.

1.  **Device Renaming (Local Storage)**:
    *   Set up a simple Room database with a table mapping `client_id` (String) to `custom_name` (String).
    *   Allow users to long-press or tap an edit icon on a device to give it a custom name.
    *   Update the UI to show the custom name if one exists, otherwise show the `client_id`.

2.  **Network Latency Indicator**:
    *   Create a mechanism to measure latency. A simple approach is to measure the round-trip time of a lightweight API call like `GET /api/health`.
    *   Display the result (e.g., "Ping: 52ms") in the settings screen or a corner of the dashboard.

3.  **UI/UX Refinement**:
    *   Add clear loading indicators when data is being fetched.
    *   Implement proper error handling (e.g., snackbars for "Failed to connect" or "Invalid IP address").
    *   Design intuitive empty states for when no devices are found or no schedules are set.
    *   Ensure the app has a professional look and feel with consistent theming and animations.

4.  **Testing**:
    *   Write unit tests for your ViewModels and Repositories to ensure business logic is correct.
    *   Write instrumentation tests for critical user flows like connecting to the broker and creating a schedule.
