-keep class com.simplexray.an.service.TProxyService {
    @kotlin.jvm.JvmStatic *;
}

# Keep Protobuf Generated Message Classes & Reflective Methods
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}