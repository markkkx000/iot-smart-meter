package com.sabado.kuryentrol.ui.devicedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sabado.kuryentrol.data.DeviceRepository

class DeviceDetailsViewModelFactory(
    private val clientId: String,
    private val deviceRepository: DeviceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceDetailsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceDetailsViewModel(clientId, deviceRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
