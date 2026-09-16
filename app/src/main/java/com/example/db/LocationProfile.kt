package com.example.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Room Entity representing a saved preset location (Venue Directory) with tiers, addresses, and GPS coordinates.
 */
@Entity(tableName = "location_profiles")
data class LocationProfile(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "name")
    val name: String, // e.g., "Office", "Client HQ", "Home Studio", "Favorite Cafe"
    @ColumnInfo(name = "address")
    val address: String? = null,
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,
    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,
    @ColumnInfo(name = "tier")
    val tier: String = "Primary", // Primary, Secondary, Remote, Custom, Discovered
    @ColumnInfo(name = "notes")
    val notes: String? = null,
    @ColumnInfo(name = "visit_count")
    val visitCount: Int = 0
)
