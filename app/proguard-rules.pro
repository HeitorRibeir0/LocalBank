# ─── LocalBank ProGuard Rules ───

# Manter modelos de dados (Room precisa reflection; Firestore usa Map manual)
-keep class com.localbank.finance.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# SQLCipher (versão nova)
-keep class net.zetetic.database.sqlcipher.** { *; }

# Credential Manager carrega o provedor do Play Services por reflexão
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

# Preservar nomes para stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile