package com.radiocompanion.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromList(list: List<String>): String = list.joinToString("\n")

    @TypeConverter
    fun toList(value: String): List<String> = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}
