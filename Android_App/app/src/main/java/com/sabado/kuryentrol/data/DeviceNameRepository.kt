package com.sabado.kuryentrol.data

import com.sabado.kuryentrol.data.local.DeviceNameDao
import com.sabado.kuryentrol.data.model.DeviceName
import kotlinx.coroutines.flow.Flow

class DeviceNameRepository(private val deviceNameDao: DeviceNameDao) {

    suspend fun saveDeviceName(clientId: String, customName: String) {
        deviceNameDao.insertDeviceName(DeviceName(clientId, customName))
    }

    fun getDeviceName(clientId: String): Flow<DeviceName?> {
        return deviceNameDao.getDeviceName(clientId)
    }

    fun getAllDeviceNames(): Flow<List<DeviceName>> {
        return deviceNameDao.getAllDeviceNames()
    }

    suspend fun deleteDeviceName(clientId: String) {
        deviceNameDao.deleteDeviceName(clientId)
    }
}
