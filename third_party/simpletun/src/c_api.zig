const std = @import("std");
const engine = @import("engine.zig");

var global_engine: ?engine.Engine = null;
var global_lock: std.atomic.Value(bool) = std.atomic.Value(bool).init(false);

fn acquireLock() void {
    while (global_lock.cmpxchgWeak(false, true, .acquire, .monotonic) != null) {
        std.atomic.spinLoopHint();
    }
}

fn releaseLock() void {
    global_lock.store(false, .release);
}

export fn simpletun_start(tun_fd: c_int, socks_ip: u32, socks_port: u16) c_int {
    acquireLock();
    if (global_engine != null) {
        releaseLock();
        return -1; // already running
    }

    const eng = engine.Engine.init(.{
        .tun_fd = tun_fd,
        .socks_ip = socks_ip,
        .socks_port = socks_port,
        .mtu = 1500,
    }) catch {
        releaseLock();
        return -2;
    };

    global_engine = eng;
    releaseLock();

    // Block calling thread until stopped
    if (global_engine) |*e| {
        e.run() catch {};
    }

    acquireLock();
    if (global_engine) |*e| {
        e.deinit();
        global_engine = null;
    }
    releaseLock();
    return 0;
}

export fn simpletun_stop() c_int {
    acquireLock();
    defer releaseLock();

    if (global_engine) |*e| {
        e.stop();
        return 0;
    }
    return -1;
}

export fn simpletun_version() [*:0]const u8 {
    return "0.1.0-simpletun";
}

