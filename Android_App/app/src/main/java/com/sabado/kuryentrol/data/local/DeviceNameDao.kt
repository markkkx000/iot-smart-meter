package com.sabado.kuryentrol.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sabado.kuryentrol.data.model.DeviceName
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceNameDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeviceName(deviceName: DeviceName)

    @Query("SELECT * FROM device_names WHERE clientId = :clientId")
    fun getDeviceName(clientId: String): Flow<DeviceName?>

    @Query("SELECT * FROM device_names")
    fun getAllDeviceNames(): Flow<List<DeviceName>>

    @Query("DELETE FROM device_names WHERE clientId = :clientId")
    suspend fun deleteDeviceName(clientId: String)
}
