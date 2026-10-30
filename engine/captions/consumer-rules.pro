# Framecraft :engine:captions R8 rules: Vosk reaches its native library through JNA, which needs its classes intact.
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
