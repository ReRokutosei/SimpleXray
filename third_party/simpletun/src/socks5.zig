const std = @import("std");

pub const Greeting = [_]u8{ 0x05, 0x01, 0x00 };

pub fn formatConnectRequest(buf: *[10]u8, dst_ip: u32, dst_port: u16) usize {
    buf[0] = 0x05; // SOCKS version 5
    buf[1] = 0x01; // CMD: CONNECT
    buf[2] = 0x00; // RSV
    buf[3] = 0x01; // ATYP: IPv4

    // dst_ip is in network byte order in raw packets, copy directly
    const ip_bytes: *const [4]u8 = @ptrCast(&dst_ip);
    @memcpy(buf[4..8], ip_bytes);

    // dst_port is in network byte order in raw packets, copy directly
    const port_bytes: *const [2]u8 = @ptrCast(&dst_port);
    @memcpy(buf[8..10], port_bytes);

    return 10;
}

test "SOCKS5 formatConnectRequest" {
    var buf: [10]u8 = undefined;
    const ip = std.mem.nativeToBig(u32, 0x7f000001); // 127.0.0.1
    const port = std.mem.nativeToBig(u16, 8080);
    const len = formatConnectRequest(&buf, ip, port);
    try std.testing.expectEqual(@as(usize, 10), len);
    try std.testing.expectEqual(@as(u8, 0x05), buf[0]);
    try std.testing.expectEqual(@as(u8, 0x01), buf[1]);
    try std.testing.expectEqual(@as(u8, 0x01), buf[3]);
}
