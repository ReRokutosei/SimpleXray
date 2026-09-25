const std = @import("std");
const engine = @import("engine.zig");

var global_engine_storage: engine.Engine = undefined;
var global_engine: ?*engine.Engine = null;
var global_lock: std.atomic.Value(bool) = std.atomic.Value(bool).init(false);

fn acquireLock() void {
    while (global_lock.cmpxchgWeak(false, true, .acquire, .monotonic) != null) {
        std.atomic.spinLoopHint();
    }
}

fn releaseLock() void {
    global_lock.store(false, .release);
}

pub fn logMsg(prio: c_int, comptime fmt: []const u8, args: anytype) void {
    _ = prio;
    _ = fmt;
    _ = args;
}

export fn simpletun_start(tun_fd: c_int, socks_ip: u32, socks_port: u16) c_int {
    acquireLock();
    if (global_engine != null) {
        releaseLock();
        return -1; // already running
    }

    global_engine_storage.initInto(.{
        .tun_fd = tun_fd,
        .socks_ip = socks_ip,
        .socks_port = socks_port,
        .mtu = 1500,
    }) catch |err| {
        releaseLock();
        logMsg(6, "Engine.init failed: {s}", .{@errorName(err)});
        return -2;
    };

    global_engine = &global_engine_storage;
    releaseLock();

    // Block calling thread until stopped
    if (global_engine) |e| {
        e.run() catch |err| {
            logMsg(6, "Engine.run returned error: {s}", .{@errorName(err)});
        };
        logMsg(4, "Engine.run exited, running={}", .{e.running});
    }

    acquireLock();
    if (global_engine) |e| {
        e.deinit();
        global_engine = null;
    }
    releaseLock();
    return 0;
}

export fn simpletun_stop() c_int {
    logMsg(4, "simpletun_stop() called from C/JNI", .{});
    acquireLock();
    defer releaseLock();

    if (global_engine) |e| {
        e.stop();
        return 0;
    }
    return -1;
}

export fn simpletun_version() [*:0]const u8 {
    return "0.1.0-simpletun";
}

