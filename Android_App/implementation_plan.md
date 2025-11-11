# Phase 1: Project Setup & Foundational Components 🚀

This phase focuses on setting up your empty project with all the necessary dependencies and architecture before writing UI code.

## 1.1. Dependency Setup (The Most Critical Step)

First, let's add all the libraries you'll need.

### (A) In gradle/libs.versions.toml:

Add these entries to your `[versions]` block:

```toml
[versions]
# ... (keep existing agp, kotlin, etc.)
activityCompose = "1.11.0"
composeBom = "2024.09.00"
coreKtx = "1.17.0"
espressoCore = "3.7.0"
junit = "4.13.2"
junitVersion = "1.3.0"
lifecycleRuntimeKtx = "2.9.4"

# --- ADD THESE ---
datastore = "1.1.1"
hilt = "2.51.1"
hiltNavigationCompose = "1.2.0"
infoMqtt = "3.7.0"
ksp = "2.0.21-1.0.20"  # CRITICAL: Must match your kotlin = "2.0.21"
lifecycle = "2.9.4"
navigationCompose = "2.7.7"
okhttp = "4.12.0"
retrofit = "2.9.0"
room = "2.6.1"
```

Add these entries to your `[libraries]` block:

```toml
[libraries]
# ... (keep existing androidx-core-ktx, androidx-activity-compose, etc.)

# --- ADD THESE ---

# Hilt (Dependency Injection)
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }

# Navigation
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

# ViewModel
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Networking (Retrofit, OkHttp, Gson)
retrofit = { group = "com.squareup.retrofit2", name = "retrofit", version.ref = "retrofit" }
converter-gson = { group = "com.squareup.retrofit2", name = "converter-gson", version.ref = "retrofit" }
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
okhttp-logging-interceptor = { group = "com.squareup.okhttp3", name = "logging-interceptor", version.ref = "okhttp" }

# MQTT (The modern, recommended library)
info-mqtt-android-service = { group = "info.mqtt.android", name = "service", version.ref = "infoMqtt" }

# Persistence (DataStore for settings)
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# Persistence (Room for device names)
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

### (B) In your project-level build.gradle.kts (in the Android_App root):

Add the Hilt and KSP plugins.

```kotlin
plugins {
    # ... (keep existing plugins)
    
    # --- ADD THESE ---
    alias(libs.plugins.hilt) apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.20" apply false // Use the KSP version from your toml
}
```

### (C) In your app-level build.gradle.kts (in the Android_App/app folder):

Apply the plugins and add the implementation lines.

```kotlin
plugins {
    # ... (keep existing plugins)
    
    # --- ADD THESE ---
    alias(libs.plugins.hilt)
    id("com.google.devtools.ksp")
}

// ... inside android { ... }

