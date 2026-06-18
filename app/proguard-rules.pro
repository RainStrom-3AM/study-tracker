# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.studytracker.data.db.** { *; }
-keep class com.studytracker.domain.model.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
