# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.studytracker.data.db.** { *; }
-keep class com.studytracker.domain.model.** { *; }
-keep class com.studytracker.sync.Mongo** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class retrofit2.** { *; }
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn retrofit2.**
