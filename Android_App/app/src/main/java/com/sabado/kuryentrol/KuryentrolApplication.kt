package com.sabado.kuryentrol

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Kuryentrol
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection
 */
@HiltAndroidApp
class KuryentrolApplication : Application()
