package com.example.db

import kotlinx.coroutines.flow.Flow

class LocationRepository(private val locationDao: LocationDao) {
    val allLocations: Flow<List<LocationProfile>> = locationDao.getAllLocations()

    fun getLocationsForUser(userId: String): Flow<List<LocationProfile>> = locationDao.getLocationsForUser(userId)

    suspend fun getLocationsForUserSync(userId: String): List<LocationProfile> = locationDao.getLocationsForUserSync(userId)

    suspend fun updateUserIdForLocalRecords(oldUserId: String = "local_user", newUserId: String) {
        locationDao.updateUserIdForLocalRecords(oldUserId, newUserId)
    }

    suspend fun getAllLocationsSync(): List<LocationProfile> = locationDao.getAllLocationsSync()

    suspend fun getByName(name: String): LocationProfile? = locationDao.getLocationByName(name)

    suspend fun insert(location: LocationProfile) {
        locationDao.insertLocation(location)
    }

    suspend fun insertAll(locations: List<LocationProfile>) {
        locationDao.insertLocations(locations)
    }

    suspend fun update(location: LocationProfile) {
        locationDao.updateLocation(location)
    }

    suspend fun delete(location: LocationProfile) {
        locationDao.deleteLocation(location)
    }

    suspend fun deleteById(id: String) {
        locationDao.deleteLocationById(id)
    }

    suspend fun clear() {
        locationDao.clearAll()
    }

    /**
     * Matches an inferred location name against saved presets.
     * If found, increments visit count. If not found, creates a new "Discovered" location profile.
     */
    suspend fun matchOrCreateLocation(name: String): LocationProfile {
        val clean = name.trim()
        if (clean.isBlank()) {
            return LocationProfile(name = "General", tier = "Primary", visitCount = 1)
        }
        val existing = locationDao.getLocationByName(clean)
        if (existing != null) {
            val updated = existing.copy(visitCount = existing.visitCount + 1)
            locationDao.updateLocation(updated)
            return updated
        } else {
            val newLoc = LocationProfile(
                name = clean,
                tier = "Discovered",
                visitCount = 1
            )
            locationDao.insertLocation(newLoc)
            return newLoc
        }
    }
}
