# ─── LocalBank ProGuard Rules ───

# Manter modelos de dados (Room + Firestore precisam reflection)
-keep class com.localbank.finance.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# SQLCipher
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# Google Sign-In
-keep class com.google.android.gms.auth.** { *; }

# Compose — não ofuscar previews e componentes
-keep class androidx.compose.** { *; }

# Preservar nomes para stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile