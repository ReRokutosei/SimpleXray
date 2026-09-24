# Keep all native methods and JNI bindings completely intact across all classes
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep service classes and all their members (protect JNI calls, callbacks, and reflection)
-keep class com.simplexray.re.service.** {
    *;
}

# Keep Protobuf Generated Message Classes & Reflective Methods
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}

# SnakeYAML Rules
-dontwarn java.beans.**
-dontwarn java.nio.file.**
-keep class org.yaml.snakeyaml.** { *; }