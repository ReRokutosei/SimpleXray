# Keep all native methods and JNI bindings completely intact across all classes
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep Android Services and Worker components instantiated by system / reflection
-keep class com.simplexray.re.service.TProxyService { *; }
-keep class com.simplexray.re.service.BenchmarkService { *; }
-keep class com.simplexray.re.service.GeoUpdateWorker { *; }

# Keep Protobuf Generated Message Classes & Reflective Methods used by CoreStatsClient
-keep class com.xray.app.stats.command.StatsServiceGrpc** { *; }
-keep class com.xray.app.stats.command.QueryStats** { *; }
-keep class com.xray.app.stats.command.SysStats** { *; }
-keep class com.xray.app.stats.command.Stat** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-keep class * extends com.google.protobuf.GeneratedMessageV3 { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageV3 {
    <fields>;
    <methods>;
}
-dontwarn com.google.protobuf.**

# SnakeYAML Rules
-dontwarn java.beans.**
-dontwarn java.nio.file.**
-keep class org.yaml.snakeyaml.** { *; }