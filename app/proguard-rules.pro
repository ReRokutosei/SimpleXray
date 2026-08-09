-keep class com.simplexray.an.service.TProxyService {
    native <methods>;
    *;
}

-keepclasseswithmembernames class * {
    native <methods>;
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