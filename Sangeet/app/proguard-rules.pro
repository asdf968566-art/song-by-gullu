# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.sangeet.player.**$$serializer { *; }
-keepclassmembers class com.sangeet.player.** { *** Companion; }
-keepclasseswithmembers class com.sangeet.player.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# NewPipeExtractor (YouTube) + Rhino
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn com.google.re2j.**

# Anthropic SDK (AI DJ) uses Jackson + Kotlin reflection: keep its model classes and ours.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class com.sangeet.player.data.ai.** { *; }
-keepattributes Signature, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn com.anthropic.**
-dontwarn com.fasterxml.jackson.**
-dontwarn kotlin.reflect.**
-dontwarn org.slf4j.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
