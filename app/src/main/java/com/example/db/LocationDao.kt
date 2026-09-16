package com.example.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {
    @Query("SELECT * FROM location_profiles ORDER BY name ASC")
    fun getAllLocations(): Flow<List<LocationProfile>>

    @Query("SELECT * FROM location_profiles ORDER BY name ASC")
    suspend fun getAllLocationsSync(): List<LocationProfile>

    @Query("SELECT * FROM location_profiles WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getLocationByName(name: String): LocationProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<LocationProfile>)

    @Update
    suspend fun updateLocation(location: LocationProfile)

    @Delete
    suspend fun deleteLocation(location: LocationProfile)

    @Query("DELETE FROM location_profiles WHERE id = :id")
    suspend fun deleteLocationById(id: String)

    @Query("DELETE FROM location_profiles")
    suspend fun clearAll()
}
