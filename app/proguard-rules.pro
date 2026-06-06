# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.frenchai.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.frenchai.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
