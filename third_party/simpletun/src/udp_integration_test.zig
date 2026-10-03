const std = @import("std");
const linux = std.os.linux;
const engine = @import("engine.zig");
const sys = @import("sys.zig");

const SERVER_DEADLINE_MS: i64 = 8_000;
const CLIENT_DEADLINE_MS: i64 = 5_000;

fn waitEvents(fd: i32, events: i16, deadline_ms: i64) bool {
    while (true) {
        const now = sys.monotonicMs();
        if (now >= deadline_ms) return false;
        const remaining: i32 = @intCast(@min(@as(i64, 100), deadline_ms - now));
        var pfd = [1]linux.pollfd{.{ .fd = fd, .events = events, .revents = 0 }};
        const rc = linux.poll(&pfd, 1, remaining);
        const err = linux.errno(rc);
        if (err == .INTR) continue;
        if (err != .SUCCESS) return false;
        if (rc > 0 and (pfd[0].revents & (events | linux.POLL.ERR | linux.POLL.HUP)) != 0) return true;
    }
}

fn readFullDeadline(fd: i32, buf: []u8, deadline_ms: i64) !void {
    var offset: usize = 0;
    while (offset < buf.len) {
        if (!waitEvents(fd, linux.POLL.IN, deadline_ms)) return error.Timeout;
        const rc = linux.read(fd, buf.ptr + offset, buf.len - offset);
        const err = linux.errno(rc);
        if (err == .AGAIN or err == .INTR) continue;
        if (err != .SUCCESS or rc == 0) return error.ReadFailed;
        offset += rc;
    }
}

fn writeFullDeadline(fd: i32, buf: []const u8, deadline_ms: i64) !void {
    var offset: usize = 0;
    while (offset < buf.len) {
        if (!waitEvents(fd, linux.POLL.OUT, deadline_ms)) return error.Timeout;
        const rc = linux.write(fd, buf.ptr + offset, buf.len - offset);
        const err = linux.errno(rc);
        if (err == .AGAIN or err == .INTR) continue;
        if (err != .SUCCESS or rc == 0) return error.WriteFailed;
        offset += rc;
    }
}

fn acceptDeadline(listener: i32, deadline_ms: i64) !i32 {
    while (true) {
        if (!waitEvents(listener, linux.POLL.IN, deadline_ms)) return error.Timeout;
        var addr: sys.SockAddrIn = undefined;
        var addr_len: linux.socklen_t = @sizeOf(sys.SockAddrIn);
        const rc = linux.accept(listener, @ptrCast(&addr), &addr_len);
        const err = linux.errno(rc);
        if (err == .AGAIN or err == .INTR) continue;
        if (err != .SUCCESS) return error.AcceptFailed;
        return @intCast(rc);
    }
}

fn recvfromDeadline(fd: i32, buf: []u8, src: *sys.SockAddrIn, src_len: *linux.socklen_t, deadline_ms: i64) !usize {
    while (true) {
        if (!waitEvents(fd, linux.POLL.IN, deadline_ms)) return error.Timeout;
        const rc = linux.recvfrom(fd, buf.ptr, buf.len, 0, @ptrCast(src), src_len);
        const err = linux.errno(rc);
        if (err == .AGAIN or err == .INTR) continue;
        if (err != .SUCCESS) return error.RecvFailed;
        return rc;
    }
}

fn mockSocksUdpServer(listener: i32) void {
    const deadline_ms = sys.monotonicMs() + SERVER_DEADLINE_MS;
    const conn = acceptDeadline(listener, deadline_ms) catch return;
    defer _ = linux.close(conn);

    var handshake: [10]u8 = undefined;
    readFullDeadline(conn, handshake[0..3], deadline_ms) catch return;
    writeFullDeadline(conn, &[2]u8{ 5, 0 }, deadline_ms) catch return;
    readFullDeadline(conn, handshake[0..10], deadline_ms) catch return;
    if (handshake[0] != 5 or handshake[1] != 3) return;

    const udp_rc = linux.socket(linux.AF.INET, linux.SOCK.DGRAM | linux.SOCK.NONBLOCK | linux.SOCK.CLOEXEC, linux.IPPROTO.UDP);
    if (linux.errno(udp_rc) != .SUCCESS) return;
    const udp_fd: i32 = @intCast(udp_rc);
    defer _ = linux.close(udp_fd);

    var bind_addr = sys.SockAddrIn{
        .sin_family = linux.AF.INET,
        .sin_port = 0,
        .sin_addr = std.mem.nativeToBig(u32, 0x7f000001),
    };
    if (linux.errno(linux.bind(udp_fd, @ptrCast(&bind_addr), @sizeOf(sys.SockAddrIn))) != .SUCCESS) return;

    var bound: sys.SockAddrIn = undefined;
    var bound_len: linux.socklen_t = @sizeOf(sys.SockAddrIn);
    if (linux.errno(linux.getsockname(udp_fd, @ptrCast(&bound), &bound_len)) != .SUCCESS) return;
    const relay_port = std.mem.bigToNative(u16, bound.sin_port);

    const reply = [10]u8{ 5, 0, 0, 1, 127, 0, 0, 1, @intCast(relay_port >> 8), @intCast(relay_port & 0xff) };
    writeFullDeadline(conn, &reply, deadline_ms) catch return;

    var src: sys.SockAddrIn = undefined;
    var src_len: linux.socklen_t = @sizeOf(sys.SockAddrIn);
    var packet: [128]u8 = undefined;
    const n = recvfromDeadline(udp_fd, &packet, &src, &src_len, deadline_ms) catch return;
    if (n < 10) return;
    _ = linux.sendto(udp_fd, &packet, n, 0, @ptrCast(&src), src_len);
}

