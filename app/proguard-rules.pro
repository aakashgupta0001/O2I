# Add project specific ProGuard rules here.
# Preserve line numbers for production crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep Room Database Entities, DAOs, and Database implementation
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.example.data.local.** { *; }
-keep class com.example.domain.** { *; }
-keep class com.example.excel.** { *; }
