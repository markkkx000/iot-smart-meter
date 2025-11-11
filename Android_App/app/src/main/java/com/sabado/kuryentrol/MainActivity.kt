package com.sabado.kuryentrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sabado.kuryentrol.data.ApiService
import com.sabado.kuryentrol.data.DeviceNameRepository
import com.sabado.kuryentrol.data.DeviceRepository
import com.sabado.kuryentrol.data.SettingsRepository
import com.sabado.kuryentrol.data.local.AppDatabase
import com.sabado.kuryentrol.service.MqttManager
import com.sabado.kuryentrol.ui.dashboard.DashboardScreen
import com.sabado.kuryentrol.ui.dashboard.DashboardViewModel
import com.sabado.kuryentrol.ui.devicedetails.DeviceDetailsScreen
import com.sabado.kuryentrol.ui.devicedetails.DeviceDetailsViewModel
import com.sabado.kuryentrol.ui.devicedetails.DeviceDetailsViewModelFactory
import com.sabado.kuryentrol.ui.settings.SettingsScreen
import com.sabado.kuryentrol.ui.settings.SettingsViewModel
import com.sabado.kuryentrol.ui.theme.KuryentrolTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// No longer need ViewModelFactory classes as Hilt will provide ViewModels
// import com.sabado.kuryentrol.ui.dashboard.DashboardViewModelFactory
// import com.sabado.kuryentrol.ui.settings.SettingsViewModelFactory

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KuryentrolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    KuryentrolApp()
                }
            }
        }
    }
}

@Composable
fun KuryentrolApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect error messages from the AppEventBus and show them in a Snackbar
    LaunchedEffect(snackbarHostState) {
        AppEventBus.errorMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Temporary ViewModel and Repository instantiation without full DI for now
    // These will be removed once Hilt modules are set up and dependencies are injected
    val settingsRepository = remember { SettingsRepository(context) }
    val mqttManager = remember { MqttManager(context) }

    // Room Database and Repository
    val database = remember { AppDatabase.getDatabase(context) }
    val deviceNameRepository = remember { DeviceNameRepository(database.deviceNameDao()) }

    // Initialize Retrofit and ApiService based on settings
    val restApiUrl = runBlocking { settingsRepository.restApiUrl.firstOrNull() } ?: "http://mqttpi.local:5001/api/"

    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
    val httpClient = OkHttpClient.Builder().addInterceptor(logging).build()

    val retrofit = remember(restApiUrl) {
        Retrofit.Builder()
            .baseUrl(restApiUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(httpClient)
            .build()
    }
    val apiService = remember { retrofit.create(ApiService::class.java) }
    val deviceRepository = remember { DeviceRepository(apiService) }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("dashboard") {
                // Use hiltViewModel() for Hilt-injected ViewModels
                val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(mqttManager, settingsRepository, deviceNameRepository, deviceRepository))
                DashboardScreen(dashboardViewModel, navController)
            }
            composable("settings") {
                // Use hiltViewModel() for Hilt-injected ViewModels
                val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsRepository))
                SettingsScreen(settingsViewModel)
            }
            composable("deviceDetails/{clientId}") { backStackEntry ->
                val clientId = backStackEntry.arguments?.getString("clientId")
                if (clientId != null) {
                    val deviceDetailsViewModel: DeviceDetailsViewModel = viewModel(factory = DeviceDetailsViewModelFactory(
                        clientId,
                        deviceRepository
                    )
                    )
                    DeviceDetailsScreen(deviceDetailsViewModel, navController, clientId)
                }
            }
        }
    }
}

// Temporary ViewModelFactory for DashboardViewModel - to be removed with Hilt
class DashboardViewModelFactory(private val mqttManager: MqttManager, private val settingsRepository: SettingsRepository, private val deviceNameRepository: DeviceNameRepository, private val deviceRepository: DeviceRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DashboardViewModel(mqttManager, settingsRepository, deviceNameRepository, deviceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Temporary ViewModelFactory for SettingsViewModel - to be removed with Hilt
class SettingsViewModelFactory(private val settingsRepository: SettingsRepository) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
