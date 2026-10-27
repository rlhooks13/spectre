# Applies to the minified build types, staging and release. Shrink unused
# library code (Material icons, Compose tooling) but do not obfuscate or
# optimize, so stack traces stay readable.
-dontobfuscate
-dontoptimize

-keep class app.fieldwatch.** { *; }
-keepclassmembers class app.fieldwatch.** { *; }

-keepattributes RuntimeVisibleAnnotations, AnnotationDefault, InnerClasses, Signature, Exception, EnclosingMethod, SourceFile, LineNumberTable

# config.json / signature packs
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

-dontwarn androidx.compose.**
