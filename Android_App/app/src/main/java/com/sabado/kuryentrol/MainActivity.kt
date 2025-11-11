package com.sabado.kuryentrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sabado.kuryentrol.ui.AppNavHost
import com.sabado.kuryentrol.ui.theme.KuryentrolTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KuryentrolTheme {
                AppNavHost()
            }
        }
    }
}
