package com.unpluck.app.defs

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.util.UUID

@Entity(tableName = "spaces")
@TypeConverters(AppIdConverter::class)
data class Space(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val appIds: List<String> = emptyList(),
    val isDndEnabled: Boolean = false,
    val isCallBlockingEnabled: Boolean = false,
    val allowedContactIds: List<String> = emptyList()
)

// This helper class tells Room how to save a List<String>
class AppIdConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
}

class ContactIdConverter {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
}