const std = @import("std");
const builtin = @import("builtin");
const linux = std.os.linux;

pub const fd_t = i32;

pub fn monotonicMs() i64 {
    var ts: linux.timespec = undefined;
    _ = linux.clock_gettime(.MONOTONIC, &ts);
    return @as(i64, @intCast(ts.sec)) * 1000 + @divTrunc(@as(i64, @intCast(ts.nsec)), 1_000_000);
}

pub fn close(fd: fd_t) void {
    if (fd >= 0) {
        _ = linux.close(fd);
    }
}

pub fn read(fd: fd_t, buf: []u8) !usize {
    const rc = linux.read(fd, buf.ptr, buf.len);
    return switch (linux.errno(rc)) {
        .SUCCESS => @intCast(rc),
        .AGAIN => error.WouldBlock,
        .INTR => error.WouldBlock,
        else => error.ReadFailed,
    };
}

pub fn writeSocket(fd: fd_t, buf: []const u8) !usize {
    const rc = linux.sendto(fd, buf.ptr, buf.len, linux.MSG.NOSIGNAL, null, 0);
    return switch (linux.errno(rc)) {
        .SUCCESS => @intCast(rc),
        .AGAIN => error.WouldBlock,
        .INTR => error.WouldBlock,
        .PIPE => error.BrokenPipe,
        .CONNRESET => error.ConnectionReset,
        else => error.WriteFailed,
    };
}

pub fn writeTun(fd: fd_t, buf: []const u8) !usize {
    const rc = linux.write(fd, buf.ptr, buf.len);
    return switch (linux.errno(rc)) {
        .SUCCESS => @intCast(rc),
        .AGAIN => error.WouldBlock,
        .INTR => error.WouldBlock,
        .PIPE => error.BrokenPipe,
        .CONNRESET => error.ConnectionReset,
        else => error.WriteFailed,
    };
}

pub fn write(fd: fd_t, buf: []const u8) !usize {
    return writeSocket(fd, buf);
}

pub fn createTcpSocket() !fd_t {
    const rc = linux.socket(linux.AF.INET, linux.SOCK.STREAM | linux.SOCK.NONBLOCK | linux.SOCK.CLOEXEC, linux.IPPROTO.TCP);
    if (linux.errno(rc) != .SUCCESS) return error.SocketCreationFailed;
    return @intCast(rc);
}

pub fn connect(fd: fd_t, ip: u32, port: u16) !void {
    const sockaddr_in = extern struct {
        sin_family: u16 = linux.AF.INET,
        sin_port: u16,
        sin_addr: u32,
        sin_zero: [8]u8 = [_]u8{0} ** 8,
    };

    const sa = sockaddr_in{
        .sin_family = linux.AF.INET,
        .sin_port = std.mem.nativeToBig(u16, port),
        .sin_addr = std.mem.nativeToBig(u32, ip),
    };

    const rc = linux.connect(fd, @ptrCast(&sa), @sizeOf(sockaddr_in));
    const err = linux.errno(rc);
    if (err == .SUCCESS) return;
    if (err == .INPROGRESS or err == .ALREADY or err == .AGAIN) return error.ConnectionPending;
    return error.ConnectFailed;
}

pub fn shutdown(fd: fd_t) void {
    _ = linux.shutdown(fd, linux.SHUT.WR);
}

pub fn createUdpSocket() !fd_t {
    const rc = linux.socket(linux.AF.INET, linux.SOCK.DGRAM | linux.SOCK.NONBLOCK | linux.SOCK.CLOEXEC, linux.IPPROTO.UDP);
    if (linux.errno(rc) != .SUCCESS) return error.SocketCreationFailed;
    return @intCast(rc);
}

pub fn sendto(fd: fd_t, buf: []const u8, ip: u32, port: u16) !usize {
    const sockaddr_in = extern struct {
        sin_family: u16 = linux.AF.INET,
        sin_port: u16,
        sin_addr: u32,
        sin_zero: [8]u8 = [_]u8{0} ** 8,
    };

    const sa = sockaddr_in{
        .sin_family = linux.AF.INET,
        .sin_port = std.mem.nativeToBig(u16, port),
        .sin_addr = std.mem.nativeToBig(u32, ip),
    };

    const rc = linux.sendto(fd, buf.ptr, buf.len, 0, @ptrCast(&sa), @sizeOf(sockaddr_in));
    return switch (linux.errno(rc)) {
        .SUCCESS => @intCast(rc),
        .AGAIN => error.WouldBlock,
        .INTR => error.WouldBlock,
        else => error.WriteFailed,
    };
}

pub fn recvfrom(fd: fd_t, buf: []u8) !usize {
    const rc = linux.recvfrom(fd, buf.ptr, buf.len, 0, null, null);
    return switch (linux.errno(rc)) {
        .SUCCESS => @intCast(rc),
        .AGAIN => error.WouldBlock,
        .INTR => error.WouldBlock,
        else => error.ReadFailed,
    };
}

pub fn createEventFd() !fd_t {
    const rc = linux.eventfd(0, linux.EFD.NONBLOCK | linux.EFD.CLOEXEC);
    if (linux.errno(rc) != .SUCCESS) return error.EventFdFailed;
    return @intCast(rc);
}

pub fn signalEventFd(fd: fd_t) void {
    const val: u64 = 1;
    _ = linux.write(fd, std.mem.asBytes(&val).ptr, 8);
}

