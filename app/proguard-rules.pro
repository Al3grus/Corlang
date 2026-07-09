# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.corlang.app.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.corlang.app.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
