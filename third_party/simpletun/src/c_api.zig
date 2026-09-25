const std = @import("std");
const engine = @import("engine.zig");

export fn simpletun_run(tun_fd: c_int, socks_ip: u32, socks_port: u16) c_int {
    var eng = engine.Engine.init(.{
        .tun_fd = tun_fd,
        .socks_ip = socks_ip,
        .socks_port = socks_port,
        .mtu = 1500,
    }) catch return -1;
    defer eng.deinit();

    eng.run() catch return -2;
    return 0;
}
