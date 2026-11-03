# Media3, Room, Coil, OkHttp and kotlinx.serialization ship their own consumer rules.

# Keep @Serializable models' generated serializers (JSON import/export of station lists).
-keepclassmembers @kotlinx.serialization.Serializable class com.radiocompanion.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.radiocompanion.**$$serializer { *; }

# Readable stack traces from the car (map with mapping.txt).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
