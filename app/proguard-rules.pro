# Keep kotlinx.serialization classes (they're accessed via reflection/generated code)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class dev.matejgroombridge.triolingo.**$$serializer { *; }
-keepclassmembers class dev.matejgroombridge.triolingo.** {
    *** Companion;
}
-keepclasseswithmembers class dev.matejgroombridge.triolingo.** {
    kotlinx.serialization.KSerializer serializer(...);
}