fn runEngine(eng: *engine.Engine) void {
    eng.run() catch {};
}

test "UDP ASSOCIATE end-to-end maps replies through the pending queue" {
    const listener_rc = linux.socket(
        linux.AF.INET,
        linux.SOCK.STREAM | linux.SOCK.NONBLOCK | linux.SOCK.CLOEXEC,
        linux.IPPROTO.TCP,
    );
    if (linux.errno(listener_rc) != .SUCCESS) return error.SkipZigTest;
    const listener: i32 = @intCast(listener_rc);
    defer _ = linux.close(listener);

    var bind_addr = sys.SockAddrIn{
        .sin_family = linux.AF.INET,
        .sin_port = 0,
        .sin_addr = std.mem.nativeToBig(u32, 0x7f000001),
    };
    if (linux.errno(linux.bind(listener, @ptrCast(&bind_addr), @sizeOf(sys.SockAddrIn))) != .SUCCESS) return error.SkipZigTest;
    if (linux.errno(linux.listen(listener, 16)) != .SUCCESS) return error.SkipZigTest;

    var bound: sys.SockAddrIn = undefined;
    var bound_len: linux.socklen_t = @sizeOf(sys.SockAddrIn);
    if (linux.errno(linux.getsockname(listener, @ptrCast(&bound), &bound_len)) != .SUCCESS) return error.SkipZigTest;
    const socks_port = std.mem.bigToNative(u16, bound.sin_port);

    const server_thread = try std.Thread.spawn(.{}, mockSocksUdpServer, .{listener});
    server_thread.detach();

    var tun_pair: [2]i32 = undefined;
    if (linux.errno(linux.socketpair(linux.AF.UNIX, linux.SOCK.STREAM, 0, &tun_pair)) != .SUCCESS) return error.SkipZigTest;
    defer _ = linux.close(tun_pair[1]);

    var eng: engine.Engine = undefined;
    try eng.initInto(.{
        .tun_fd = tun_pair[0],
        .socks_ip = 0x7f000001,
        .socks_port = socks_port,
        .mtu = 1500,
    });

    const engine_thread = try std.Thread.spawn(.{}, runEngine, .{&eng});
    var engine_running = true;
    defer {
        if (engine_running) {
            eng.stop();
            engine_thread.join();
        }
        eng.deinit();
    }

    var packet: [33]u8 = undefined;
    @memset(&packet, 0);
    packet[0] = 0x45;
    packet[3] = @intCast(packet.len);
    packet[6] = 0x40;
    packet[8] = 64;
    packet[9] = 17;
    packet[12] = 10;
    packet[15] = 1;
    packet[16] = 203;
    packet[17] = 0;
    packet[18] = 113;
    packet[19] = 7;
    packet[20] = 0x9c;
    packet[21] = 0x40;
    packet[23] = 53;
    packet[25] = 13;
    @memcpy(packet[28..33], "hello");

    const deadline_ms = sys.monotonicMs() + CLIENT_DEADLINE_MS;
    try writeFullDeadline(tun_pair[1], &packet, deadline_ms);

    var response: [64]u8 = undefined;
    const response_len = try readFullDeadline(tun_pair[1], response[0..33], deadline_ms);
    _ = response_len;
    try std.testing.expectEqual(@as(u8, 17), response[9]);
    try std.testing.expectEqualSlices(u8, &[4]u8{ 203, 0, 113, 7 }, response[12..16]);
    try std.testing.expectEqualSlices(u8, &[4]u8{ 10, 0, 0, 1 }, response[16..20]);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 0, 53 }, response[20..22]);
    try std.testing.expectEqualSlices(u8, &[2]u8{ 0x9c, 0x40 }, response[22..24]);
    try std.testing.expectEqualSlices(u8, "hello", response[28..33]);

    eng.stop();
    engine_thread.join();
    engine_running = false;
}
