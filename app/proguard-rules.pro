# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.argot.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.argot.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.argot.** {
    kotlinx.serialization.KSerializer serializer(...);
}
