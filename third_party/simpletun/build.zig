const std = @import("std");

pub fn build(b: *std.Build) void {
    const target = b.standardTargetOptions(.{});
    const optimize = b.standardOptimizeOption(.{});

    // Standalone CLI executable (for microbench & local testing)
    const exe = b.addExecutable(.{
        .name = "simpletun",
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/main.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    b.installArtifact(exe);

    const run_cmd = b.addRunArtifact(exe);
    run_cmd.step.dependOn(b.getInstallStep());
    if (b.args) |args| {
        run_cmd.addArgs(args);
    }
    const run_step = b.step("run", "Run SimpleTUN standalone CLI");
    run_step.dependOn(&run_cmd.step);

    // Shared library for Android JNI / Linux host
    const lib = b.addLibrary(.{
        .name = "simpletun",
        .linkage = .dynamic,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/c_api.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    b.installArtifact(lib);

    // Static library
    const static_lib = b.addLibrary(.{
        .name = "simpletun",
        .linkage = .static,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/c_api.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    b.installArtifact(static_lib);

    b.installFile("include/simpletun.h", "include/simpletun.h");

    // Android cross-compilation step (arm64-v8a dedicated)
    const android_step = b.step("android", "Build arm64-v8a static library for SimpleXray Android integration");
    const arm64_android_query = std.Target.Query.parse(.{ .arch_os_abi = "aarch64-linux-android" }) catch @panic("invalid target");
    const arm64_android_target = b.resolveTargetQuery(arm64_android_query);

    const android_static_lib = b.addLibrary(.{
        .name = "simpletun",
        .linkage = .static,
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/c_api.zig"),
            .target = arm64_android_target,
            .optimize = .ReleaseSmall,
            .pic = true,
        }),
    });
    const prebuilt: std.Build.InstallDir = .{ .custom = "android/prebuilt/arm64-v8a" };
    android_step.dependOn(&b.addInstallArtifact(android_static_lib, .{ .dest_dir = .{ .override = prebuilt } }).step);

    // Unit test suite
    const unit_tests = b.addTest(.{
        .root_module = b.createModule(.{
            .root_source_file = b.path("src/main.zig"),
            .target = target,
            .optimize = optimize,
        }),
    });
    const run_unit_tests = b.addRunArtifact(unit_tests);
    const test_step = b.step("test", "Run SimpleTUN unit tests");
    test_step.dependOn(&run_unit_tests.step);
}

