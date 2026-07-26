# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.gson.** { *; }
-keep class com.gituploader.data.model.** { *; }
