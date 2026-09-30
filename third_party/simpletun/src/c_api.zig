const std = @import("std");
const engine = @import("engine.zig");

const SpinLock = struct {
    state: u8 = 0,

    pub fn lock(self: *SpinLock) void {
        while (@cmpxchgWeak(u8, &self.state, 0, 1, .acquire, .monotonic) != null) {
            std.atomic.spinLoopHint();
        }
    }

    pub fn unlock(self: *SpinLock) void {
        @atomicStore(u8, &self.state, 0, .release);
    }
};

var global_engine_storage: engine.Engine = undefined;
var global_engine: ?*engine.Engine = null;
var global_lock: SpinLock = .{};

pub fn logMsg(prio: c_int, comptime fmt: []const u8, args: anytype) void {
    _ = prio;
    _ = fmt;
    _ = args;
}

export fn simpletun_init(tun_fd: c_int, socks_ip: u32, socks_port: u16) c_int {
    global_lock.lock();
    defer global_lock.unlock();

    if (global_engine != null) {
        return -1; // already running
    }

    global_engine_storage.initInto(.{
        .tun_fd = tun_fd,
        .socks_ip = socks_ip,
        .socks_port = socks_port,
        .mtu = 1500,
    }) catch |err| {
        logMsg(6, "Engine.init failed: {s}", .{@errorName(err)});
        return -2;
    };

    global_engine = &global_engine_storage;
    return 0;
}

export fn simpletun_run() c_int {
    var opt_engine: ?*engine.Engine = null;
    {
        global_lock.lock();
        opt_engine = global_engine;
        global_lock.unlock();
    }

    if (opt_engine) |e| {
        e.run() catch |err| {
            logMsg(6, "Engine.run returned error: {s}", .{@errorName(err)});
        };
        logMsg(4, "Engine.run exited, running={}", .{e.running});

        global_lock.lock();
        defer global_lock.unlock();
        e.deinit();
        global_engine = null;
        return 0;
    }
    return -1;
}

export fn simpletun_start(tun_fd: c_int, socks_ip: u32, socks_port: u16) c_int {
    const rc = simpletun_init(tun_fd, socks_ip, socks_port);
    if (rc != 0) return rc;
    return simpletun_run();
}

export fn simpletun_stop() c_int {
    logMsg(4, "simpletun_stop() called from C/JNI", .{});
    global_lock.lock();
    defer global_lock.unlock();
    if (global_engine) |e| {
        e.stop();
        return 0;
    }
    return -1;
}

export fn simpletun_version() [*:0]const u8 {
    return "0.1.0-simpletun";
}
