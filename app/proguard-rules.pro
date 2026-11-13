# EV Navigator Iran V9.5 - R8/ProGuard Production Rules (Item 74)
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# osmdroid
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Android for Cars App Library (Android Auto Session, Screen, CarAppService)
-keep class androidx.car.app.** { *; }
-keep class com.evnavigator.iran.automotive.** { *; }
-dontwarn androidx.car.app.**

# Dagger Hilt DI
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Room Database Entities, DAOs, and TypeConverters
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.evnavigator.iran.data.**.entity.** { *; }
-keep class com.evnavigator.iran.data.**.converters.** { *; }

# Kotlinx Serialization & Parcelize
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Remove verbose/debug/info logging from release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
