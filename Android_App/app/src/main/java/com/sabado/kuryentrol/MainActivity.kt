package com.sabado.kuryentrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.sabado.kuryentrol.ui.settings.SettingsScreen
import com.sabado.kuryentrol.ui.theme.KuryentrolTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main Activity annotated with @AndroidEntryPoint for Hilt dependency injection
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuryentrolTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Phase 1: Display Settings Screen
                    SettingsScreen()
                }
            }
        }
    }
}
