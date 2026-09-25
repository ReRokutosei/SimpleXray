const std = @import("std");
const engine = @import("engine.zig");
const sys = @import("sys.zig");
const linux = std.os.linux;

const TUNSETIFF: usize = 0x400454ca;
const IFF_TUN: i16 = 0x0001;
const IFF_NO_PI: i16 = 0x1000;

const ifreq = extern struct {
    ifr_name: [16]u8,
    ifr_flags: i16,
    _pad: [22]u8,
};

fn openTunDevice(dev_name: []const u8) !sys.fd_t {
    const flags: linux.O = .{
        .ACCMODE = .RDWR,
        .NONBLOCK = true,
        .CLOEXEC = true,
    };
    const rc = linux.open("/dev/net/tun", flags, 0);
    if (linux.errno(rc) != .SUCCESS) return error.TunOpenFailed;
    const fd: sys.fd_t = @intCast(rc);
    errdefer sys.close(fd);

    var ifr: ifreq = undefined;
    @memset(std.mem.asBytes(&ifr), 0);
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;

    const copy_len = @min(dev_name.len, 15);
    @memcpy(ifr.ifr_name[0..copy_len], dev_name[0..copy_len]);

    const ioctl_rc = linux.ioctl(fd, TUNSETIFF, @intFromPtr(&ifr));
    if (linux.errno(ioctl_rc) != .SUCCESS) {
        return error.TunSetIffFailed;
    }

    return fd;
}

pub fn main(init: std.process.Init.Minimal) !u8 {
    var arena_state = std.heap.ArenaAllocator.init(std.heap.page_allocator);
    defer arena_state.deinit();
    const arena = arena_state.allocator();

    const all = init.args.toSlice(arena) catch {
        std.debug.print("SimpleTUN: out of memory\n", .{});
        return 1;
    };
    const args = if (all.len > 0) all[1..] else &.{};

    var tun_name: []const u8 = "tun0";
    var socks_port: u16 = 10808;

    var i: usize = 0;
    while (i < args.len) : (i += 1) {
        if (std.mem.eql(u8, args[i], "--tun") and i + 1 < args.len) {
            i += 1;
            tun_name = args[i];
        } else if (std.mem.eql(u8, args[i], "--socks5") and i + 1 < args.len) {
            i += 1;
            const s = args[i];
            if (std.mem.lastIndexOfScalar(u8, s, ':')) |idx| {
                socks_port = try std.fmt.parseInt(u16, s[idx + 1 ..], 10);
            } else {
                socks_port = try std.fmt.parseInt(u16, s, 10);
            }
        }
    }

    std.debug.print("[SimpleTUN] Starting on TUN: {s}, SOCKS5: 127.0.0.1:{d}\n", .{ tun_name, socks_port });

    const tun_fd = try openTunDevice(tun_name);
    defer sys.close(tun_fd);

    try global_main_engine.initInto(.{
        .tun_fd = tun_fd,
        .socks_ip = 0x7f000001,
        .socks_port = socks_port,
        .mtu = 1500,
    });
    defer global_main_engine.deinit();

    try global_main_engine.run();
    return 0;
}

var global_main_engine: engine.Engine = undefined;
