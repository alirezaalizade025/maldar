# R8/ProGuard rules for Maldar (Personal Accounting App)
#
# Code shrinking, obfuscation and resource shrinking are enabled for the
# release build. The rules below keep the members that R8 cannot see are used
# because they are referenced reflectively or only from the manifest/generated
# code.

# --- Kotlin ---
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }
-keepclassmembers class kotlin.Metadata { public <methods>; }

# --- Android components declared in the manifest ---
# Activities, Services, Receivers and the Application are instantiated by the
# framework via reflection, so keep them (and their no-arg constructors).
-keep public class com.personalfinance.tracker.MainActivity { *; }
-keep public class com.personalfinance.tracker.PersonalFinanceApp { *; }
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.service.notification.NotificationListenerService

# --- Room ---
# Room generates implementation classes and uses entities/DAOs reflectively.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# --- WorkManager ---
# Workers are instantiated by name via the WorkManager runtime.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Jetpack Compose ---
# Compose already ships consumer rules; these guard reflection-based tooling.
-dontwarn androidx.compose.**

# --- Keep line numbers for readable crash logs (the app has a CrashLogger) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
