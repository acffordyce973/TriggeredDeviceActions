# Keep Kotlinx Serialization classes & annotations
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
	*** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
	kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.antigravity.triggeredactions.**$$serializer { *; }
-keepclassmembers class com.antigravity.triggeredactions.** {
	*** Companion;
}
-keepclasseswithmembers class com.antigravity.triggeredactions.** {
	kotlinx.serialization.KSerializer serializer(...);
}