dependencies {
    # ... (keep existing compose, core-ktx, etc.)

    # --- ADD THESE ---

    # Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    # Navigation
    implementation(libs.androidx.navigation.compose)

    # ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    
    # Networking
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    # MQTT
    implementation(libs.info.mqtt.android.service)

    # DataStore
    implementation(libs.androidx.datastore.preferences)

    # Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}
```

### (D) Sync Gradle
Click the "Sync Project with Gradle Files" button. This will download and set up all your dependencies.

## 1.2. App Architecture (MVVM & Hilt)

Create your package structure:
- `com.sabado.kuryentrol.data`
- `com.sabado.kuryentrol.data.local` (for Room)
- `com.sabado.kuryentrol.data.model`
- `com.sabado.kuryentrol.data.remote` (for Retrofit/Api)
- `com.sabado.kuryentrol.di` (for Hilt)
- `com.sabado.kuryentrol.service` (for MqttManager)
- `com.sabado.kuryentrol.ui`

Create a `KuryentrolApplication.kt` file, annotate it with `@HiltAndroidApp`, and update your `AndroidManifest.xml` to use it.

## 1.3. Data Models

Based on your README.md and curl output, create these models in the `data/model` package.

```kotlin
// For MQTT (dev/+/pzem/metrics)
data class PzemMetrics(
    val voltage: Float,
    val current: Float,
    val power: Float
)

// For API (GET /api/energy)
data class EnergyReading(
    val energy_kwh: Float,
    val timestamp: String // "2025-11-11 19:22:28"
)

// For API (GET /api/devices)
data class Device(
    val client_id: String,
    val last_seen: String,
    val current_energy_kwh: Float
)

// For API (GET /api/schedules)
data class Schedule(
    val id: Int,
    val client_id: String,
    val schedule_type: String, // "daily" or "timer"
    val start_time: String?, // "08:00"
    val end_time: String?,   // "20:00"
    val days_of_week: String?, // "0,1,2,3,4"
    val duration_seconds: Int?, // 3600
    val enabled: Int,
    val created_at: String
)

// For API (GET /api/thresholds)
data class Threshold(
    val id: Int,
    val client_id: String,
    val limit_kwh: Float,
    val reset_period: String, // "daily", "weekly", or "monthly"
    val enabled: Int,
    val last_reset: String,
    val created_at: String
)
```

## 1.4. Settings Screen & Persistence

- Create a `SettingsRepository` that uses Jetpack DataStore to save/retrieve the broker URL and API URL.
- Create a `SettingsScreen` and `SettingsViewModel` to allow the user to input these values.

# Phase 2: MQTT Integration & Real-time Dashboard 📡

## MQTT Connection Service

- Create an `MqttManager` class (as a Hilt-provided Singleton).
- Use the `info.mqtt.android.service.MqttAndroidClient`.
- Remember to use `message.getPayload()` and `mqttMessage.setPayload()`.

## Dashboard ViewModel & UI

- Create a `DashboardViewModel` that is injected with `MqttManager` and `SettingsRepository`.
- On init, launch a coroutine to get the broker URL from settings, then call `mqttManager.connect()`.

## Device Discovery & Status

- Subscribe to `dev/+/status` (for online/offline).
- Subscribe to `dev/+/relay/state` (for ON/OFF state).
- Subscribe to `dev/+/pzem/energy` (for cumulative kWh).

## Real-time Data Display

- Subscribe to `dev/+/pzem/metrics`.
- In your ViewModel, use `Gson().fromJson(payloadString, PzemMetrics::class.java)` to parse the incoming JSON into your data class.

## Relay Control

- Add a `Switch` composable.
- On toggle, call `mqttManager.publish("dev/<CLIENT_ID>/relay/commands", "RELAY_ON")` (or `RELAY_OFF`).

# Phase 3: REST API Integration & Device Details 📈

## Retrofit Setup

Create your API wrapper data classes. Based on your curl output:

```kotlin
data class EnergyResponse(
    val client_id: String,
    val readings: List<EnergyReading>,
    val success: Boolean
)
data class SchedulesResponse(
    val client_id: String,
    val schedules: List<Schedule>,
    val success: Boolean
)
// ...and so on for other endpoints.
```

Create your `ApiService` interface:

```kotlin
interface ApiService {
    @GET("api/energy/{clientId}")
    suspend fun getEnergyReadings(
        @Path("clientId") clientId: String
    ): Response<EnergyResponse> // Use the wrapper

    // ... other endpoints
}
```

- Create a Hilt module in the `di` package to provide your Retrofit and `ApiService` instances.

## Repository Implementation

- Create a `DeviceRepository` injected with your `ApiService`.
- The functions will call the API and return the inner data, e.g., `apiService.getEnergyReadings(id).body()?.readings`.

## Navigation

- Implement `androidx.navigation.compose`.
- Make the dashboard list items clickable, navigating to a `DeviceDetailsScreen` and passing the `client_id` as an argument.

# Phase 4: Historical Data Graphs 📊

This is the most complex part of the UI.

## In DeviceDetailsViewModel

- Create a private StateFlow to hold the full, raw list: `_allReadings = MutableStateFlow<List<EnergyReading>>(emptyList())`.
- Create a StateFlow for the user's tab selection: `_selectedPeriod = MutableStateFlow("Daily")`.
- When the screen loads, call `deviceRepository.getEnergyReadings(clientId)`. On success, update `_allReadings.value` with the reversed list (so it's in chronological, oldest-first order).

## Data Processing

- The `energy_kwh` value is cumulative. To get daily consumption, you need to calculate the delta (difference) between readings.
- The timestamps are strings. You'll need a parser: `DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")`.

## Filtered Data Logic

Combine `_allReadings` and `_selectedPeriod` to produce a final `graphData` state.

This logic will:
- Parse timestamps into `LocalDateTime` objects.
- Filter the list based on the `_selectedPeriod` (e.g., for "Daily", filter for `timestamp.isAfter(LocalDateTime.now().minusDays(1))`).
- Process the filtered list to calculate the consumption per time block (e.g., per hour for the daily view). This means grouping readings by hour and then subtracting the `energy_kwh` of the first reading of that hour from the last.
- This processed list (e.g., `List<Pair<String, Float>>` representing `["1pm", 0.05], ["2pm", 0.12]`) is what you'll feed to your chart.

## Energy Bill Computation

- Your "Total Consumption" will be the `last()` reading's `energy_kwh` minus the `first()` reading's `energy_kwh` from your filtered list.
- Multiply this total by the user's price-per-kWh.

# Phase 5: Schedule & Threshold Management ⚙️

## Schedule Management UI

- Fetch schedules using `GET /api/schedules/<client_id>`.
- Build a `ScheduleDialog` composable.
- When saving a "timer" schedule, ensure your `Schedule` object includes the `duration_seconds` field.

## Threshold Management UI

- Fetch threshold with `GET /api/thresholds/<client_id>`.
- Build a `ThresholdDialog` to PUT or DELETE the threshold.

# Phase 6: Nice-to-Have Features & Polish ✨

## Device Renaming (Local Storage)

- Create a `DeviceName` entity for Room.
- Create a `DeviceNameDao` and `AppDatabase` class (annotated with `@Database`).
- Provide the `AppDatabase` and Dao using a Hilt module.
- Create a `DeviceNameRepository` injected with the Dao.
- Update the `DashboardViewModel` to be injected with `DeviceNameRepository` and combine the API device list with the local custom names.

## Network Latency Indicator

- Add a `GET /api/health` endpoint to your `ApiService`.
- Create a function in your `DeviceRepository` to call it and measure the round-trip time.

## UI/UX Refinement

- Add loading spinners (`CircularProgressIndicator`) when StateFlows indicate data is loading.
- Show error messages (`Snackbar`) when API or MQTT connections fail.
- Design "Empty" states (e.g., "No devices found. Make sure your Pi is online.").